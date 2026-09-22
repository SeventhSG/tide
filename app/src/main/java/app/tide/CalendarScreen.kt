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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.OceanIntensity
import app.tide.core.design.TideColors
import app.tide.core.design.TideTheme
import java.time.LocalDate

/**
 * The training history, a month at a time.
 *
 * The question this answers is "have I been going", and it answers it by
 * showing the days and letting the person look. **No streak, no flame, no
 * percentage of a target.** A day with nothing on it is drawn as a day with
 * nothing on it, and the screen says nothing about it.
 *
 * Tapping a day opens what was actually done: the exercises, their working
 * sets, the top set of each, and the day's volume. A day is only filled in
 * when a session finished, since an abandoned session is not training.
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onBack: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onSelectDay: (LocalDate?) -> Unit = {},
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
                Text(state.monthSummary, style = LabelStyle, color = TideColors.TextFaint)
            }

            Text(
                "History",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )

            Spacer(Modifier.height(16.dp))
            Scrim {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthStep("<", onPreviousMonth)
                    Spacer(Modifier.weight(1f))
                    Text(state.monthLabel, style = LabelStyle, color = TideColors.Text)
                    Spacer(Modifier.weight(1f))
                    // Forward stops at the current month. There is no history
                    // ahead of today, and a planner would be a different screen.
                    if (state.canGoForward) {
                        MonthStep(">", onNextMonth)
                    } else {
                        Spacer(Modifier.width(38.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(
                            it,
                            style = LabelStyle,
                            color = TideColors.TextFaint,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                state.days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day -> DayCell(day, onSelectDay, Modifier.weight(1f)) }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            state.selected?.let { detail ->
                Spacer(Modifier.height(10.dp))
                Scrim {
                    Text(
                        detail.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    if (detail.retroactive) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "LOGGED AFTER THE FACT",
                            style = LabelStyle,
                            color = TideColors.TextFaint,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (detail.exercises.isEmpty()) {
                        Text(
                            "A session, with no working sets in it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TideColors.TextMuted,
                        )
                    } else {
                        detail.exercises.forEach { line ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    line.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TideColors.Text,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(line.detail, style = LabelStyle, color = TideColors.TextMuted)
                            }
                        }
                    }
                    detail.volume?.let {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(TideColors.Hairline))
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("VOLUME", style = LabelStyle, color = TideColors.TextFaint)
                            Spacer(Modifier.weight(1f))
                            Text(it, style = DataStyle, color = TideColors.Accent)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarUiState.Day,
    onSelectDay: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (day.date == null) {
        Box(modifier.aspectRatio(1f))
        return
    }
    val shape = ContinuousCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(
                when {
                    day.trained -> TideColors.Accent.copy(alpha = 0.90f)
                    else -> Color.White.copy(alpha = 0.05f)
                },
            )
            .border(
                1.dp,
                when {
                    day.isSelected -> TideColors.Text
                    day.isToday -> TideColors.Text.copy(alpha = 0.55f)
                    else -> Color.Transparent
                },
                shape,
            )
            .clickable(role = Role.Button) { onSelectDay(day.date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.label,
            style = LabelStyle,
            color = if (day.trained) TideColors.OnAccent else TideColors.TextMuted,
        )
    }
}

@Composable
private fun MonthStep(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = DataStyle, color = TideColors.TextMuted)
    }
}

@Composable
private fun Scrim(content: @Composable ColumnScope.() -> Unit) {
    val shape = ContinuousCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TideColors.SurfaceRaised)
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun CalendarPreview() {
    val month = LocalDate.of(2026, 9, 1)
    TideTheme {
        CalendarScreen(
            CalendarUiState(
                monthLabel = "SEPTEMBER 2026",
                monthSummary = "3 SESSIONS",
                days = List(1) {
                    CalendarUiState.Day(null, "", trained = false, isToday = false, isSelected = false)
                } + (1..30).map {
                    CalendarUiState.Day(
                        date = month.withDayOfMonth(it),
                        label = it.toString(),
                        trained = it in listOf(14, 16, 18),
                        isToday = it == 22,
                        isSelected = it == 18,
                    )
                },
                selected = CalendarUiState.DayDetail(
                    title = "Friday 18 September",
                    exercises = listOf(
                        CalendarUiState.ExerciseLine("Back squat", "3 sets, top 100 kg x 5"),
                        CalendarUiState.ExerciseLine("Barbell row", "3 sets, top 82.5 kg x 8"),
                    ),
                    volume = "3 480 kg",
                    retroactive = false,
                ),
            ),
        )
    }
}
