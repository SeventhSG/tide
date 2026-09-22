package app.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.tide.core.design.LiquidGlassNav
import app.tide.core.design.NavItem
import app.tide.core.design.OceanBackground
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme
import app.tide.core.design.oceanScrimColor

/**
 * Today.
 *
 * A vertical priority stack, not a grid, and its shape changes across the week.
 * Opened twenty-plus times a day, so nothing animates on entry: content is
 * present at the first frame.
 *
 * Every number on it comes from [TodayViewModel], which reads the database.
 * Where there is no source yet, there is no row: sleep and resting heart rate
 * wait for Body, renewals wait for Money, and the backup age waits for
 * something that backs up. Inventing them was the first thing this screen did
 * and the first thing that had to go.
 */
private val navItems = listOf(
    NavItem("TODAY", TideIcons.Waves),
    NavItem("TRAIN", TideIcons.Barbell),
    NavItem("BODY", TideIcons.Pulse),
    NavItem("MONEY", TideIcons.Card),
    NavItem("ASK", TideIcons.Chat),
)

@Composable
fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    onStartSession: () -> Unit = {},
    onImport: () -> Unit = {},
    onMuscles: () -> Unit = {},
    onCalendar: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    OceanBackground(modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                TopBar(onSettings)

                Spacer(Modifier.height(26.dp))
                Text(state.dateLabel, style = LabelStyle, color = TideColors.TextFaint)

                Spacer(Modifier.height(8.dp))
                Text(
                    state.headline,
                    style = MaterialTheme.typography.displayLarge,
                    color = TideColors.Text,
                )

                state.subline?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TideColors.TextMuted,
                    )
                }

                // No "Skip today". Skipping is only meaningful against a plan,
                // and there is no planner yet, so the button would be a gesture
                // at a schedule that does not exist.
                Spacer(Modifier.height(20.dp))
                TideButton(onClick = onStartSession) {
                    Text(
                        if (state.resuming) "Resume session" else "Start session",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.OnAccent,
                    )
                }

                // One row, because one is what the database can answer today.
                // The section returns when Body and Money give it more.
                state.volumeLast7Days?.let {
                    Spacer(Modifier.height(22.dp))
                    Scrim {
                        Text("DRIFT", style = LabelStyle, color = TideColors.TextFaint)
                        Spacer(Modifier.height(6.dp))
                        DriftRow("Volume, 7 days", it, divider = false)
                    }
                }

                Spacer(Modifier.height(10.dp))
                WeekStrip(state)

                // Quiet, because both matter enormously on day one and rarely
                // after it.
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TideGhostButton(onClick = onCalendar, modifier = Modifier.weight(1f)) {
                        Text(
                            "History",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.TextMuted,
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
                TideGhostButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Import a history",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.TextMuted,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            // TRAIN opens the picker. The other three select nothing, because
            // Body, Money and Ask are not built and a tab that moved the
            // highlight onto an empty screen would be worse than an inert one.
            LiquidGlassNav(
                items = navItems,
                selected = 0,
                onSelect = { if (it == 1) onStartSession() },
            )
        }
    }
}

/**
 * No inbox button yet.
 *
 * It carried an unread dot and read "Inbox, 2 unread" to a screen reader, from
 * an inbox that does not exist. The dot is the right pattern for a real unread
 * count and it comes back with the module that can count one.
 */
@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("TIDE", style = LabelStyle, color = TideColors.TextMuted)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Text("SETTINGS", style = LabelStyle, color = TideColors.TextMuted)
        }
    }
}

/**
 * The week, at a glance: the days trained, and which one is today.
 *
 * Two states per day, not three. A "planned" day needs a plan, and until the
 * planner exists an empty square means only that nothing was logged. It is a
 * fact, and there is no red, no flame and no tally of what was missed.
 *
 * The count reads "2 SESSIONS" rather than "2 of 4 done", because nothing has
 * declared how many days this week was meant to hold.
 */
@Composable
private fun WeekStrip(state: TodayUiState) {
    Scrim {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THIS WEEK", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.weight(1f))
            Text(state.weekSummary, style = DataStyle, color = TideColors.TextMuted)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            state.week.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        d.letter,
                        style = LabelStyle,
                        color = if (d.isToday) TideColors.Text else TideColors.TextFaint,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(ContinuousCornerShape(11.dp))
                            .background(
                                if (d.trained) TideColors.Accent.copy(alpha = 0.90f)
                                else Color.White.copy(alpha = 0.05f),
                            )
                            .border(
                                1.dp,
                                if (d.isToday) TideColors.Text.copy(alpha = 0.55f)
                                else Color.Transparent,
                                ContinuousCornerShape(11.dp),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * The surface every readable thing sits on. The ocean shows around it and never
 * behind the glyphs, which is what keeps the contrast ratios honest.
 */
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

@Composable
private fun DriftRow(
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

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun TodayPreview() {
    TideTheme {
        TodayScreen(
            TodayUiState(
                dateLabel = "TUE 22 SEPTEMBER",
                headline = "No session\nyet today.",
                subline = "Last session was 2 days ago.",
                volumeLast7Days = "12 480 kg",
                week = listOf(
                    TodayUiState.Day("M", trained = true, isToday = false),
                    TodayUiState.Day("T", trained = false, isToday = true),
                    TodayUiState.Day("W", trained = false, isToday = false),
                    TodayUiState.Day("T", trained = false, isToday = false),
                    TodayUiState.Day("F", trained = false, isToday = false),
                    TodayUiState.Day("S", trained = false, isToday = false),
                    TodayUiState.Day("S", trained = false, isToday = false),
                ),
                weekSummary = "1 SESSION",
            ),
        )
    }
}
