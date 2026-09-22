package app.tide

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import app.tide.core.data.Tide
import app.tide.core.data.importer.TrainingImporter
import app.tide.core.data.training.TrainingRepository
import app.tide.core.design.TideTheme
import app.tide.notify.DigestWorker
import app.tide.notify.NotifyPreferences
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object Today : Screen
    data object Import : Screen
    data object Muscles : Screen
    data object Picker : Screen
    data object Calendar : Screen
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
        // they exist from the first launch whether or not anything is ever
        // posted. Creating one is free and idempotent.
        Tide.notifier(applicationContext).ensureChannels()
        setContent {
            TideTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Today) }
                when (val current = screen) {
                    is Screen.Today -> WiredTodayScreen(
                        repository = remember { Tide.training(applicationContext) },
                        onStartSession = { screen = Screen.Picker },
                        onImport = { screen = Screen.Import },
                        onMuscles = { screen = Screen.Muscles },
                        onCalendar = { screen = Screen.Calendar },
                        onSettings = { screen = Screen.Settings },
                    )
                    is Screen.Picker -> WiredExercisePickerScreen(
                        repository = remember { Tide.training(applicationContext) },
                        onBack = { screen = Screen.Today },
                        onPick = { screen = Screen.Session(it) },
                    )
                    is Screen.Session -> WiredSessionScreen(
                        exerciseId = current.exerciseId,
                        repository = remember { Tide.training(applicationContext) },
                        // Back goes to the picker, not home: the session stays
                        // open, so the next lift is logged into the same one.
                        onBack = { screen = Screen.Picker },
                        // Finished is different. There is no session left to
                        // add to, so picking another lift here would start one.
                        onFinished = { screen = Screen.Today },
                    )
                    is Screen.Calendar -> WiredCalendarScreen(
                        repository = remember { Tide.training(applicationContext) },
                        onBack = { screen = Screen.Today },
                    )
                    is Screen.Settings -> WiredSettingsScreen(
                        onBack = { screen = Screen.Today },
                    )
                    is Screen.Muscles -> WiredMuscleMapScreen(
                        repository = remember { Tide.training(applicationContext) },
                        onBack = { screen = Screen.Today },
                    )
                    is Screen.Import -> WiredImportScreen(
                        importer = remember { Tide.importer(applicationContext) },
                        readText = ::readTextFromUri,
                        onBack = { screen = Screen.Today },
                    )
                }
            }
        }
    }
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
private fun WiredSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Asked for here rather than at launch, at the moment the person turns the
    // summary on. An app that asks before it has anything to say is asking for
    // a habit, not for permission.
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val viewModel = remember {
        val prefs = NotifyPreferences(context)
        SettingsViewModel(
            prefs = prefs,
            notifier = Tide.notifier(context.applicationContext),
            scope = scope,
            onScheduleChanged = { enabled, at ->
                if (enabled) {
                    DigestWorker.schedule(context.applicationContext, at)
                } else {
                    DigestWorker.cancel(context.applicationContext)
                }
            },
        )
    }
    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    SettingsScreen(
        state = uiState,
        onBack = onBack,
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
            delay(1_000)
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
