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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.ExerciseArt
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.OceanIntensity
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideTheme
import app.tide.core.design.tidePress

/**
 * The week, planned.
 *
 * Seven days along the top, the chosen day's exercises underneath, in the order
 * they are meant to be done. The order is the point: "squats first, then rows"
 * is most of what a training day is, so every line can move up, down, or into
 * the day either side of it.
 *
 * **An empty day is a rest day** and says so. There is no rest day to create
 * and nothing asks you to fill it, because a week with three training days in
 * it is a plan, not a gap.
 */
@Composable
fun PlannerScreen(
    state: PlannerUiState,
    onBack: () -> Unit = {},
    onSelectDay: (Int) -> Unit = {},
    onAdd: () -> Unit = {},
    onMoveUp: (String) -> Unit = {},
    onMoveDown: (String) -> Unit = {},
    onMoveToDay: (String, Int) -> Unit = { _, _ -> },
    onRemove: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val day = state.days.getOrNull(state.selectedDay)

    OceanBackground(modifier, intensity = OceanIntensity.Off) {
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
                    Modifier.size(44.dp).clip(CircleShape).tidePress(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("<", style = DataStyle, color = TideColors.TextMuted)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        state.plannedTotal == 0 -> "NOTHING PLANNED"
                        state.trainingDays == 1 -> "${state.plannedTotal} ON 1 DAY"
                        else -> "${state.plannedTotal} ACROSS ${state.trainingDays} DAYS"
                    },
                    style = LabelStyle,
                    color = TideColors.TextFaint,
                )
            }

            Text(
                "Your week",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                state.days.forEach { d -> DayChip(d, onSelectDay) }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day?.name?.uppercase() ?: "",
                    style = LabelStyle,
                    color = TideColors.Text,
                )
                Spacer(Modifier.weight(1f))
                if (day?.isToday == true) {
                    Text("TODAY", style = LabelStyle, color = TideColors.Accent)
                }
            }

            Spacer(Modifier.height(10.dp))
            if (state.exercises.isEmpty()) {
                Card {
                    Text(
                        "Rest day.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Nothing is planned for this day. A week with three training days " +
                            "in it is a plan, not a gap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }
            } else {
                state.exercises.forEach { line ->
                    PlannedRow(
                        line = line,
                        selectedDay = state.selectedDay,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onMoveToDay = onMoveToDay,
                        onRemove = onRemove,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            TideButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Add an exercise",
                    style = MaterialTheme.typography.labelLarge,
                    color = TideColors.OnAccent,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DayChip(day: PlannerUiState.Day, onSelect: (Int) -> Unit) {
    val shape = ContinuousCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    day.isSelected -> TideColors.Accent.copy(alpha = 0.18f)
                    day.count > 0 -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Transparent
                },
            )
            .border(
                1.dp,
                when {
                    day.isSelected -> TideColors.Accent.copy(alpha = 0.55f)
                    day.isToday -> TideColors.Text.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
                shape,
            )
            .tidePress { onSelect(day.index) }
            .padding(horizontal = 9.dp, vertical = 10.dp),
    ) {
        Text(
            day.letter,
            style = LabelStyle,
            color = if (day.isSelected) TideColors.Accent else TideColors.TextMuted,
        )
        Spacer(Modifier.height(6.dp))
        // The count, not a tick. A planned day is a number of exercises.
        Text(
            if (day.count == 0) "-" else day.count.toString(),
            style = DataStyle,
            color = if (day.count == 0) TideColors.TextFaint else TideColors.Text,
        )
    }
}

@Composable
private fun PlannedRow(
    line: PlannerUiState.Line,
    selectedDay: Int,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onMoveToDay: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val shape = ContinuousCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TideColors.SurfaceRaised)
            .border(1.dp, TideColors.Hairline, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${line.position}",
                style = DataStyle,
                color = TideColors.TextFaint,
                modifier = Modifier.width(20.dp),
            )
            ExerciseArt(line.equipment.art(), line.muscle.region(), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    line.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TideColors.Text,
                )
                Spacer(Modifier.height(2.dp))
                Text(line.detail, style = LabelStyle, color = TideColors.TextFaint)
            }
            IconButton(TideIcons.ChevronUp, "Move up", line.canMoveUp) { onMoveUp(line.id) }
            IconButton(TideIcons.ChevronDown, "Move down", line.canMoveDown) { onMoveDown(line.id) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction("< DAY") { onMoveToDay(line.id, selectedDay - 1) }
            SmallAction("DAY >") { onMoveToDay(line.id, selectedDay + 1) }
            Spacer(Modifier.weight(1f))
            SmallAction("REMOVE", critical = true) { onRemove(line.id) }
        }
    }
}

@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(ContinuousCornerShape(14.dp))
            .tidePress(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) TideColors.TextMuted else TideColors.TextFaint.copy(alpha = 0.35f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SmallAction(label: String, critical: Boolean = false, onClick: () -> Unit) {
    val shape = ContinuousCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(
                if (critical) TideColors.Critical.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.05f),
            )
            .border(1.dp, TideColors.Hairline, shape)
            .tidePress(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = LabelStyle,
            color = if (critical) TideColors.Critical else TideColors.TextMuted,
        )
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
            .padding(horizontal = 16.dp, vertical = 16.dp),
        content = content,
    )
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun PlannerPreview() {
    TideTheme {
        PlannerScreen(
            PlannerUiState(
                days = listOf(
                    PlannerUiState.Day(0, "M", "Monday", 3, isToday = false, isSelected = true),
                    PlannerUiState.Day(1, "T", "Tuesday", 0, isToday = true, isSelected = false),
                    PlannerUiState.Day(2, "W", "Wednesday", 3, isToday = false, isSelected = false),
                    PlannerUiState.Day(3, "T", "Thursday", 0, isToday = false, isSelected = false),
                    PlannerUiState.Day(4, "F", "Friday", 4, isToday = false, isSelected = false),
                    PlannerUiState.Day(5, "S", "Saturday", 0, isToday = false, isSelected = false),
                    PlannerUiState.Day(6, "S", "Sunday", 0, isToday = false, isSelected = false),
                ),
                selectedDay = 0,
                exercises = listOf(
                    PlannerUiState.Line(
                        "1", 1, "Back squat", "3 x 5",
                        app.tide.core.data.db.Equipment.Barbell,
                        app.tide.core.data.db.Muscle.Quads,
                        canMoveUp = false, canMoveDown = true,
                    ),
                    PlannerUiState.Line(
                        "2", 2, "Bench press", "3 x 5",
                        app.tide.core.data.db.Equipment.Barbell,
                        app.tide.core.data.db.Muscle.Chest,
                        canMoveUp = true, canMoveDown = true,
                    ),
                    PlannerUiState.Line(
                        "3", 3, "Barbell row", "3 x 8",
                        app.tide.core.data.db.Equipment.Barbell,
                        app.tide.core.data.db.Muscle.Back,
                        canMoveUp = true, canMoveDown = false,
                    ),
                ),
                plannedTotal = 10,
                trainingDays = 3,
            ),
        )
    }
}
