package app.tide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.tide.core.design.TideTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The three readings, and the empty state.
 *
 * The fatigue shot is here to hold a rule in place: it must show bars and
 * days, and never a number for the index itself.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MuscleMapScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: MuscleMapUiState) {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    MuscleMapScreen(state)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/muscles-$name.png")
    }

    @Test
    fun balance() = shoot(
        "balance",
        MuscleMapUiState(
            windowLabel = "LAST 14 DAYS",
            view = MuscleView.Balance,
            rows = listOf(
                MuscleMapUiState.Row("Quads", 1f, "12 480 kg"),
                MuscleMapUiState.Row("Back", 0.82f, "10 240 kg"),
                MuscleMapUiState.Row("Chest", 0.64f, "8 010 kg"),
                MuscleMapUiState.Row("Hamstrings", 0.41f, "5 120 kg"),
                MuscleMapUiState.Row("Biceps", 0.22f, "2 760 kg"),
                MuscleMapUiState.Row("Calves", 0.04f, "500 kg", stale = true),
            ),
        ),
    )

    @Test
    fun fatigue() = shoot(
        "fatigue",
        MuscleMapUiState(
            windowLabel = "LAST 14 DAYS",
            view = MuscleView.Fatigue,
            rows = listOf(
                MuscleMapUiState.Row("Chest", 1f, "today"),
                MuscleMapUiState.Row("Triceps", 0.71f, "today"),
                MuscleMapUiState.Row("Quads", 0.33f, "2d"),
                MuscleMapUiState.Row("Back", 0.12f, "4d"),
            ),
        ),
    )

    @Test
    fun strength() = shoot(
        "strength",
        MuscleMapUiState(
            windowLabel = "LAST 14 DAYS",
            view = MuscleView.Strength,
            rows = listOf(
                MuscleMapUiState.Row("Hamstrings", 1f, "182 kg, 3d"),
                MuscleMapUiState.Row("Quads", 0.88f, "160 kg, 2d"),
                MuscleMapUiState.Row("Chest", 0.55f, "100 kg, today"),
                MuscleMapUiState.Row("Calves", 0f, "38d", stale = true),
                MuscleMapUiState.Row("Neck", 0f, null, stale = true),
            ),
        ),
    )

    @Test
    fun nothingLoggedYet() = shoot(
        "empty",
        MuscleMapUiState(
            windowLabel = "LAST 14 DAYS",
            view = MuscleView.Balance,
            rows = emptyList(),
        ),
    )
}
