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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.tide.core.data.db.PlanMode
import app.tide.core.design.ContinuousCornerShape
import app.tide.core.design.DataStyle
import app.tide.core.design.ExerciseArt
import app.tide.core.design.LabelStyle
import app.tide.core.design.OceanBackground
import app.tide.core.design.OceanIntensity
import app.tide.core.design.TideButton
import app.tide.core.design.TideColors
import app.tide.core.design.TideGhostButton
import app.tide.core.design.TideTheme
import app.tide.core.design.tidePress

/**
 * The plan, in whichever of its two shapes it is in.
 *
 * Fixed shows seven slots along the top, always present, an empty one a rest
 * day. Rotation shows however many named slots exist, with a way to add,
 * rename, reorder and delete them, because a rotation is not a calendar week:
 * "Push" is not late on a Tuesday.
 *
 * Under either, the chosen slot's exercises, in the order they are meant to
 * be done. The order is the point: "squats first, then rows" is most of what
 * a training day is, so every line can move up, down, or into the slot
 * either side of it.
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
    onSwitchMode: (PlanMode) -> Unit = {},
    onAddDay: (String) -> Unit = {},
    onRenameDay: (Int, String) -> Unit = { _, _ -> },
    onRemoveDay: (Int) -> Unit = {},
    onMoveDayLeft: (Int) -> Unit = {},
    onMoveDayRight: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val day = state.slots.getOrNull(state.selectedDay)
    var pendingMode by remember { mutableStateOf<PlanMode?>(null) }
    var addingDay by remember { mutableStateOf(false) }
    var renamingDay by remember { mutableStateOf(false) }

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
                if (state.mode == PlanMode.Fixed) "Your week" else "Your rotation",
                style = MaterialTheme.typography.displayLarge,
                color = TideColors.Text,
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip("FIXED DAYS", state.mode == PlanMode.Fixed) {
                    if (state.mode != PlanMode.Fixed) pendingMode = PlanMode.Fixed
                }
                ModeChip("ROTATION", state.mode == PlanMode.Rotation) {
                    if (state.mode != PlanMode.Rotation) pendingMode = PlanMode.Rotation
                }
            }

            pendingMode?.let { target ->
                Spacer(Modifier.height(10.dp))
                ConfirmSwitch(
                    toFixed = target == PlanMode.Fixed,
                    onCancel = { pendingMode = null },
                    onConfirm = { onSwitchMode(target); pendingMode = null },
                )
            }

            Spacer(Modifier.height(18.dp))
            if (state.mode == PlanMode.Fixed) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    state.slots.forEach { s -> SlotChip(s, onSelectDay) }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.slots.forEach { s -> SlotChip(s, onSelectDay) }
                    AddDayChip { addingDay = true }
                }
            }

            if (addingDay) {
                Spacer(Modifier.height(10.dp))
                LabelEntry(
                    placeholder = "Name this day",
                    onCancel = { addingDay = false },
                    onSubmit = { onAddDay(it); addingDay = false },
                )
            }

            Spacer(Modifier.height(18.dp))
            if (day != null && !addingDay) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (renamingDay && state.mode == PlanMode.Rotation) {
                        LabelEntry(
                            placeholder = day.name,
                            initial = day.name,
                            onCancel = { renamingDay = false },
                            onSubmit = { onRenameDay(day.index, it); renamingDay = false },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            day.name.uppercase(),
                            style = LabelStyle,
                            color = TideColors.Text,
                            modifier = if (state.mode == PlanMode.Rotation) {
                                Modifier.tidePress { renamingDay = true }
                            } else {
                                Modifier
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        if (day.isCurrent) {
                            Text(
                                if (state.mode == PlanMode.Fixed) "TODAY" else "NEXT",
                                style = LabelStyle,
                                color = TideColors.Accent,
                            )
                        }
                    }
                }

                if (state.mode == PlanMode.Rotation && !renamingDay) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("< MOVE", enabled = state.slots.indexOf(day) > 0) {
                            onMoveDayLeft(day.index)
                        }
                        SmallAction("MOVE >", enabled = state.slots.indexOf(day) < state.slots.lastIndex) {
                            onMoveDayRight(day.index)
                        }
                        Spacer(Modifier.weight(1f))
                        SmallAction("DELETE DAY", critical = true) { onRemoveDay(day.index) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                if (state.exercises.isEmpty()) {
                    Card {
                        Text(
                            if (state.mode == PlanMode.Fixed) "Rest day." else "Nothing here yet.",
                            style = MaterialTheme.typography.labelLarge,
                            color = TideColors.Text,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.mode == PlanMode.Fixed) {
                                "Nothing is planned for this day. A week with three training " +
                                    "days in it is a plan, not a gap."
                            } else {
                                "Add the first exercise to ${day.name}."
                            },
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
            } else if (!addingDay) {
                Spacer(Modifier.height(10.dp))
                Card {
                    Text(
                        "No rotation yet.",
                        style = MaterialTheme.typography.labelLarge,
                        color = TideColors.Text,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Name a day above to start one. Push, then pull, then legs, " +
                            "on whichever days you actually train.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TideColors.TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = ContinuousCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) TideColors.Accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) TideColors.Accent.copy(alpha = 0.55f) else TideColors.Hairline,
                shape,
            )
            .tidePress(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = LabelStyle, color = if (selected) TideColors.Accent else TideColors.TextMuted)
    }
}

/**
 * Switching mode has nothing honest to carry across: a weekday and a
 * rotation position are different things wearing the same column, so the
 * plan is cleared rather than guessed at.
 */
@Composable
private fun ConfirmSwitch(toFixed: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Card {
        Text(
            if (toFixed) "Switch to fixed days?" else "Switch to a rotation?",
            style = MaterialTheme.typography.labelLarge,
            color = TideColors.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (toFixed) "This clears the rotation and starts Monday to Sunday empty." else {
                "This clears Monday to Sunday and starts the rotation empty."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TideColors.TextMuted,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TideGhostButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Keep this plan", style = MaterialTheme.typography.labelLarge, color = TideColors.Text)
            }
            TideButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text("Switch", style = MaterialTheme.typography.labelLarge, color = TideColors.OnAccent)
            }
        }
    }
}

@Composable
private fun LabelEntry(
    placeholder: String,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit,
    initial: String = "",
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    var draft by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val shape = ContinuousCornerShape(14.dp)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, TideColors.Accent.copy(alpha = 0.5f), shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TideColors.Text),
                cursorBrush = SolidColor(TideColors.Accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit(draft.text) }),
                decorationBox = { inner ->
                    if (draft.text.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TideColors.TextFaint)
                    }
                    inner()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { if (!it.isFocused) onCancel() },
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallAction("DONE") { onSubmit(draft.text) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun SlotChip(slot: PlannerUiState.Slot, onSelect: (Int) -> Unit) {
    val shape = ContinuousCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    slot.isSelected -> TideColors.Accent.copy(alpha = 0.18f)
                    slot.count > 0 -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Transparent
                },
            )
            .border(
                1.dp,
                when {
                    slot.isSelected -> TideColors.Accent.copy(alpha = 0.55f)
                    slot.isCurrent -> TideColors.Text.copy(alpha = 0.45f)
                    else -> Color.Transparent
                },
                shape,
            )
            .tidePress { onSelect(slot.index) }
            .padding(horizontal = 9.dp, vertical = 10.dp),
    ) {
        Text(
            slot.letter.uppercase(),
            style = LabelStyle,
            color = if (slot.isSelected) TideColors.Accent else TideColors.TextMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        // The count, not a tick. A planned day is a number of exercises.
        Text(
            if (slot.count == 0) "-" else slot.count.toString(),
            style = DataStyle,
            color = if (slot.count == 0) TideColors.TextFaint else TideColors.Text,
        )
    }
}

@Composable
private fun AddDayChip(onClick: () -> Unit) {
    val shape = ContinuousCornerShape(14.dp)
    Box(
        Modifier
            .clip(shape)
            .border(1.dp, TideColors.Hairline, shape)
            .tidePress(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = MaterialTheme.typography.labelLarge, color = TideColors.TextMuted)
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
private fun SmallAction(label: String, critical: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = ContinuousCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(
                if (critical) TideColors.Critical.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.05f),
            )
            .border(1.dp, TideColors.Hairline, shape)
            .tidePress(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = LabelStyle,
            color = when {
                !enabled -> TideColors.TextFaint.copy(alpha = 0.35f)
                critical -> TideColors.Critical
                else -> TideColors.TextMuted
            },
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
                mode = PlanMode.Fixed,
                slots = listOf(
                    PlannerUiState.Slot(0, "M", "Monday", 3, isCurrent = false, isSelected = true),
                    PlannerUiState.Slot(1, "T", "Tuesday", 0, isCurrent = true, isSelected = false),
                    PlannerUiState.Slot(2, "W", "Wednesday", 3, isCurrent = false, isSelected = false),
                    PlannerUiState.Slot(3, "T", "Thursday", 0, isCurrent = false, isSelected = false),
                    PlannerUiState.Slot(4, "F", "Friday", 4, isCurrent = false, isSelected = false),
                    PlannerUiState.Slot(5, "S", "Saturday", 0, isCurrent = false, isSelected = false),
                    PlannerUiState.Slot(6, "S", "Sunday", 0, isCurrent = false, isSelected = false),
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

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun PlannerRotationPreview() {
    TideTheme {
        PlannerScreen(
            PlannerUiState(
                mode = PlanMode.Rotation,
                slots = listOf(
                    PlannerUiState.Slot(0, "Push", "Push", 3, isCurrent = false, isSelected = false),
                    PlannerUiState.Slot(1, "Pull", "Pull", 3, isCurrent = true, isSelected = true),
                    PlannerUiState.Slot(2, "Legs", "Legs", 0, isCurrent = false, isSelected = false),
                ),
                selectedDay = 1,
                exercises = listOf(
                    PlannerUiState.Line(
                        "1", 1, "Barbell row", "3 x 8",
                        app.tide.core.data.db.Equipment.Barbell,
                        app.tide.core.data.db.Muscle.Back,
                        canMoveUp = false, canMoveDown = true,
                    ),
                    PlannerUiState.Line(
                        "2", 2, "Lat pulldown", "3 x 10",
                        app.tide.core.data.db.Equipment.Cable,
                        app.tide.core.data.db.Muscle.Lats,
                        canMoveUp = true, canMoveDown = false,
                    ),
                ),
                plannedTotal = 6,
                trainingDays = 2,
            ),
        )
    }
}
