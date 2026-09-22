package app.tide

import app.tide.core.data.db.Equipment
import app.tide.core.data.db.Muscle
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
 * The week, as a plan rather than as a record.
 *
 * Seven days, each holding exercises in the order they are meant to be done,
 * because "first, second, third" is most of what a plan is. A day with nothing
 * in it is a rest day and is labelled as one: there is no rest day object to
 * create, and no prompt to fill it.
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

    /** Moves a line to the day before or after, landing at the end of it. */
    fun onMoveToDay(plannedId: String, dayIndex: Int) {
        scope.launch { edits.withLock {
            repository.movePlannedToDay(plannedId, ((dayIndex % 7) + 7) % 7)
            render()
        } }
    }

    fun refresh() {
        scope.launch { reload() }
    }

    private fun move(plannedId: String, up: Boolean) {
        scope.launch { edits.withLock {
            repository.movePlanned(plannedId, up)
            render()
        } }
    }

    private suspend fun reload() = edits.withLock { render() }

    private suspend fun render() {
        val byDay = repository.planDays()
        // Kept in step with every edit rather than on a save button, since the
        // plan has no save button.
        runCatching { planSchedule?.sync() }
        val todayIndex = Instant.ofEpochMilli(now()).atZone(zone).dayOfWeek.value - 1

        _state.value = PlannerUiState(
            days = (0..6).map { index ->
                val planned = byDay[index].orEmpty()
                PlannerUiState.Day(
                    index = index,
                    letter = DayOfWeek.of(index + 1)
                        .getDisplayName(java.time.format.TextStyle.NARROW, Locale.ENGLISH),
                    name = DayOfWeek.of(index + 1)
                        .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
                    count = planned.size,
                    isToday = index == todayIndex,
                    isSelected = index == selectedDay,
                )
            },
            selectedDay = selectedDay,
            exercises = byDay[selectedDay].orEmpty().mapIndexed { i, p ->
                PlannerUiState.Line(
                    id = p.id,
                    position = i + 1,
                    name = p.name,
                    detail = "${p.targetSets} x ${p.targetReps}",
                    equipment = p.equipment,
                    muscle = p.primaryMuscle,
                    canMoveUp = i > 0,
                    canMoveDown = i < byDay[selectedDay].orEmpty().lastIndex,
                )
            },
            plannedTotal = byDay.values.sumOf { it.size },
            trainingDays = byDay.count { it.value.isNotEmpty() },
        )
    }
}

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class PlannerUiState(
    val days: List<Day> = emptyList(),
    val selectedDay: Int = 0,
    val exercises: List<Line> = emptyList(),
    val plannedTotal: Int = 0,
    val trainingDays: Int = 0,
) {
    data class Day(
        val index: Int,
        val letter: String,
        val name: String,
        val count: Int,
        val isToday: Boolean,
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
