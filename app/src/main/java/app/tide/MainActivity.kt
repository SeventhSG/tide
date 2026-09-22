package app.tide

import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object Today : Screen
    data object Import : Screen
    data object Muscles : Screen
    data object Picker : Screen
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
        setContent {
            TideTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Today) }
                when (val current = screen) {
                    is Screen.Today -> TodayScreen(
                        onStartSession = { screen = Screen.Picker },
                        onImport = { screen = Screen.Import },
                        onMuscles = { screen = Screen.Muscles },
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
