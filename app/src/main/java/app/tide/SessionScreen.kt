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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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

/**
 * The active session logger.
 *
 * This is the screen that decides whether the app gets used. It is a thumb
 * surface, operated sweaty, one handed, mid set, at a hundred taps a session.
 * Everything here follows from that:
 *
 *  - **No ocean.** [OceanIntensity.Off]. Mid set you do not need bubbles, and
 *    the battery is better spent on the rest timer.
 *  - **Nothing animates** except the press response. At this frequency any
 *    motion becomes an obstacle between you and the next set.
 *  - **Targets are 56dp**, above the 48dp floor, because the tap is made with a
 *    thumb while breathing hard.
 *  - **The prescription is prefilled** from the progression engine, so the
 *    common case is one tap: log it and move on.
 */

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class SessionUiState(
    val exerciseName: String,
    val setNumber: Int,
    /**
     * Null when nothing has prescribed a set count yet. The header then reads
     * "SET 3" rather than inventing a total to count towards.
     */
    val targetSets: Int?,
    val ruleLabel: String,
    val loadKg: String,
    val reps: String,
    val lastTime: String?,
    val elapsed: String,
    val restRemaining: String?,
    val logged: List<LoggedSet>,
    /** Every set in the session, any exercise, warm-ups included. */
    val sessionSetCount: Int = 0,
    /** The sets in the session that can move a target. */
    val workingSetCount: Int = 0,
    val confirmingFinish: Boolean = false,
    /** Set once the session has finished. The screen then shows only this. */
    val summary: SessionSummary? = null,
) {
    data class LoggedSet(
        val index: String,
        val summary: String,
        val rir: String?,
        val isWarmUp: Boolean,
    )
}

/**
 * What finishing decided. Every line comes from the progression engine: the
 * reason is its own plain-language explanation, not copy written here.
 */
data class SessionSummary(
    val duration: String,
    val workingSets: Int,
    /** False when nothing was logged and the session was discarded. */
    val saved: Boolean,
    val exercises: List<Decision>,
) {
    data class Decision(
        val exerciseName: String,
        val reason: String,
        val next: String,
    )
}

@Composable
fun SessionScreen(
    state: SessionUiState,
    onBack: () -> Unit = {},
    onLoadChange: (Double) -> Unit = {},
    onRepsChange: (Int) -> Unit = {},
    onLogSet: () -> Unit = {},
    onFinishRequested: () -> Unit = {},
    onFinishCancelled: () -> Unit = {},
    onFinishConfirmed: () -> Unit = {},
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The ocean is off here, deliberately. See the note above.
    OceanBackground(modifier, intensity = OceanIntensity.Off) {
        state.summary?.let {
            SummaryContent(it, onDone)
            return@OceanBackground
        }
        Column(
            Modifier
                .fillMaxSize()
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
                Spacer(Modifier.weight(1f))
                Text(state.elapsed, style = LabelStyle, color = TideColors.TextFaint)
                Spacer(Modifier.width(12.dp))
                // Up here, away from the thumb. Finishing happens once a session
                // and logging a hundred times, so the two never share a reach.
                Box(
                    Modifier
                        .height(44.dp)
                        .clip(ContinuousCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, TideColors.Hairline, ContinuousCornerShape(14.dp))
                        .clickable(role = Role.Button, onClick = onFinishRequested)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("FINISH", style = LabelStyle, color = TideColors.Text)
                }
            }

            Text(
                state.exerciseName,
                style = MaterialTheme.typography.titleLarge,
                color = TideColors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.targetSets?.let { "SET ${state.setNumber} OF $it" }
                        ?: "SET ${state.setNumber}",
                    style = LabelStyle,
                    color = TideColors.TextFaint,
                )
                Spacer(Modifier.width(10.dp))
                Text(state.ruleLabel, style = LabelStyle, color = TideColors.Accent)
            }

            Spacer(Modifier.height(16.dp))
            Card {
                state.logged.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            s.index,
                            style = DataStyle,
                            color = TideColors.TextFaint,
                            modifier = Modifier.width(26.dp),
                        )
                        Text(
                            s.summary,
                            style = DataStyle,
                            color = if (s.isWarmUp) TideColors.TextMuted else TideColors.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            s.rir ?: if (s.isWarmUp) "warm-up" else "",
                            style = LabelStyle,
                            color = if (s.isWarmUp) TideColors.TextFaint else TideColors.Accent,
                        )
                    }
                    if (i < state.logged.lastIndex) Hairline()
                }
                if (state.logged.isEmpty()) {
                    Text(
                        "No sets yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Card {
                Stepper(
                    "LOAD, KG",
                    state.loadKg,
                    onDecrement = {
                        onLoadChange(((state.loadKg.toDoubleOrNull() ?: 0.0) - LoadStepKg).coerceAtLeast(0.0))
                    },
                    onIncrement = {
                        onLoadChange((state.loadKg.toDoubleOrNull() ?: 0.0) + LoadStepKg)
                    },
                )
                Spacer(Modifier.height(14.dp))
                Stepper(
                    "REPS",
                    state.reps,
                    onDecrement = {
                        onRepsChange(((state.reps.toIntOrNull() ?: 0) - 1).coerceAtLeast(0))
                    },
                    onIncrement = {
                        onRepsChange((state.reps.toIntOrNull() ?: 0) + 1)
                    },
                )
                state.lastTime?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Last time: $it", style = LabelStyle, color = TideColors.TextFaint)
                }
            }

            state.restRemaining?.let { rest ->
                Spacer(Modifier.height(12.dp))
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("REST", style = LabelStyle, color = TideColors.TextFaint)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                rest,
                                style = DataStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                                color = TideColors.Text,
                            )
                        }
                        StepButton("-30")
                        Spacer(Modifier.width(8.dp))
                        StepButton("+30")
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (state.confirmingFinish) {
                ConfirmFinish(state.sessionSetCount, state.workingSetCount, onFinishCancelled, onFinishConfirmed)
            } else {
                TideButton(onClick = onLogSet, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Log set ${state.setNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.OnAccent,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "SCREEN STAYS AWAKE",
                style = LabelStyle,
                color = TideColors.TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Asked once, because finishing is the one tap here that cannot be undone: it
 * runs the engine and sets every lift's next target.
 */
@Composable
private fun ConfirmFinish(setCount: Int, workingCount: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Card {
        Text(
            "Finish this session?",
            style = MaterialTheme.typography.titleMedium,
            color = TideColors.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // Counted in working sets, because those are what set a target.
            // A warm-up counted here would promise a decision it cannot make.
            when {
                setCount == 0 -> "Nothing is logged yet, so finishing discards it."
                workingCount == 0 -> "Only warm-ups so far. They are kept, and they move no target."
                workingCount == 1 -> "1 working set logged. Next targets are set from it."
                else -> "$workingCount working sets logged. Next targets are set from them."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TideColors.TextMuted,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TideGhostButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("Keep going", style = MaterialTheme.typography.labelLarge, color = TideColors.Text)
        }
        TideButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
            Text("Finish", style = MaterialTheme.typography.labelLarge, color = TideColors.OnAccent)
        }
    }
}

@Composable
private fun SummaryContent(summary: SessionSummary, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(52.dp))
        Text(
            if (summary.saved) "Session done" else "Nothing logged",
            style = MaterialTheme.typography.titleLarge,
            color = TideColors.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (summary.saved) {
                val sets = if (summary.workingSets == 1) "1 WORKING SET" else "${summary.workingSets} WORKING SETS"
                "${summary.duration}, $sets"
            } else {
                summary.duration
            },
            style = LabelStyle,
            color = TideColors.TextFaint,
        )
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                !summary.saved -> Card {
                    Text(
                        "The session was empty, so it was not kept.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }
                summary.exercises.isEmpty() -> Card {
                    Text(
                        "Only warm-ups were logged. They are kept, and they never move a target.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }
                else -> summary.exercises.forEach { d ->
                    Card {
                        Text(d.exerciseName, style = MaterialTheme.typography.titleMedium, color = TideColors.Text)
                        Spacer(Modifier.height(4.dp))
                        Text(d.reason, style = MaterialTheme.typography.bodyMedium, color = TideColors.TextMuted)
                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NEXT TIME", style = LabelStyle, color = TideColors.TextFaint)
                            Spacer(Modifier.weight(1f))
                            Text(d.next, style = DataStyle, color = TideColors.Accent)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        TideButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done", style = MaterialTheme.typography.labelLarge, color = TideColors.OnAccent)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val shape = ContinuousCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TideColors.SurfaceRaised)
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(TideColors.Hairline))
}

/** Load steps 2.5 kg at a tap, the smallest plate change worth a button press. */
private const val LoadStepKg = 2.5

@Composable
private fun Stepper(
    label: String,
    value: String,
    onDecrement: () -> Unit = {},
    onIncrement: () -> Unit = {},
) {
    Column {
        Text(label, style = LabelStyle, color = TideColors.TextFaint)
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepButton("-", onDecrement)
            Box(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(ContinuousCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, TideColors.Hairline, ContinuousCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    value,
                    style = DataStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                    color = TideColors.Text,
                )
            }
            StepButton("+", onIncrement)
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit = {}) {
    Box(
        Modifier
            .size(56.dp)
            .clip(ContinuousCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, TideColors.Hairline, ContinuousCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DataStyle, color = TideColors.TextMuted)
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun SessionPreview() {
    TideTheme {
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
