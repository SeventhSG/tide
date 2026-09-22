package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme
import app.tide.core.design.oceanScrimColor

/**
 * The training section.
 *
 * Tapping TRAIN used to drop you straight into a list of exercises, which is
 * the one thing you do not want when you are standing at home deciding whether
 * to go. This is the module's front door: what the last four weeks actually
 * held, then the way in.
 *
 * **Four counted figures, no score.** Sessions, working sets, volume and how
 * long since the last one. Each is a number you could reach yourself from the
 * log. There is no readiness percentage and no grade, because neither would be
 * a measurement of anything.
 */
@Composable
fun TrainScreen(
    state: TrainUiState,
    onStartSession: () -> Unit = {},
    onPlanner: () -> Unit = {},
    onMuscles: () -> Unit = {},
    onHistory: () -> Unit = {},
    onExercises: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            Text("TRAINING", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.sessionOpen) "Session in\nprogress." else "Train.",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )

            Spacer(Modifier.height(18.dp))
            TideButton(onClick = onStartSession, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (state.sessionOpen) "Resume session" else "Start a session",
                    style = MaterialTheme.typography.labelLarge,
                    color = TideColors.OnAccent,
                )
            }

            Spacer(Modifier.height(18.dp))
            if (state.hasHistory) {
                Scrim {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.windowLabel, style = LabelStyle, color = TideColors.TextFaint)
                        Spacer(Modifier.weight(1f))
                        state.lastTrainedLabel?.let {
                            Text(it, style = LabelStyle, color = TideColors.TextMuted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Figure("SESSIONS", state.sessions.toString(), Modifier.weight(1f))
                        Figure("WORKING SETS", state.workingSets.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    // Volume is absent rather than zero when nothing was lifted.
                    Figure("VOLUME", state.volume ?: "NONE", Modifier.fillMaxWidth())
                }
            } else {
                Scrim {
                    Text(
                        "Nothing logged yet.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "The first session starts the history, and the engine starts " +
                            "prescribing from it straight after.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TideGhostButton(onClick = onPlanner, modifier = Modifier.weight(1f)) {
                    Text(
                        "Plan the week",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                }
                TideGhostButton(onClick = onMuscles, modifier = Modifier.weight(1f)) {
                    Text(
                        "Muscle map",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.TextMuted,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TideGhostButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                    Text(
                        "History",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.TextMuted,
                    )
                }
                TideGhostButton(onClick = onExercises, modifier = Modifier.weight(1f)) {
                    Text(
                        "Exercises",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    val shape = ContinuousCornerShape(18.dp)
    Column(
        modifier
            .clip(shape)
            .background(TideColors.Accent.copy(alpha = 0.10f))
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = DataStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
            color = TideColors.Text,
        )
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
private fun TrainPreview() {
    TideTheme {
        TrainScreen(
            TrainUiState(
                windowLabel = "LAST 28 DAYS",
                sessions = 11,
                workingSets = 142,
                volume = "48 260 kg",
                lastTrainedLabel = "YESTERDAY",
                hasHistory = true,
            ),
        )
    }
}
