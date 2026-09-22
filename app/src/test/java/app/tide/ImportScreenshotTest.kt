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
 * Every phase of the import screen, including the ones nobody photographs.
 *
 * The failure and the done states are rendered here deliberately. They are
 * where honest copy either exists or does not, and they are the states that
 * rot first because they are the hardest to reach by hand.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ImportScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: ImportUiState) {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    ImportScreen(state)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/import-$name.png")
    }

    @Test
    fun idle() = shoot("idle", ImportUiState.Idle)

    @Test
    fun ready() = shoot(
        "ready",
        ImportUiState.Ready(
            format = "Strong",
            span = "March 2024 to September 2025",
            sessions = 184,
            sets = 4021,
            matched = 31,
            unmatched = listOf("Zercher Carry", "Jefferson Curl"),
            skipped = 3,
        ),
    )

    @Test
    fun readyWithNothingUnmatched() = shoot(
        "ready-clean",
        ImportUiState.Ready(
            format = "FitNotes",
            span = "January 2025",
            sessions = 12,
            sets = 214,
            matched = 9,
            unmatched = emptyList(),
            skipped = 0,
        ),
    )

    @Test
    fun done() = shoot(
        "done",
        ImportUiState.Done(
            sessions = 184,
            sets = 4021,
            created = listOf("Zercher Carry", "Jefferson Curl"),
            duplicates = 0,
            skipped = 3,
        ),
    )

    @Test
    fun doneWithEverythingAlreadyThere() = shoot(
        "done-duplicate",
        ImportUiState.Done(
            sessions = 0,
            sets = 0,
            created = emptyList(),
            duplicates = 184,
            skipped = 0,
        ),
    )

    @Test
    fun failed() = shoot(
        "failed",
        ImportUiState.Failed(
            "That does not look like a FitNotes or Strong export. " +
                "Both are CSV files, exported from inside those apps.",
        ),
    )
}
