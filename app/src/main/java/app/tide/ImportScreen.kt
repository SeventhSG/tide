package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.OceanIntensity
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme
import app.tide.core.design.oceanScrimColor

/**
 * Bringing a training history in from another app.
 *
 * Opened once, maybe twice in the life of the app, which is the opposite of
 * the logger, so the constraints invert: the ocean stays on at [OceanIntensity.Subtle]
 * because there is no reason to strip it, and nothing here needs to be
 * operated one handed mid set.
 *
 * The screen it replaces in most apps is a single "Import" button that eats a
 * file and tells you it worked. This one shows what it found and makes you
 * press the button afterwards, because a year of training is not something to
 * commit sight unseen, and because the interesting part is what it could
 * **not** match.
 */

/** Pure view state, so every phase renders in a screenshot test with no file. */
sealed interface ImportUiState {

    /** Nothing chosen yet. */
    data object Idle : ImportUiState

    /** A file is being read. Local and fast, so this is rarely seen. */
    data object Reading : ImportUiState

    /** Read, matched, and waiting for a decision. Nothing written yet. */
    data class Ready(
        val format: String,
        val span: String?,
        val sessions: Int,
        val sets: Int,
        val matched: Int,
        val unmatched: List<String>,
        val skipped: Int,
    ) : ImportUiState

    data class Done(
        val sessions: Int,
        val sets: Int,
        val created: List<String>,
        val duplicates: Int,
        val skipped: Int,
    ) : ImportUiState

    data class Failed(val reason: String) : ImportUiState
}

@Composable
fun ImportScreen(
    state: ImportUiState,
    onBack: () -> Unit = {},
    onChooseFile: () -> Unit = {},
    onConfirm: () -> Unit = {},
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier, intensity = OceanIntensity.Subtle) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("<", style = DataStyle, color = TideColors.TextMuted)
                    }
                }

                Text(
                    "Import history",
                    style = MaterialTheme.typography.displayLarge,
                    color = TideColors.Text,
                )
                Spacer(Modifier.height(10.dp))

                when (state) {
                    ImportUiState.Idle -> Idle()
                    ImportUiState.Reading -> Reading()
                    is ImportUiState.Ready -> Ready(state)
                    is ImportUiState.Done -> Done(state)
                    is ImportUiState.Failed -> Failed(state)
                }

                Spacer(Modifier.height(20.dp))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                when (state) {
                    ImportUiState.Idle, is ImportUiState.Failed ->
                        Primary("Choose a file", onChooseFile)

                    ImportUiState.Reading -> Unit

                    is ImportUiState.Ready -> {
                        Primary(
                            if (state.sets == 1) "Import 1 set" else "Import ${state.sets.grouped()} sets",
                            onConfirm,
                        )
                        Spacer(Modifier.height(8.dp))
                        TideGhostButton(
                            onClick = onChooseFile,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Choose a different file",
                                style = MaterialTheme.typography.labelLarge,
                                color = TideColors.TextMuted,
                            )
                        }
                    }

                    is ImportUiState.Done -> Primary("Done", onDone)
                }
            }
        }
    }
}

@Composable
private fun Primary(label: String, onClick: () -> Unit) {
    TideButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = TideColors.OnAccent,
        )
    }
}

@Composable
private fun Idle() {
    Text(
        "Bring your training across from another app. Nothing leaves this phone, " +
            "and nothing is written until you have seen what came out of the file.",
        style = MaterialTheme.typography.bodyLarge,
        color = TideColors.TextMuted,
    )
    Spacer(Modifier.height(18.dp))
    Scrim {
        Text("WHAT WORKS", style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(10.dp))
        Supported("FitNotes", "Settings, Export Data, CSV")
        Spacer(Modifier.height(8.dp))
        Supported("Strong", "Profile, Export Data")
    }
}

@Composable
private fun Supported(app: String, where: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            app,
            style = MaterialTheme.typography.bodyLarge,
            color = TideColors.Text,
            modifier = Modifier.weight(1f),
        )
        Text(where, style = LabelStyle, color = TideColors.TextFaint)
    }
}

@Composable
private fun Reading() {
    // No spinner. The read is local and finishes in a blink, and a spinner
    // that flashes for 40ms is noise pretending to be progress.
    Text(
        "Reading the file.",
        style = MaterialTheme.typography.bodyLarge,
        color = TideColors.TextMuted,
    )
}

@Composable
private fun Ready(state: ImportUiState.Ready) {
    Text(
        buildString {
            append("A ${state.format} export")
            state.span?.let { append(", $it") }
            append(".")
        },
        style = MaterialTheme.typography.bodyLarge,
        color = TideColors.TextMuted,
    )

    Spacer(Modifier.height(18.dp))
    Scrim {
        Text("WHAT IS IN IT", style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(8.dp))
        Stat("Sessions", state.sessions.grouped())
        Stat("Sets", state.sets.grouped())
        Stat("Exercises matched", state.matched.grouped(), divider = state.skipped > 0)
        if (state.skipped > 0) {
            Stat("Rows unreadable", state.skipped.grouped(), TideColors.Warning, divider = false)
        }
    }

    if (state.unmatched.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Scrim {
            Text("ADDED AS NEW EXERCISES", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.height(8.dp))
            Text(
                // The honest version of what other importers hide. These lifts
                // come in, but they start their own history rather than
                // joining one, and that is worth knowing beforehand.
                "These are not in the library, so they come in under their own names " +
                    "and start their own history.",
                style = MaterialTheme.typography.bodyMedium,
                color = TideColors.TextMuted,
            )
            Spacer(Modifier.height(10.dp))
            state.unmatched.forEach {
                Text(
                    it,
                    style = DataStyle,
                    color = TideColors.Text,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun Done(state: ImportUiState.Done) {
    Text(
        if (state.sessions == 0) {
            "Nothing new came in."
        } else {
            "${state.sessions.grouped()} sessions and ${state.sets.grouped()} sets are in."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = TideColors.TextMuted,
    )

    Spacer(Modifier.height(18.dp))
    Scrim {
        Text("WHAT HAPPENED", style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(8.dp))
        Stat("Sessions added", state.sessions.grouped())
        Stat("Sets added", state.sets.grouped())
        Stat(
            "Exercises created",
            state.created.size.grouped(),
            divider = state.duplicates > 0 || state.skipped > 0,
        )
        if (state.duplicates > 0) {
            // Not an error. Re-importing the same export is a normal thing to
            // do, and saying so is better than silently doing nothing.
            Stat(
                "Already had",
                "${state.duplicates.grouped()} sessions",
                divider = state.skipped > 0,
            )
        }
        if (state.skipped > 0) {
            Stat("Rows unreadable", state.skipped.grouped(), TideColors.Warning, divider = false)
        }
    }
}

@Composable
private fun Failed(state: ImportUiState.Failed) {
    Scrim {
        Text("THAT FILE DID NOT WORK", style = LabelStyle, color = TideColors.Critical)
        Spacer(Modifier.height(8.dp))
        Text(
            state.reason,
            style = MaterialTheme.typography.bodyLarge,
            color = TideColors.Text,
        )
    }
}

/**
 * Thousands split by a thin space, the way Today writes `12 480 kg`.
 *
 * A comma would read as a decimal point to most of Europe, and this app has
 * no locale setting to disambiguate it.
 */
private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(" ").reversed()

@Composable
private fun Stat(
    label: String,
    value: String,
    valueColor: Color = TideColors.Text,
    divider: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TideColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = DataStyle, color = valueColor, textAlign = TextAlign.End)
    }
    if (divider) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(TideColors.Hairline))
    }
}

@Composable
private fun Scrim(content: @Composable ColumnScope.() -> Unit) {
    val shape = ContinuousCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(oceanScrimColor())
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun ImportReadyPreview() {
    TideTheme {
        ImportScreen(
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
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun ImportIdlePreview() {
    TideTheme { ImportScreen(ImportUiState.Idle) }
}
