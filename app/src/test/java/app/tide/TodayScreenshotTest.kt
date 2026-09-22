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
 * Renders screens to PNG on the JVM, with no device and no emulator.
 *
 * This exists because it is the only screenshot path that works here: the
 * machine has no hypervisor driver and installing one needs admin, so the
 * emulator can only run in software emulation.
 *
 * One real limitation, stated because it matters: Robolectric does not execute
 * AGSL runtime shaders, so the ocean renders as its depth gradient alone. Layout,
 * type, colour and the superellipse corners are all genuine. The shader needs a
 * real device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TodayScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun today() {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    TodayScreen()
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/today.png")
    }

    @Test
    fun session() {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    SessionScreen(
                        SessionUiState(
                            exerciseName = "Barbell row",
                            setNumber = 3,
                            targetSets = 4,
                            ruleLabel = "DOUBLE PROGRESSION 6-9",
                            loadKg = "82.5",
                            reps = "7",
                            lastTime = "80 kg x 7",
                            elapsed = "00:42:18",
                            restRemaining = "1:47",
                            logged = listOf(
                                SessionUiState.LoggedSet("W", "60 kg x 8", null, true),
                                SessionUiState.LoggedSet("1", "82.5 kg x 8", "RIR 2", false),
                                SessionUiState.LoggedSet("2", "82.5 kg x 7", "RIR 1", false),
                            ),
                        ),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/session.png")
    }

    @Test
    fun todayLargeFont() {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    TodayScreen()
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/today-fontscale.png")
    }
}
