package app.tide

import app.tide.core.data.db.Equipment
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.PlanMode
import app.tide.core.data.training.TrainingRepository
import app.tide.notify.PlanSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * The plan, as one of two shapes.
 *
 * [PlanMode.Fixed] pins exercises to a weekday: seven slots, always present,
 * an empty one a rest day. [PlanMode.Rotation] pins them to a named, ordered
 * slot instead: push, then pull, then legs, on whichever days you actually
 * train. Both are the same screen and the same `dayIndex`-keyed storage; only
 * how the slots are labelled, how many there are, and what "current" means
 * differ, which is why one state shape and one set of handlers cover both.
 *
 * Switching mode clears the plan. `dayIndex` means a weekday in one and a
 * rotation position in the other, and reinterpreting one as the other would
 * be a guess, not a migration.
 *
 * Nothing here judges. The plan is what you intend; [CalendarScreen] is what
 * happened; the two are never scored against each other.
 */
class PlannerViewModel(
    private val repository: TrainingRepository,
    private val scope: CoroutineScope,
    /**
     * Writes the plan out as a schedule rule, so the daily summary knows which
     * days are training days. Null in tests that are only about the plan.
     */
    private val planSchedule: PlanSchedule? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val _state = MutableStateFlow(PlannerUiState())
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()

    private var selectedDay: Int = Instant.ofEpochMilli(now()).atZone(zone).dayOfWeek.value - 1

    /**
     * One edit at a time.
     *
     * Every action here is a write followed by a read, and the screen is a row
     * of buttons people press quickly. Without this, two overlapping edits can
     * finish out of order and the older read wins, leaving the screen showing a
     * plan that no longer exists. It is cheap: the lock is held for one
     * database round trip.
     */
    private val edits = Mutex()

    init {
        refresh()
    }

    fun onSelectDay(dayIndex: Int) {
        selectedDay = dayIndex
        refresh()
    }

    fun onAdd(exerciseId: String) {
        scope.launch { edits.withLock {
            repository.addToPlan(selectedDay, exerciseId)
            render()
        } }
    }

    fun onMoveUp(plannedId: String) = move(plannedId, up = true)

    fun onMoveDown(plannedId: String) = move(plannedId, up = false)

    fun onRemove(plannedId: String) {
        scope.launch { edits.withLock {
            repository.removeFromPlan(plannedId)
            render()
        } }
    }

    /** Moves a line to the neighbouring slot, wrapping around the real slot count. */
    fun onMoveToDay(plannedId: String, dayIndex: Int) {
        val count = _state.value.slots.size
        if (count == 0) return
        val target = ((dayIndex % count) + count) % count
        scope.launch { edits.withLock {
            repository.movePlannedToDay(plannedId, target)
            render()
        } }
    }

    /** Switches Fixed and Rotation. The caller confirms first; this just clears and switches. */
    fun onSwitchMode(mode: PlanMode) {
        scope.launch { edits.withLock {
            repository.setPlanMode(mode)
            selectedDay = 0
            render()
        } }
    }

    /** Rotation only. Names and selects a new slot at the end of the rotation. */
    fun onAddDay(label: String) {
        if (label.isBlank()) return
        scope.launch { edits.withLock {
            selectedDay = repository.addDay(label.trim())
            render()
        } }
    }

    fun onRenameDay(dayIndex: Int, label: String) {
        if (label.isBlank()) return
        scope.launch { edits.withLock {
            repository.renameDay(dayIndex, label.trim())
            render()
        } }
    }

    /** Rotation only. Deletes a slot and closes the gap it leaves. */
    fun onRemoveDay(dayIndex: Int) {
        scope.launch { edits.withLock {
            repository.removeDay(dayIndex)
            if (selectedDay > dayIndex) selectedDay -= 1
            render()
        } }
    }

    fun onMoveDayLeft(dayIndex: Int) = moveDay(dayIndex, up = true)

    fun onMoveDayRight(dayIndex: Int) = moveDay(dayIndex, up = false)

    fun refresh() {
        scope.launch { reload() }
    }

    private fun move(plannedId: String, up: Boolean) {
        scope.launch { edits.withLock {
            repository.movePlanned(plannedId, up)
            render()
        } }
    }

    private fun moveDay(dayIndex: Int, up: Boolean) {
        val target = if (up) dayIndex - 1 else dayIndex + 1
        scope.launch { edits.withLock {
            repository.moveDay(dayIndex, up)
            selectedDay = when (selectedDay) {
                dayIndex -> target
                target -> dayIndex
                else -> selectedDay
            }
            render()
        } }
    }

    private suspend fun reload() = edits.withLock { render() }

    private suspend fun render() {
        val routine = repository.plan()
        val byDay = repository.planDays()
        // Kept in step with every edit rather than on a save button, since the
        // plan has no save button.
        runCatching { planSchedule?.sync() }

        val todayIndex = Instant.ofEpochMilli(now()).atZone(zone).dayOfWeek.value - 1

        val slots: List<PlannerUiState.Slot> = when (routine.planMode) {
            PlanMode.Fixed -> (0..6).map { index ->
                val planned = byDay[index].orEmpty()
                PlannerUiState.Slot(
                    index = index,
                    letter = DayOfWeek.of(index + 1)
                        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH),
                    name = DayOfWeek.of(index + 1)
                        .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
                    count = planned.size,
                    isCurrent = index == todayIndex,
                    isSelected = index == selectedDay,
                )
            }
            PlanMode.Rotation -> {
                val labels = repository.planDayLabels()
                // The slot the app would actually open next, not a calendar day.
                val nextUp = repository.plannedToday()?.dayIndex
                labels.keys.sorted().map { index ->
                    val planned = byDay[index].orEmpty()
                    val label = labels.getValue(index)
                    PlannerUiState.Slot(
                        index = index,
                        letter = label,
                        name = label,
                        count = planned.size,
                        isCurrent = index == nextUp,
                        isSelected = index == selectedDay,
                    )
                }
            }
        }

        if (selectedDay !in slots.indices) selectedDay = 0
        val dayExercises = byDay[selectedDay].orEmpty()

        _state.value = PlannerUiState(
            mode = routine.planMode,
            slots = slots,
            selectedDay = selectedDay,
            exercises = dayExercises.mapIndexed { i, p ->
                PlannerUiState.Line(
                    id = p.id,
                    position = i + 1,
                    name = p.name,
                    detail = "${p.targetSets} x ${p.targetReps}",
                    equipment = p.equipment,
                    muscle = p.primaryMuscle,
                    canMoveUp = i > 0,
                    canMoveDown = i < dayExercises.lastIndex,
                )
            },
            plannedTotal = byDay.values.sumOf { it.size },
            trainingDays = byDay.count { it.value.isNotEmpty() },
        )
    }
}

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class PlannerUiState(
    val mode: PlanMode = PlanMode.Fixed,
    val slots: List<Slot> = emptyList(),
    val selectedDay: Int = 0,
    val exercises: List<Line> = emptyList(),
    val plannedTotal: Int = 0,
    val trainingDays: Int = 0,
) {
    data class Slot(
        val index: Int,
        /** The chip's short text: a narrow weekday letter, or a rotation label. */
        val letter: String,
        /** The full name shown above the exercise list. */
        val name: String,
        val count: Int,
        /** Fixed: today's weekday. Rotation: the slot the app would open next. */
        val isCurrent: Boolean,
        val isSelected: Boolean,
    )

    data class Line(
        val id: String,
        /** Its place in the day: first, second, third. */
        val position: Int,
        val name: String,
        val detail: String,
        val equipment: Equipment?,
        val muscle: Muscle?,
        val canMoveUp: Boolean,
        val canMoveDown: Boolean,
    )
}
