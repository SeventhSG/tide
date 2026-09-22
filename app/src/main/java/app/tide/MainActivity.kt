package app.tide

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.tide.ask.ModelInstaller
import app.tide.body.AndroidHealthSource
import app.tide.body.HealthSource
import app.tide.core.data.Tide
import app.tide.core.data.importer.TrainingImporter
import app.tide.core.data.training.TrainingRepository
import app.tide.core.design.BootWave
import app.tide.core.design.TideTheme
import app.tide.core.design.WaveTransition
import app.tide.notify.DigestWorker
import app.tide.notify.NotifyPreferences
import app.tide.notify.PlanSchedule
import app.tide.notify.SleepGuardWorker
import app.tide.sound.OceanSoundPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a screen is.
 *
 * A [Section] keeps the bottom bar; the rest are places you go, do one thing,
 * and come back from, so they take the whole screen. Hand written rather than
 * Navigation-Compose: there are a dozen destinations, no deep links yet and no
 * back stack worth the dependency.
 */
private sealed interface Screen {
    data class Main(val section: Section) : Screen
    data object Import : Screen
    data object Muscles : Screen
    data class Picker(val addingToPlan: Boolean = false) : Screen
    data object Calendar : Screen
    data object Planner : Screen
    data object Settings : Screen
    data class Session(val exerciseId: String) : Screen
}

class MainActivity : ComponentActivity() {

    /**
     * Reads a picked export off disk.
     *
     * On the IO dispatcher, because an export can be a few megabytes of CSV
     * and reading it on the main thread would drop frames on the one screen
     * where the person is already waiting.
     */
    private suspend fun readTextFromUri(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            contentResolver.openInputStream(Uri.parse(uri))?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The ocean runs to the edges, so the app draws behind the system bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Channels are the only notification control Android gives the user, so
        // they exist from the first launch whether or not anything is posted.
        Tide.notifier(applicationContext).ensureChannels()

        // Ask for the highest refresh rate the panel has. Compose animations are
        // already frame-rate independent (they run off the choreographer, not a
        // fixed step), so this is the one line needed to let a 90 or 120Hz
        // display actually use it rather than being capped at 60. Harmless on a
        // 60Hz phone: the system clamps to what the panel supports.
        window.attributes = window.attributes.apply { preferredRefreshRate = 120f }

        setContent {
            TideTheme {
                var booted by remember { mutableStateOf(false) }
                var screen by remember { mutableStateOf<Screen>(Screen.Main(Section.Today)) }
                var forward by remember { mutableStateOf(true) }

                // Exclusively one or the other. Both used to be mounted at
                // once, with the real app composed second and so drawn on top,
                // which hid the boot wave behind it for its entire run: the
                // animation was playing, just never visible.
                if (!booted) {
                    BootWave(Modifier.fillMaxSize()) { booted = true }
                    return@TideTheme
                }

                val go: (Screen, Boolean) -> Unit = { destination, deeper ->
                    forward = deeper
                    screen = destination
                }

                WaveTransition(
                    target = screen,
                    modifier = Modifier.fillMaxSize(),
                    // The crest also marks entering an exercise, since that is
                    // the one other moment worth a flourish: it is the door
                    // into the one screen in the app that then animates
                    // nothing but the press for as long as you are inside it.
                    crest = screen is Screen.Main || screen is Screen.Session,
                    forward = forward,
                ) { current ->
                    when (current) {
                        is Screen.Main -> MainSection(
                            section = current.section,
                            onSelectSection = { go(Screen.Main(it), true) },
                            go = go,
                        )

                        is Screen.Picker -> WiredExercisePickerScreen(
                            repository = remember { Tide.training(applicationContext) },
                            onBack = {
                                go(
                                    if (current.addingToPlan) Screen.Planner else Screen.Main(Section.Train),
                                    false,
                                )
                            },
                            onPick = { exerciseId ->
                                if (current.addingToPlan) {
                                    PlanHolder.pendingAdd = exerciseId
                                    go(Screen.Planner, false)
                                } else {
                                    go(Screen.Session(exerciseId), true)
                                }
                            },
                        )

                        is Screen.Session -> WiredSessionScreen(
                            exerciseId = current.exerciseId,
                            repository = remember { Tide.training(applicationContext) },
                            // Back goes to the picker, not home: the session
                            // stays open, so the next lift joins the same one.
                            onBack = { go(Screen.Picker(), false) },
                            // Finished is different. There is no session left
                            // to add to, so this goes back to the section.
                            onFinished = { go(Screen.Main(Section.Train), false) },
                        )

                        is Screen.Planner -> WiredPlannerScreen(
                            repository = remember { Tide.training(applicationContext) },
                            onBack = { go(Screen.Main(Section.Train), false) },
                            onAdd = { go(Screen.Picker(addingToPlan = true), true) },
                        )

                        is Screen.Calendar -> WiredCalendarScreen(
                            repository = remember { Tide.training(applicationContext) },
                            onBack = { go(Screen.Main(Section.Train), false) },
                        )

                        is Screen.Muscles -> WiredMuscleMapScreen(
                            repository = remember { Tide.training(applicationContext) },
                            onBack = { go(Screen.Main(Section.Train), false) },
                        )

                        is Screen.Settings -> WiredSettingsScreen(
                            onBack = { go(Screen.Main(Section.Today), false) },
                        )

                        is Screen.Import -> WiredImportScreen(
                            importer = remember { Tide.importer(applicationContext) },
                            readText = ::readTextFromUri,
                            onBack = { go(Screen.Main(Section.Today), false) },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MainSection(
        section: Section,
        onSelectSection: (Section) -> Unit,
        go: (Screen, Boolean) -> Unit,
    ) {
        TideScaffold(section = section, onSelectSection = onSelectSection) {
            when (section) {
                Section.Today -> WiredTodayScreen(
                    repository = remember { Tide.training(applicationContext) },
                    onStartSession = { go(Screen.Picker(), true) },
                    onImport = { go(Screen.Import, true) },
                    onMuscles = { go(Screen.Muscles, true) },
                    onCalendar = { go(Screen.Calendar, true) },
                    onSettings = { go(Screen.Settings, true) },
                )

                Section.Train -> WiredTrainScreen(
                    repository = remember { Tide.training(applicationContext) },
                    onStartSession = { go(Screen.Picker(), true) },
                    onPlanner = { go(Screen.Planner, true) },
                    onMuscles = { go(Screen.Muscles, true) },
                    onHistory = { go(Screen.Calendar, true) },
                    onExercises = { go(Screen.Picker(), true) },
                )

                Section.Body -> WiredBodyScreen()

                Section.Money -> MoneyScreen()

                Section.Ask -> WiredAskScreen()
            }
        }
    }
}

/**
 * The exercise the picker chose for the plan, on its way back to the planner.
 *
 * A single field rather than a navigation result, because the navigation here
 * is a `when` over a sealed interface and threading a result through it would
 * cost more than it saves. It is read once and cleared.
 */
private object PlanHolder {
    var pendingAdd: String? = null
}

@Composable
private fun WiredTodayScreen(
    repository: TrainingRepository,
    onStartSession: () -> Unit,
    onImport: () -> Unit,
    onMuscles: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { TodayViewModel(repository, scope) }
    val uiState by viewModel.state.collectAsState()

    // Coming back from a session, or from a day that turned over while the app
    // sat in the background, both land here. Neither is a database change, so
    // the flows do not fire and the screen would keep yesterday's date.
    LaunchedEffect(Unit) { viewModel.refresh() }

    TodayScreen(
        state = uiState,
        onStartSession = onStartSession,
        onImport = onImport,
        onMuscles = onMuscles,
        onCalendar = onCalendar,
        onSettings = onSettings,
    )
}

@Composable
private fun WiredTrainScreen(
    repository: TrainingRepository,
    onStartSession: () -> Unit,
    onPlanner: () -> Unit,
    onMuscles: () -> Unit,
    onHistory: () -> Unit,
    onExercises: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { TrainViewModel(repository, scope) }
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    TrainScreen(
        state = uiState,
        onStartSession = onStartSession,
        onPlanner = onPlanner,
        onMuscles = onMuscles,
        onHistory = onHistory,
        onExercises = onExercises,
    )
}

@Composable
private fun WiredPlannerScreen(
    repository: TrainingRepository,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val viewModel = remember {
        PlannerViewModel(
            repository = repository,
            scope = scope,
            planSchedule = PlanSchedule(repository, Tide.schedule(context.applicationContext)),
        )
    }
    val uiState by viewModel.state.collectAsState()

    // An exercise chosen in the picker lands here on the way back.
    LaunchedEffect(Unit) {
        PlanHolder.pendingAdd?.let {
            PlanHolder.pendingAdd = null
            viewModel.onAdd(it)
        }
        viewModel.refresh()
    }

    PlannerScreen(
        state = uiState,
        onBack = onBack,
        onSelectDay = viewModel::onSelectDay,
        onAdd = onAdd,
        onMoveUp = viewModel::onMoveUp,
        onMoveDown = viewModel::onMoveDown,
        onMoveToDay = viewModel::onMoveToDay,
        onRemove = viewModel::onRemove,
    )
}

@Composable
private fun WiredBodyScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { AndroidHealthSource(context) }
    val viewModel = remember { BodyViewModel(source, scope) }
    val uiState by viewModel.state.collectAsState()

    // Health Connect hands out its own permission sheet rather than Android's,
    // through a contract the client exposes.
    val permissions = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refresh() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    BodyScreen(
        state = uiState,
        onConnect = { runCatching { permissions.launch(HealthSource.PERMISSIONS) } },
        onOpenHealthConnect = {
            // The provider lives in the Play Store on most devices, and this is
            // the intent Google documents for sending someone to get it.
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setData(
                        Uri.parse(
                            "market://details?id=com.google.android.apps.healthdata" +
                                "&url=healthconnect%3A%2F%2Fonboarding",
                        ),
                    ),
                )
            }
        },
    )
}

@Composable
private fun WiredAskScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = remember { AskViewModel(ModelInstaller(context.applicationContext), scope) }
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    AskScreen(
        state = uiState,
        onInstall = viewModel::onInstall,
        onCancel = viewModel::onCancel,
        onRemove = viewModel::onRemove,
    )
}

@Composable
private fun WiredSessionScreen(
    exerciseId: String,
    repository: TrainingRepository,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(exerciseId) { SessionViewModel(exerciseId, repository, scope) }
    val uiState by viewModel.state.collectAsState()

    // The only ambient loop on this screen: a once-a-second nudge so the
    // elapsed clock reads live rather than freezing between logged sets.
    LaunchedEffect(viewModel) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            viewModel.tick()
        }
    }

    SessionScreen(
        state = uiState,
        onBack = onBack,
        onLoadChange = viewModel::onLoadChange,
        onRepsChange = viewModel::onRepsChange,
        onLogSet = viewModel::onLogSet,
        onToggleWarmUp = viewModel::onToggleWarmUp,
        onRowTapped = viewModel::onRowTapped,
        onRemoveSet = viewModel::onRemoveSet,
        onFinishRequested = viewModel::onFinishRequested,
        onFinishCancelled = viewModel::onFinishCancelled,
        onFinishConfirmed = viewModel::onFinishConfirmed,
        onDone = onFinished,
    )
}

@Composable
private fun WiredExercisePickerScreen(
    repository: TrainingRepository,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ExercisePickerViewModel(repository, scope) }
    val uiState by viewModel.state.collectAsState()

    ExercisePickerScreen(
        state = uiState,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onPick = onPick,
    )
}

@Composable
private fun WiredCalendarScreen(
    repository: TrainingRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { CalendarViewModel(repository, scope) }
    val uiState by viewModel.state.collectAsState()

    CalendarScreen(
        state = uiState,
        onBack = onBack,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onSelectDay = viewModel::onSelectDay,
    )
}

@Composable
private fun WiredMuscleMapScreen(
    repository: TrainingRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { MuscleMapViewModel(repository, scope) }
    val uiState by viewModel.state.collectAsState()

    MuscleMapScreen(
        state = uiState,
        onBack = onBack,
        onSelectView = viewModel::onSelectView,
    )
}

@Composable
private fun WiredSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Asked for here rather than at launch, at the moment the person turns the
    // summary on. An app that asks before it has anything to say is asking for
    // a habit, not for permission.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val player = remember { OceanSoundPlayer(context) }
    val viewModel = remember {
        SettingsViewModel(
            prefs = NotifyPreferences(context),
            notifier = Tide.notifier(context.applicationContext),
            scope = scope,
            onScheduleChanged = { enabled, at ->
                if (enabled) {
                    DigestWorker.schedule(context.applicationContext, at)
                } else {
                    DigestWorker.cancel(context.applicationContext)
                }
            },
            onSleepGuardScheduleChanged = { enabled, quietStart ->
                if (enabled) {
                    SleepGuardWorker.schedule(context.applicationContext, quietStart)
                } else {
                    SleepGuardWorker.cancel(context.applicationContext)
                }
            },
            sound = player,
        )
    }
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    SettingsScreen(
        state = uiState,
        onBack = {
            // Leaving the screen stops the sea. It is a thing you turn on while
            // looking at it, not a service that outlives the screen.
            viewModel.onStopSound()
            onBack()
        },
        onToggleDigest = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.onToggleDigest()
        },
        onDigestEarlier = viewModel::onDigestEarlier,
        onDigestLater = viewModel::onDigestLater,
        onToggleQuietHours = viewModel::onToggleQuietHours,
        onQuietStartEarlier = viewModel::onQuietStartEarlier,
        onQuietStartLater = viewModel::onQuietStartLater,
        onQuietEndEarlier = viewModel::onQuietEndEarlier,
        onQuietEndLater = viewModel::onQuietEndLater,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onSendTest = viewModel::onSendTest,
        onToggleSound = viewModel::onToggleSound,
        onSoundQuieter = viewModel::onSoundQuieter,
        onSoundLouder = viewModel::onSoundLouder,
        onToggleSleepGuard = viewModel::onToggleSleepGuard,
    )
}

@Composable
private fun WiredImportScreen(
    importer: TrainingImporter,
    readText: suspend (String) -> String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ImportViewModel(importer, scope, readText) }
    val uiState by viewModel.state.collectAsState()

    // OpenDocument rather than GetContent: it returns a durable uri and lets
    // the picker show files from anywhere, which is where an export lands.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onFileChosen(it.toString()) } }

    ImportScreen(
        state = uiState,
        onBack = onBack,
        // Some exporters write text/comma-separated-values, some write
        // text/plain, and a few write nothing useful at all, so the filter
        // has to be wide or the person's own file is greyed out.
        onChooseFile = {
            picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
        },
        onConfirm = viewModel::onConfirm,
        onDone = onBack,
    )
}
