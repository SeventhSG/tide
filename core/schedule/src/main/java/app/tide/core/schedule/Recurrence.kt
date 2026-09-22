package app.tide.core.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * How often something is meant to happen.
 *
 * Two kinds of thing live here, and keeping them apart is the whole design:
 *
 *  - **Dated.** Rent on the first, a lift on Monday. It happens on a day, and
 *    if that day passes it was missed.
 *  - **A quota.** Three sessions a week. It happens some number of times
 *    inside a period, and which days is nobody's business. Nothing is late on
 *    a Tuesday: you are one of three through the week, and that is a fact
 *    rather than a failure.
 *
 * Most schedulers only model the first and then force the second into it,
 * which is where "you missed Monday's workout" comes from for a person who
 * trains Tuesday, Thursday and Saturday and is perfectly on track.
 */
sealed interface Recurrence {

    /** Every day, or every [every] days from [anchor]. */
    data class Daily(val every: Int = 1) : Recurrence {
        init { require(every >= 1) { "a daily rule repeats at least every day" } }
    }

    /** On these weekdays, every [everyWeeks] weeks. */
    data class Weekly(
        val days: Set<DayOfWeek>,
        val everyWeeks: Int = 1,
    ) : Recurrence {
        init {
            require(days.isNotEmpty()) { "a weekly rule needs at least one day" }
            require(everyWeeks >= 1) { "a weekly rule repeats at least every week" }
        }
    }

    /**
     * On a day of the month, every [everyMonths] months.
     *
     * A [dayOfMonth] past the end of a short month lands on its last day, so a
     * rule set for the 31st still fires in February rather than skipping it.
     * Silently skipping a month is how a renewal goes unnoticed.
     */
    data class MonthlyByDay(
        val dayOfMonth: Int,
        val everyMonths: Int = 1,
    ) : Recurrence {
        init {
            require(dayOfMonth in 1..31) { "a day of the month is 1 to 31" }
            require(everyMonths >= 1) { "a monthly rule repeats at least every month" }
        }
    }

    /** The [week]th [day] of the month. Week -1 means the last one. */
    data class MonthlyByWeekday(
        val week: Int,
        val day: DayOfWeek,
    ) : Recurrence {
        init { require(week in -1..4 && week != 0) { "week is 1 to 4, or -1 for the last" } }
    }

    /**
     * A number of times inside a week, on no particular day.
     *
     * The rule most training actually follows, and the one that makes a
     * scheduler stop nagging.
     */
    data class TimesPerWeek(val times: Int) : Recurrence {
        init { require(times >= 1) { "a quota is at least one" } }
    }

    /** A number of times inside a calendar month, on no particular day. */
    data class TimesPerMonth(val times: Int) : Recurrence {
        init { require(times >= 1) { "a quota is at least one" } }
    }
}

/**
 * A rule, with the date it starts counting from and an optional end.
 *
 * [anchor] is what "every 3 days" counts from, and what decides which weeks
 * an every-other-week rule falls on. Without it those rules have no meaning.
 */
data class ScheduleRule(
    val id: String,
    val recurrence: Recurrence,
    val anchor: LocalDate,
    val until: LocalDate? = null,
)

/**
 * One thing that is meant to happen, resolved onto the calendar.
 *
 * [window] is what makes a quota work. A dated occurrence's window is its own
 * single day; a quota's window is the whole period, and any evidence inside it
 * counts. Everything downstream can then treat both the same way.
 */
data class Occurrence(
    val ruleId: String,
    /** The day this is anchored to. For a quota, the last day of its period. */
    val due: LocalDate,
    /** Any evidence inside this span satisfies it. */
    val window: ClosedRange<LocalDate>,
    /** Which of a quota's several. Always 0 for a dated rule. */
    val index: Int = 0,
) {
    val isQuota: Boolean get() = window.start != window.endInclusive
}

/**
 * Turning rules into occurrences over a span of dates.
 *
 * Pure, and deliberately not persisted. Occurrences are derived from the rule
 * every time rather than written into a table and kept in step, because a
 * materialised calendar and a rule that has since changed will disagree, and
 * the table always wins by accident.
 */
object Schedule {

    fun expand(rule: ScheduleRule, from: LocalDate, to: LocalDate): List<Occurrence> {
        if (from > to) return emptyList()
        val end = rule.until?.let { minOf(it, to) } ?: to
        if (end < from) return emptyList()

        return when (val r = rule.recurrence) {
            is Recurrence.Daily -> daily(rule, r, from, end)
            is Recurrence.Weekly -> weekly(rule, r, from, end)
            is Recurrence.MonthlyByDay -> monthlyByDay(rule, r, from, end)
            is Recurrence.MonthlyByWeekday -> monthlyByWeekday(rule, r, from, end)
            is Recurrence.TimesPerWeek -> timesPerWeek(rule, r, from, end)
            is Recurrence.TimesPerMonth -> timesPerMonth(rule, r, from, end)
        }
    }

    fun expandAll(rules: List<ScheduleRule>, from: LocalDate, to: LocalDate): List<Occurrence> =
        rules.flatMap { expand(it, from, to) }.sortedWith(compareBy({ it.due }, { it.ruleId }, { it.index }))

    private fun dated(rule: ScheduleRule, day: LocalDate) =
        Occurrence(rule.id, day, day..day)

    private fun daily(
        rule: ScheduleRule,
        r: Recurrence.Daily,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        // Step from the anchor rather than from the window, so "every 3 days"
        // keeps its phase no matter which fortnight is being asked about.
        var day = alignForward(rule.anchor, from, r.every)
        while (day <= to) {
            if (day >= rule.anchor) result += dated(rule, day)
            day = day.plusDays(r.every.toLong())
        }
        return result
    }

    private fun alignForward(anchor: LocalDate, from: LocalDate, stepDays: Int): LocalDate {
        if (from <= anchor) return anchor
        val gap = ChronoUnit.DAYS.between(anchor, from)
        val steps = (gap + stepDays - 1) / stepDays
        return anchor.plusDays(steps * stepDays)
    }

    private fun weekly(
        rule: ScheduleRule,
        r: Recurrence.Weekly,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        val anchorWeek = weekStart(rule.anchor)
        var day = maxOf(from, rule.anchor)
        while (day <= to) {
            if (day.dayOfWeek in r.days) {
                val weeksApart = ChronoUnit.WEEKS.between(anchorWeek, weekStart(day))
                if (weeksApart % r.everyWeeks == 0L) result += dated(rule, day)
            }
            day = day.plusDays(1)
        }
        return result
    }

    private fun monthlyByDay(
        rule: ScheduleRule,
        r: Recurrence.MonthlyByDay,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        var month = rule.anchor.withDayOfMonth(1)
        val lastMonth = to.withDayOfMonth(1)
        while (month <= lastMonth) {
            val monthsApart = ChronoUnit.MONTHS.between(rule.anchor.withDayOfMonth(1), month)
            if (monthsApart >= 0 && monthsApart % r.everyMonths == 0L) {
                // Clamped, not skipped. A rule set for the 31st must still
                // fire in February, or a renewal quietly goes unnoticed.
                val day = month.withDayOfMonth(minOf(r.dayOfMonth, month.lengthOfMonth()))
                if (day in from..to && day >= rule.anchor) result += dated(rule, day)
            }
            month = month.plusMonths(1)
        }
        return result
    }

    private fun monthlyByWeekday(
        rule: ScheduleRule,
        r: Recurrence.MonthlyByWeekday,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        var month = maxOf(rule.anchor, from).withDayOfMonth(1)
        val lastMonth = to.withDayOfMonth(1)
        while (month <= lastMonth) {
            nthWeekday(month, r.week, r.day)?.let { day ->
                if (day in from..to && day >= rule.anchor) result += dated(rule, day)
            }
            month = month.plusMonths(1)
        }
        return result
    }

    private fun nthWeekday(month: LocalDate, week: Int, day: DayOfWeek): LocalDate? {
        if (week == -1) {
            var d = month.withDayOfMonth(month.lengthOfMonth())
            while (d.dayOfWeek != day) d = d.minusDays(1)
            return d
        }
        var d = month
        while (d.dayOfWeek != day) d = d.plusDays(1)
        val result = d.plusWeeks((week - 1).toLong())
        return if (result.month == month.month) result else null
    }

    private fun timesPerWeek(
        rule: ScheduleRule,
        r: Recurrence.TimesPerWeek,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        var week = weekStart(maxOf(from, rule.anchor))
        while (week <= to) {
            val windowEnd = week.plusDays(6)
            // The whole week is the window, so training Tuesday and Saturday
            // satisfies two of three without either being "the Monday one".
            repeat(r.times) { i ->
                result += Occurrence(rule.id, windowEnd, week..windowEnd, i)
            }
            week = week.plusWeeks(1)
        }
        return result
    }

    private fun timesPerMonth(
        rule: ScheduleRule,
        r: Recurrence.TimesPerMonth,
        from: LocalDate,
        to: LocalDate,
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        var month = maxOf(from, rule.anchor).withDayOfMonth(1)
        while (month <= to) {
            val windowEnd = month.withDayOfMonth(month.lengthOfMonth())
            repeat(r.times) { i ->
                result += Occurrence(rule.id, windowEnd, month..windowEnd, i)
            }
            month = month.plusMonths(1)
        }
        return result
    }

    /**
     * Weeks start on Monday. ISO, and what a training week means to most people.
     *
     * Public because everything downstream has to agree with it. A caller that
     * decided weeks began on Sunday would draw a week that disagrees with the
     * quota it is reporting, and the two would be out by a day forever.
     */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - 1).toLong())
}
