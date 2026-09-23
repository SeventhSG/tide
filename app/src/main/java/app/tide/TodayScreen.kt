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
import androidx.compose.foundation.layout.aspectRatio
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
import app.tide.core.design.OceanBackground
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme
import app.tide.core.design.tidePress
import androidx.compose.material3.Icon
import app.tide.core.design.oceanScrimColor
import java.time.LocalDate

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
 *
 * **No nav bar in here.** [TideScaffold] owns the one bottom bar shared by
 * every main section; a second one embedded in this screen shipped briefly as
 * a leftover from before that existed, stacking two bars on top of each other.
 */
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
        Column(
            Modifier
                .fillMaxSize()
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

            // Only while there is still something to say: once a session is
            // open or something is already logged today, the plan has done
            // its job and repeating it would be noise.
            state.planned?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it.line,
                    style = LabelStyle,
                    color = TideColors.TextFaint,
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
            MonthCalendar(state)

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
                .tidePress(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TideIcons.Settings,
                contentDescription = "Settings",
                tint = TideColors.TextMuted,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/**
 * The month, at a glance: training, the plan and money renewals, all on one
 * grid, which is the one place this app puts them together.
 *
 * A filled square is a day actually trained. A hairline ring is a day the
 * plan says is a training day (Fixed only; a rotation has no weekday to mark
 * across a month, only a real answer for today). A small dot is a
 * subscription renewing. A square can carry more than one of these at once,
 * and none of it is a verdict: a blank day is drawn blank, and nothing is
 * said about it.
 *
 * The count reads "2 SESSIONS" rather than "2 of 4 done", because nothing has
 * declared how many days this week was meant to hold.
 */
@Composable
private fun MonthCalendar(state: TodayUiState) {
    Scrim {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("THIS MONTH", style = LabelStyle, color = TideColors.TextFaint)
            Spacer(Modifier.weight(1f))
            Text(state.weekSummary, style = DataStyle, color = TideColors.TextMuted)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    style = LabelStyle,
                    color = TideColors.TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        state.calendarMonth.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day -> CalendarCell(day, Modifier.weight(1f)) }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarCell(day: TodayUiState.CalendarDay, modifier: Modifier = Modifier) {
    if (day.date == null) {
        Box(modifier.aspectRatio(1f))
        return
    }
    val shape = ContinuousCornerShape(10.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(
                if (day.trained) TideColors.Accent.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.05f),
            )
            .border(
                1.dp,
                when {
                    day.isToday -> TideColors.Text.copy(alpha = 0.55f)
                    day.planned -> TideColors.Text.copy(alpha = 0.25f)
                    else -> Color.Transparent
                },
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.label,
            style = LabelStyle,
            color = if (day.trained) TideColors.OnAccent else TideColors.TextMuted,
        )
        if (day.moneyDue) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(TideColors.Warning),
            )
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
    val month = LocalDate.of(2026, 9, 1)
    TideTheme {
        TodayScreen(
            TodayUiState(
                dateLabel = "TUE 22 SEPTEMBER",
                headline = "No session\nyet today.",
                subline = "Last session was 2 days ago.",
                volumeLast7Days = "12 480 kg",
                calendarMonth = List(1) {
                    TodayUiState.CalendarDay(null, "", trained = false, planned = false, moneyDue = false, isToday = false)
                } + (1..30).map {
                    TodayUiState.CalendarDay(
                        date = month.withDayOfMonth(it),
                        label = it.toString(),
                        trained = it in listOf(14, 16, 18),
                        planned = it in listOf(14, 16, 18, 21, 23, 25, 28, 30),
                        moneyDue = it == 22,
                        isToday = it == 22,
                    )
                },
                weekSummary = "1 SESSION",
            ),
        )
    }
}
