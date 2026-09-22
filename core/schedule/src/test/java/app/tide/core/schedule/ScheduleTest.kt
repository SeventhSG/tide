package app.tide.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Expanding rules onto the calendar.
 *
 * The awkward cases are the point: a monthly rule set for the 31st, an
 * every-other-week rule asked about a window that does not start on its
 * anchor, and a quota, which is the one most schedulers cannot express.
 */
class ScheduleTest {

    // A Monday, so the week arithmetic is easy to read.
    private val monday = LocalDate.of(2025, 9, 1)

    private fun rule(r: Recurrence, anchor: LocalDate = monday, until: LocalDate? = null) =
        ScheduleRule("r", r, anchor, until)

    private fun days(occurrences: List<Occurrence>) = occurrences.map { it.due }

    @Test
    fun `a daily rule fires every day`() {
        val out = Schedule.expand(rule(Recurrence.Daily()), monday, monday.plusDays(3))
        assertEquals(4, out.size)
        assertEquals(monday, out.first().due)
    }

    @Test
    fun `every three days keeps its phase, whatever window is asked about`() {
        val r = rule(Recurrence.Daily(every = 3))
        // Asked about a window starting two days after the anchor, the answer
        // must still fall on the anchor's rhythm, not on the window's start.
        val out = days(Schedule.expand(r, monday.plusDays(2), monday.plusDays(10)))
        assertEquals(
            listOf(monday.plusDays(3), monday.plusDays(6), monday.plusDays(9)),
            out,
        )
    }

    @Test
    fun `a weekly rule fires on its days only`() {
        val r = rule(Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val out = days(Schedule.expand(r, monday, monday.plusDays(13)))
        assertEquals(
            listOf(monday, monday.plusDays(2), monday.plusDays(7), monday.plusDays(9)),
            out,
        )
    }

    @Test
    fun `an every other week rule skips the week between`() {
        val r = rule(Recurrence.Weekly(setOf(DayOfWeek.MONDAY), everyWeeks = 2))
        val out = days(Schedule.expand(r, monday, monday.plusDays(28)))
        assertEquals(
            listOf(monday, monday.plusDays(14), monday.plusDays(28)),
            out,
        )
    }

    @Test
    fun `a monthly rule set for the 31st still fires in February`() {
        val r = rule(Recurrence.MonthlyByDay(31), anchor = LocalDate.of(2025, 1, 31))
        val out = days(
            Schedule.expand(r, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 30)),
        )
        assertEquals(
            "skipping February silently is how a renewal goes unnoticed",
            listOf(
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 3, 31),
                LocalDate.of(2025, 4, 30),
            ),
            out,
        )
    }

    @Test
    fun `the first Monday of each month`() {
        val r = rule(
            Recurrence.MonthlyByWeekday(1, DayOfWeek.MONDAY),
            anchor = LocalDate.of(2025, 9, 1),
        )
        val out = days(Schedule.expand(r, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 11, 30)))
        assertEquals(
            listOf(
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2025, 10, 6),
                LocalDate.of(2025, 11, 3),
            ),
            out,
        )
    }

    @Test
    fun `the last Friday of the month`() {
        val r = rule(
            Recurrence.MonthlyByWeekday(-1, DayOfWeek.FRIDAY),
            anchor = LocalDate.of(2025, 9, 1),
        )
        val out = days(Schedule.expand(r, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 31)))
        assertEquals(
            listOf(LocalDate.of(2025, 9, 26), LocalDate.of(2025, 10, 31)),
            out,
        )
    }

    @Test
    fun `a quota puts its occurrences across the whole week, not on a day`() {
        val r = rule(Recurrence.TimesPerWeek(3))
        val out = Schedule.expand(r, monday, monday.plusDays(6))

        assertEquals(3, out.size)
        assertTrue("a quota is not a dated obligation", out.all { it.isQuota })
        assertTrue(
            "any day of the week must be able to satisfy it",
            out.all { it.window == monday..monday.plusDays(6) },
        )
        assertEquals(listOf(0, 1, 2), out.map { it.index })
    }

    @Test
    fun `a rule stops at its end date`() {
        val r = rule(Recurrence.Daily(), until = monday.plusDays(2))
        assertEquals(3, Schedule.expand(r, monday, monday.plusDays(10)).size)
    }

    @Test
    fun `nothing fires before the anchor`() {
        val r = rule(Recurrence.Daily(), anchor = monday.plusDays(5))
        assertTrue(Schedule.expand(r, monday, monday.plusDays(4)).isEmpty())
    }

    @Test
    fun `a backwards window is empty rather than an error`() {
        assertTrue(Schedule.expand(rule(Recurrence.Daily()), monday, monday.minusDays(1)).isEmpty())
    }

    @Test
    fun `a rule that cannot mean anything is refused at construction`() {
        val bad = listOf(
            { Recurrence.Daily(every = 0) },
            { Recurrence.Weekly(emptySet()) },
            { Recurrence.MonthlyByDay(32) },
            { Recurrence.MonthlyByWeekday(0, DayOfWeek.MONDAY) },
            { Recurrence.TimesPerWeek(0) },
        )
        bad.forEach {
            runCatching { it() }.onSuccess { r -> throw AssertionError("$r should not be constructible") }
        }
    }

    @Test
    fun `several rules expand together, in date order`() {
        val out = Schedule.expandAll(
            listOf(
                ScheduleRule("a", Recurrence.Weekly(setOf(DayOfWeek.WEDNESDAY)), monday),
                ScheduleRule("b", Recurrence.Weekly(setOf(DayOfWeek.MONDAY)), monday),
            ),
            monday,
            monday.plusDays(6),
        )
        assertEquals(listOf("b", "a"), out.map { it.ruleId })
    }
}
