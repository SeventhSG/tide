package app.tide

import app.tide.core.data.db.PlanMode
import app.tide.core.data.db.SessionEntity
import app.tide.core.data.money.MoneyRepository
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Joins [TodayScreen] to the database.
 *
 * Today was hardcoded from the day it was drawn: a volume figure, a sleep
 * average, a resting heart rate, renewals, a backup age and a week of training
 * that never happened. Every one of those is now either read from the database
 * or gone from the screen, which is the rule this app is built on: a number is
 * real, labelled as sample data, or absent.
 *
 * What is gone, and why: **sleep and resting heart rate** have no source until
 * Body reads Health Connect. **Renewals** have no source until Money exists.
 * **Last backup** has no source because nothing backs up yet. They come back
 * when there is something true to put in them.
 *
 * The screen is opened twenty-plus times a day, so it holds no scroll position
 * and animates nothing: state arrives, the screen is already correct.
 */
class TodayViewModel(
    private val repository: TrainingRepository,
    private val scope: CoroutineScope,
    /** Null in tests that are only about training. No renewal markers without it. */
    private val moneyRepository: MoneyRepository? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val _state = MutableStateFlow(TodayUiState(dateLabel = dateLabel(today())))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(
                repository.observeActiveSession(),
                repository.observeRecentSessions(),
            ) { active, recent -> active to recent }
                .collect { (active, recent) -> refresh(active, recent) }
        }
    }

    /** Called when the screen comes back to the front, in case the day turned over. */
    fun refresh() {
        scope.launch {
            val recent = repository.recentSessions()
            refresh(repository.activeSession(), recent)
        }
    }

    private suspend fun refresh(active: SessionEntity?, recent: List<SessionEntity>) {
        val today = today()
        val dayStart = startOfDay(today)
        val dayEnd = dayStart + DAY_MS

        val finished = recent.filter { it.endedAt != null }
        val trainedToday = finished.any { it.startedAt in dayStart until dayEnd }
        val setsToday = repository.workingSetCountBetween(dayStart, dayEnd)

        val monday = startOfDay(today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong()))
        val trainedDays = finished
            .filter { it.startedAt >= monday && it.startedAt < monday + 7 * DAY_MS }
            .map { ((it.startedAt - monday) / DAY_MS).toInt() }
            .toSet()

        val volume = repository.volumeBetween(now() - 7 * DAY_MS, now())

        // Once something is open or already logged today, the plan has
        // already done its job: repeating it would be the app checking in.
        val planned = if (active == null && !trainedToday) plannedLine() else null

        _state.value = TodayUiState(
            dateLabel = dateLabel(today),
            headline = when {
                active != null -> "Session in\nprogress."
                trainedToday -> "Trained\ntoday."
                else -> "No session\nyet today."
            },
            subline = when {
                active != null -> startedLabel(now() - active.startedAt)
                trainedToday -> setsLabel(setsToday)
                finished.isEmpty() -> "Nothing logged yet. The first session starts the history."
                else -> lastSessionLabel(finished.maxOf { it.startedAt }, dayStart)
            },
            resuming = active != null,
            // Absent rather than zero: a week with no training has no volume to
            // report, and "0 kg" reads as a measurement of nothing.
            volumeLast7Days = if (volume > 0) "${volume.toInt().grouped()} kg" else null,
            calendarMonth = calendarMonth(today),
            weekSummary = when (trainedDays.size) {
                0 -> "NONE YET"
                1 -> "1 SESSION"
                else -> "${trainedDays.size} SESSIONS"
            },
            planned = planned,
        )
    }

    /**
     * This month, everything on one grid: training, the plan and money
     * renewals together, which is the one place they otherwise never meet.
     *
     * Rotation plans mark no day here. A rotation slot has no weekday, and
     * only "today" has a real next-up answer; painting a guess across the
     * rest of the month would be inventing a plan the rotation never made.
     */
    private suspend fun calendarMonth(today: LocalDate): List<TodayUiState.CalendarDay> {
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.withDayOfMonth(today.lengthOfMonth())

        val sessions = repository.sessionsBetween(startOfDay(monthStart), startOfDay(monthEnd.plusDays(1)))
            .filter { it.endedAt != null }
        val trainedDates = sessions
            .map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .toSet()

        val plannedWeekdays = if (repository.planMode() == PlanMode.Fixed) {
            repository.planDays().filterValues { it.isNotEmpty() }.keys
        } else {
            emptySet()
        }

        val moneyDates = moneyRepository?.upcoming(monthStart, monthEnd)
            ?.map { it.date }
            ?.toSet()
            .orEmpty()

        // Weeks start on Monday, so the offset is how many blanks come first.
        val lead = (monthStart.dayOfWeek.value + 6) % 7
        val cells = buildList<LocalDate?> {
            repeat(lead) { add(null) }
            (1..monthEnd.dayOfMonth).forEach { add(monthStart.withDayOfMonth(it)) }
        }

        return cells.map { date ->
            TodayUiState.CalendarDay(
                date = date,
                label = date?.dayOfMonth?.toString() ?: "",
                trained = date != null && date in trainedDates,
                planned = date != null && (date.dayOfWeek.value - 1) in plannedWeekdays,
                moneyDue = date != null && date in moneyDates,
                isToday = date == today,
            )
        }
    }

    /**
     * One line naming what is planned, if the plan has anything real to say.
     * Fixed names no day, since "today" already means today. Rotation names
     * the slot, since a rotation day is not otherwise self-evident.
     */
    private suspend fun plannedLine(): TodayUiState.Planned? {
        val plan = repository.plannedToday() ?: return null
        val names = plan.exercises.joinToString(", ") { it.name }
        val label = when (repository.planMode()) {
            PlanMode.Rotation -> repository.planDayLabels()[plan.dayIndex]
            PlanMode.Fixed -> null
        }
        return TodayUiState.Planned(
            if (label != null) "Planned: $label, $names" else "Planned: $names",
        )
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun lastSessionLabel(lastStartedAt: Long, todayStart: Long): String {
        val days = ((todayStart - lastStartedAt) / DAY_MS) + 1
        return when {
            days <= 1 -> "Last session was yesterday."
            days < 14 -> "Last session was $days days ago."
            else -> "Last session was ${days / 7} weeks ago."
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class TodayUiState(
    val dateLabel: String = "",
    val headline: String = "No session\nyet today.",
    val subline: String? = null,
    /** True when a session is open, so the primary action continues it. */
    val resuming: Boolean = false,
    /** Null when nothing was logged in the window. Zero is not a reading. */
    val volumeLast7Days: String? = null,
    val calendarMonth: List<CalendarDay> = emptyList(),
    val weekSummary: String = "",
    /** Null whenever there is nothing planned, or nothing left to say about it today. */
    val planned: Planned? = null,
) {
    data class CalendarDay(
        /** Null for the blanks before the first of the month. */
        val date: LocalDate?,
        val label: String,
        val trained: Boolean,
        val planned: Boolean,
        val moneyDue: Boolean,
        val isToday: Boolean,
    )
    data class Planned(val line: String)
}

private fun dateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)).uppercase()

private fun startedLabel(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "Started a moment ago."
        minutes == 1L -> "Started a minute ago."
        minutes < 60 -> "Started $minutes minutes ago."
        else -> "Started ${minutes / 60} h ${minutes % 60} min ago."
    }
}

private fun setsLabel(sets: Int): String = when (sets) {
    0 -> "Only warm-ups logged today."
    1 -> "1 working set logged."
    else -> "$sets working sets logged."
}

/** Thousands separated by a space, as the muscle map does it. */
private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(" ").reversed()
