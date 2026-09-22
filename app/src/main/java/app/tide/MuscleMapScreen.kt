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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.OceanIntensity
import app.tide.core.design.TideColors
import app.tide.core.design.TideTheme
import app.tide.core.design.oceanScrimColor

/**
 * What each muscle has had done to it lately.
 *
 * Three readings, on three tabs, kept apart because they answer different
 * questions. Rolling them into one "recovery score" would be the single most
 * tempting thing to build here and would hide all three behind a number
 * nobody could check.
 *
 * **Fatigue is drawn as a bar and never as a number.** It is a unitless index,
 * comparable only against your own other muscles. Printing "73%" next to it
 * would be inventing a measurement out of a model, which this app does not do.
 * The bars are relative to the heaviest reading on the screen, and the copy
 * says so.
 *
 * No body silhouette. A drawn figure implies the app knows where a muscle sits
 * and how much of it was worked, and it does not: it knows volume attributed
 * by a declared convention. A list is the honest shape for what is actually
 * known.
 */

enum class MuscleView { Balance, Fatigue, Strength }

/** Pure view state, so it renders in a screenshot test with no database. */
data class MuscleMapUiState(
    val windowLabel: String,
    val view: MuscleView,
    val rows: List<Row>,
) {
    data class Row(
        val muscle: String,
        /** 0 to 1, against the heaviest row in this view. Drives the bar only. */
        val fraction: Float,
        /** What to print at the right. Absent when there is nothing real to say. */
        val value: String?,
        val stale: Boolean = false,
    )
}

@Composable
fun MuscleMapScreen(
    state: MuscleMapUiState,
    onBack: () -> Unit = {},
    onSelectView: (MuscleView) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OceanBackground(modifier, intensity = OceanIntensity.Subtle) {
        Column(
            Modifier
                .fillMaxSize()
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
                Spacer(Modifier.weight(1f))
                Text(state.windowLabel, style = LabelStyle, color = TideColors.TextFaint)
            }

            Text(
                "Muscle map",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )

            Spacer(Modifier.height(16.dp))
            Tabs(state.view, onSelectView)

            Spacer(Modifier.height(14.dp))
            if (state.rows.isEmpty()) {
                Empty(state.view)
            } else {
                Scrim {
                    state.rows.forEachIndexed { i, row ->
                        Bar(row)
                        if (i < state.rows.lastIndex) Spacer(Modifier.height(12.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    caption(state.view),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TideColors.TextFaint,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun caption(view: MuscleView): String = when (view) {
    MuscleView.Balance ->
        "Volume is load times reps. A muscle worked as a secondary counts at half, " +
            "which is a convention, not a measurement."

    MuscleView.Fatigue ->
        "Relative to your own heaviest reading, not to anyone else and not to a maximum. " +
            "Recent work counts for more and fades smoothly rather than dropping out of a window."

    MuscleView.Strength ->
        "Days since the muscle was trained, and the best estimated 1RM of the lifts that " +
            "train it. An estimate, from Epley, not a number anything measured."
}

@Composable
private fun Tabs(selected: MuscleView, onSelect: (MuscleView) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MuscleView.entries.forEach { view ->
            val active = view == selected
            val shape = ContinuousCornerShape(14.dp)
            Box(
                Modifier
                    .clip(shape)
                    .background(
                        if (active) TideColors.Accent.copy(alpha = 0.16f)
                        else oceanScrimColor(),
                    )
                    .border(
                        1.dp,
                        if (active) TideColors.Accent.copy(alpha = 0.55f) else TideColors.Hairline,
                        shape,
                    )
                    .clickable(role = Role.Tab) { onSelect(view) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    view.name.uppercase(),
                    style = LabelStyle,
                    color = if (active) TideColors.Accent else TideColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun Bar(row: MuscleMapUiState.Row) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                row.muscle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.stale) TideColors.TextMuted else TideColors.Text,
                modifier = Modifier.weight(1f),
            )
            row.value?.let {
                Text(
                    it,
                    style = DataStyle,
                    color = if (row.stale) TideColors.Warning else TideColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(ContinuousCornerShape(4.dp))
                .background(TideColors.Text.copy(alpha = 0.07f)),
        ) {
            // A zero reading draws nothing at all rather than a sliver, so
            // "none" and "a little" cannot be confused at a glance.
            if (row.fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(row.fraction.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .clip(ContinuousCornerShape(4.dp))
                        .background(
                            if (row.stale) TideColors.Warning.copy(alpha = 0.55f)
                            else TideColors.Accent,
                        ),
                )
            }
        }
    }
}

@Composable
private fun Empty(view: MuscleView) {
    Scrim {
        Text(
            when (view) {
                MuscleView.Balance -> "Nothing logged in this window."
                MuscleView.Fatigue -> "Nothing recent enough to sit on anything."
                MuscleView.Strength -> "No lifts recorded yet."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TideColors.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Log a session, or import a history from another app.",
            style = MaterialTheme.typography.bodyMedium,
            color = TideColors.TextMuted,
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
private fun BalancePreview() {
    TideTheme {
        MuscleMapScreen(
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
    }
}
