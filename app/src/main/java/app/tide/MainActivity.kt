package app.tide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import app.tide.core.data.Tide
import app.tide.core.data.training.TrainingRepository
import app.tide.core.design.TideTheme
import kotlinx.coroutines.delay

private sealed interface Screen {
    data object Today : Screen
    data class Session(val exerciseId: String) : Screen
}

/**
 * No exercise picker yet, that is routine and day-of-week UI which has not
 * been built. Squat is the first seeded exercise, so it stands in as the
 * only door into the logger until a real one exists.
 */
private const val DEFAULT_EXERCISE_ID = "squat"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The ocean runs to the edges, so the app draws behind the system bars.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TideTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Today) }
                when (val current = screen) {
                    is Screen.Today -> TodayScreen(
                        onStartSession = { screen = Screen.Session(DEFAULT_EXERCISE_ID) },
                    )
                    is Screen.Session -> WiredSessionScreen(
                        exerciseId = current.exerciseId,
                        repository = remember { Tide.training(applicationContext) },
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
    )
}
