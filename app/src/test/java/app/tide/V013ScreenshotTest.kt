package app.tide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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

/** Onboarding, and Ask's new chat surface, from v0.1.3. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class V013ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun onboardingBeforeAnythingGranted() {
        capture("onboarding") {
            OnboardingScreen(
                notificationsGranted = false,
                healthConnectAvailable = true,
                healthConnectGranted = false,
            )
        }
    }

    @Test
    fun onboardingWithBothGranted() {
        capture("onboarding-granted") {
            OnboardingScreen(
                notificationsGranted = true,
                healthConnectAvailable = true,
                healthConnectGranted = true,
            )
        }
    }

    @Test
    fun askWithAConversation() {
        capture("ask-chat") {
            AskScreen(
                AskUiState(
                    modelName = "Qwen2.5 0.5B Instruct, Q4_K_M",
                    modelLicence = "Apache-2.0",
                    approximateSizeLabel = "400 MB",
                    messages = listOf(
                        AskUiState.ChatMessage(fromUser = true, text = "How many sessions this week?"),
                        AskUiState.ChatMessage(fromUser = false, text = "2 sessions this week."),
                        AskUiState.ChatMessage(fromUser = true, text = "last time I did squat"),
                        AskUiState.ChatMessage(
                            fromUser = false,
                            text = "Back squat, last time: 102.5 kg x 5 for 3 sets.",
                        ),
                    ),
                ),
            )
        }
    }
}
