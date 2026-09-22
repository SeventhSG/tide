package app.tide

import app.tide.core.data.db.SessionEntity
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The training history, by month.
 *
 * A calendar is the one view that answers "have I actually been going" without
 * anybody having to characterise the answer. It shows the days that hold a
 * session and no judgement about the days that do not: no streak, no flame, no
 * count of what was missed. A blank day is a blank day.
 *
 * Months are read one at a time rather than the whole history at once, because
 * a year of training is a lot of rows to hold in a screen that shows thirty.
 */
class CalendarViewModel(
    private val repository: TrainingRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    private var month: YearMonth = YearMonth.from(Instant.ofEpochMilli(now()).atZone(zone))
    private var selected: LocalDate? = null

    init {
        load()
    }

    fun onPreviousMonth() {
        month = month.minusMonths(1)
        selected = null
        load()
    }

    fun onNextMonth() {
        month = month.plusMonths(1)
        selected = null
        load()
    }

    fun onSelectDay(date: LocalDate?) {
        selected = if (selected == date) null else date
        load()
    }

    private fun load() {
        scope.launch {
            val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val sessions = repository.sessionsBetween(from, to).filter { it.endedAt != null }
            val byDate = sessions.groupBy { dateOf(it) }

            val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
            val first = month.atDay(1)
            // Weeks start on Monday, so the offset is how many blanks come first.
            val lead = (first.dayOfWeek.value + 6) % 7
            val cells = buildList {
                repeat(lead) { add(null) }
                (1..month.lengthOfMonth()).forEach { add(month.atDay(it)) }
            }

            _state.value = CalendarUiState(
                monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                    .uppercase(),
                canGoForward = month < YearMonth.from(today),
                days = cells.map { date ->
                    CalendarUiState.Day(
                        date = date,
                        label = date?.dayOfMonth?.toString() ?: "",
                        trained = date != null && byDate.containsKey(date),
                        isToday = date == today,
                        isSelected = date != null && date == selected,
                    )
                },
                monthSummary = summary(sessions.size, byDate.keys.size),
                selected = selected?.let { day -> detail(day, byDate[day].orEmpty()) },
            )
        }
    }

    private suspend fun detail(date: LocalDate, sessions: List<SessionEntity>): CalendarUiState.DayDetail {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val volume = repository.volumeBetween(dayStart, dayEnd)

        return CalendarUiState.DayDetail(
            title = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)),
            exercises = repository.sessionSummaries(sessions.map { it.id }).map {
                CalendarUiState.ExerciseLine(
                    name = it.exerciseName,
                    // Working sets only. A warm-up is kept in the history and
                    // counted nowhere, here as everywhere else.
                    detail = setsLine(it.workingSets, it.topLoadKg, it.topReps),
                )
            },
            volume = if (volume > 0) "${volume.toInt().grouped()} kg" else null,
            retroactive = sessions.any { it.isRetroactive },
        )
    }

    private fun dateOf(session: SessionEntity): LocalDate =
        Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()

    private fun summary(sessions: Int, days: Int): String = when {
        sessions == 0 -> "NOTHING LOGGED"
        sessions == days && sessions == 1 -> "1 SESSION"
        sessions == days -> "$sessions SESSIONS"
        else -> "$sessions SESSIONS, $days DAYS"
    }
}

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class CalendarUiState(
    val monthLabel: String = "",
    val days: List<Day> = emptyList(),
    val monthSummary: String = "",
    /** Null until a day is tapped. */
    val selected: DayDetail? = null,
    /** False in the current month. There is no history in the future. */
    val canGoForward: Boolean = false,
) {
    data class Day(
        /** Null for the blanks before the first of the month. */
        val date: LocalDate?,
        val label: String,
        val trained: Boolean,
        val isToday: Boolean,
        val isSelected: Boolean,
    )

    data class DayDetail(
        val title: String,
        val exercises: List<ExerciseLine>,
        /** Null when the day holds only warm-ups, or nothing with a load. */
        val volume: String?,
        val retroactive: Boolean,
    )

    data class ExerciseLine(val name: String, val detail: String)
}

private fun setsLine(sets: Int, topLoadKg: Double?, topReps: Int?): String {
    val count = if (sets == 1) "1 set" else "$sets sets"
    val top = when {
        topLoadKg != null && topReps != null -> ", top ${trim(topLoadKg)} kg x $topReps"
        topReps != null -> ", top x $topReps"
        else -> ""
    }
    return count + top
}

private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(" ").reversed()
