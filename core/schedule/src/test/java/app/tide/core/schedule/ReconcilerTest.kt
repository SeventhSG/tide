package app.tide.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Resolving occurrences against the record.
 *
 * The first two tests are the ones that matter. Between them they say: do not
 * ask about something the database already knows, and do not call something
 * missed while there is still time to do it.
 */
class ReconcilerTest {

    private val monday = LocalDate.of(2025, 9, 1)
    private val sunday = monday.plusDays(6)

    private fun quota(times: Int) = Schedule.expand(
        ScheduleRule("train", Recurrence.TimesPerWeek(times), monday),
        monday,
        sunday,
    )

    private fun weekly(vararg days: DayOfWeek) = Schedule.expand(
        ScheduleRule("train", Recurrence.Weekly(days.toSet()), monday),
        monday,
        sunday,
    )

    @Test
    fun `a session already logged resolves the occurrence without asking`() {
        val out = Reconciler.reconcile(
            occurrences = weekly(DayOfWeek.MONDAY),
            evidence = listOf(monday),
            today = monday.plusDays(1),
        )
        assertEquals(OccurrenceState.Done, out.single().state)
        assertEquals(monday, out.single().evidenceOn)
    }

    @Test
    fun `nothing is missed while its window is still open`() {
        val out = Reconciler.reconcile(
            occurrences = quota(3),
            evidence = emptyList(),
            today = monday.plusDays(1),
        )
        assertTrue(
            "a Tuesday lifter is not behind on Monday evening",
            out.all { it.state == OccurrenceState.Due },
        )
    }

    @Test
    fun `a quota is satisfied by any days, not by particular ones`() {
        // Trained Tuesday and Saturday against a three times a week rule.
        val out = Reconciler.reconcile(
            occurrences = quota(3),
            evidence = listOf(monday.plusDays(1), monday.plusDays(5)),
            today = monday.plusDays(5),
        )
        assertEquals(2, out.count { it.state == OccurrenceState.Done })
        assertEquals(1, out.count { it.state == OccurrenceState.Due })
    }

    @Test
    fun `one session satisfies exactly one of a quota`() {
        val out = Reconciler.reconcile(
            occurrences = quota(3),
            evidence = listOf(monday),
            today = monday,
        )
        assertEquals(
            "one session must not clear the whole week",
            1,
            out.count { it.state == OccurrenceState.Done },
        )
    }

    @Test
    fun `two sessions in one day satisfy two of a quota`() {
        val out = Reconciler.reconcile(
            occurrences = quota(3),
            evidence = listOf(monday, monday),
            today = monday,
        )
        assertEquals(2, out.count { it.state == OccurrenceState.Done })
    }

    @Test
    fun `a closed window with nothing in it is missed`() {
        val out = Reconciler.reconcile(
            occurrences = quota(2),
            evidence = emptyList(),
            today = sunday.plusDays(1),
        )
        assertTrue(out.all { it.state == OccurrenceState.Missed })
    }

    @Test
    fun `evidence outside the window does not count`() {
        val out = Reconciler.reconcile(
            occurrences = weekly(DayOfWeek.MONDAY),
            evidence = listOf(monday.plusDays(3)),
            today = monday.plusDays(4),
        )
        assertEquals(
            "a Thursday session does not retroactively become Monday's",
            OccurrenceState.Missed,
            out.single().state,
        )
    }

    @Test
    fun `a skip is not a miss, because it was a decision`() {
        val occurrences = weekly(DayOfWeek.MONDAY)
        val ref = OccurrenceRef("train", monday)
        val out = Reconciler.reconcile(
            occurrences = occurrences,
            evidence = emptyList(),
            today = sunday.plusDays(1),
            skipped = setOf(ref),
        )
        assertEquals(OccurrenceState.Skipped, out.single().state)
    }

    @Test
    fun `earliest deadline first, so a tight window is not starved by a loose one`() {
        // A dated Monday obligation and a whole-week quota, with one session
        // logged on the Monday. The dated one must take it: the quota can
        // still be satisfied later in the week, and the Monday one cannot.
        val dated = weekly(DayOfWeek.MONDAY)
        val flexible = Schedule.expand(
            ScheduleRule("other", Recurrence.TimesPerWeek(1), monday),
            monday,
            sunday,
        )
        val out = Reconciler.reconcile(
            occurrences = dated + flexible,
            evidence = listOf(monday),
            today = monday,
        )

        val byRule = out.associateBy { it.occurrence.ruleId }
        assertEquals(OccurrenceState.Done, byRule.getValue("train").state)
        assertEquals(OccurrenceState.Due, byRule.getValue("other").state)
    }

    @Test
    fun `outstanding puts a closed window above an open one`() {
        val lastWeek = Schedule.expand(
            ScheduleRule("train", Recurrence.Weekly(setOf(DayOfWeek.MONDAY)), monday.minusWeeks(1)),
            monday.minusWeeks(1),
            sunday,
        )
        val out = Reconciler.outstanding(
            Reconciler.reconcile(lastWeek, emptyList(), today = monday.plusDays(1)),
        )
        assertEquals(OccurrenceState.Missed, out.first().state)
    }

    @Test
    fun `quota progress is a count, never a verdict`() {
        val resolved = Reconciler.reconcile(
            occurrences = quota(3),
            evidence = listOf(monday, monday.plusDays(2)),
            today = monday.plusDays(2),
        )
        val progress = Reconciler.quotaProgress(resolved, "train", monday..sunday)!!

        assertEquals(2, progress.done)
        assertEquals(3, progress.target)
        assertEquals(1, progress.remaining)
    }

    @Test
    fun `quota progress for a rule with no window returns nothing rather than zero`() {
        val resolved = Reconciler.reconcile(quota(1), emptyList(), monday)
        assertNull(
            "absent is not the same as none, and a zero here would read as a failure",
            Reconciler.quotaProgress(resolved, "nonexistent", monday..sunday),
        )
    }

    @Test
    fun `the result keeps the order it was given`() {
        val occurrences = quota(3)
        val out = Reconciler.reconcile(occurrences, listOf(monday), monday)
        assertEquals(occurrences, out.map { it.occurrence })
    }
}
