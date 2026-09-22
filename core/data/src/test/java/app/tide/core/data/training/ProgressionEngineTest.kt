package app.tide.core.data.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The progression engine is pure, so these run on the JVM in milliseconds and
 * on every commit. No Android, no device, no emulator.
 *
 * The cases that matter are not the happy ones. They are: a missed rep must
 * never advance the load, and a stall must eventually deload rather than
 * grinding the same weight forever.
 */
class ProgressionEngineTest {

    private fun sets(vararg reps: Int, load: Double? = 100.0) =
        SessionOutcome(reps.map { PerformedSet(it, load) })

    // --- linear -----------------------------------------------------------

    @Test
    fun `linear advances when every set meets the target`() {
        val r = ProgressionEngine.next(
            ProgressionRule.Linear(incrementKg = 2.5),
            Prescription(loadKg = 100.0, sets = 3, reps = 5),
            sets(5, 5, 5),
        )
        assertEquals(102.5, r.next.loadKg)
        assertEquals(0, r.stalls)
        assertFalse(r.deloaded)
    }

    @Test
    fun `linear holds the load when one set falls short`() {
        val r = ProgressionEngine.next(
            ProgressionRule.Linear(),
            Prescription(loadKg = 100.0, sets = 3, reps = 5),
            sets(5, 5, 4),
        )
        assertEquals("a single missed rep must not advance the load", 100.0, r.next.loadKg)
        assertEquals(1, r.stalls)
    }

    @Test
    fun `linear deloads on the third consecutive stall`() {
        val rule = ProgressionRule.Linear(stallsBeforeDeload = 3, deloadFactor = 0.90)
        val p = Prescription(loadKg = 100.0, sets = 3, reps = 5)

        val first = ProgressionEngine.next(rule, p, sets(5, 5, 4), stalls = 0)
        assertEquals(1, first.stalls); assertFalse(first.deloaded)

        val second = ProgressionEngine.next(rule, first.next, sets(5, 4, 4), first.stalls)
        assertEquals(2, second.stalls); assertFalse(second.deloaded)

        val third = ProgressionEngine.next(rule, second.next, sets(4, 4, 4), second.stalls)
        assertTrue(third.deloaded)
        assertEquals(90.0, third.next.loadKg)
        assertEquals("stall count resets after a deload", 0, third.stalls)
    }

    // --- greyskull --------------------------------------------------------

    @Test
    fun `greyskull double jumps when the last set is big`() {
        val r = ProgressionEngine.next(
            ProgressionRule.GreyskullLp(incrementKg = 2.5, doubleJumpAtReps = 10),
            Prescription(loadKg = 60.0, sets = 3, reps = 5),
            sets(5, 5, 11),
        )
        assertEquals(65.0, r.next.loadKg)
    }

    @Test
    fun `greyskull single jumps when the last set just meets the target`() {
        val r = ProgressionEngine.next(
            ProgressionRule.GreyskullLp(incrementKg = 2.5, doubleJumpAtReps = 10),
            Prescription(loadKg = 60.0, sets = 3, reps = 5),
            sets(5, 5, 5),
        )
        assertEquals(62.5, r.next.loadKg)
    }

    @Test
    fun `greyskull deloads immediately by default`() {
        val r = ProgressionEngine.next(
            ProgressionRule.GreyskullLp(stallsBeforeDeload = 1, deloadFactor = 0.90),
            Prescription(loadKg = 60.0, sets = 3, reps = 5),
            sets(5, 5, 3),
        )
        assertTrue(r.deloaded)
        assertEquals(54.0, r.next.loadKg)
    }

    // --- double progression ----------------------------------------------

    @Test
    fun `double progression adds load only at the top of the range`() {
        val rule = ProgressionRule.DoubleProgression(minReps = 6, maxReps = 9, incrementKg = 2.5)
        val p = Prescription(loadKg = 80.0, sets = 3, reps = 6, repCeiling = 9)

        val topped = ProgressionEngine.next(rule, p, sets(9, 9, 9))
        assertEquals(82.5, topped.next.loadKg)
        assertEquals("reps reset to the bottom of the range", 6, topped.next.reps)

        val inRange = ProgressionEngine.next(rule, p, sets(8, 7, 7))
        assertEquals("load is unchanged inside the range", 80.0, inRange.next.loadKg)
        assertEquals("target is one above the weakest set", 8, inRange.next.reps)
    }

    @Test
    fun `double progression stalls below the range and eventually deloads`() {
        val rule = ProgressionRule.DoubleProgression(
            minReps = 6, maxReps = 9, stallsBeforeDeload = 2, deloadFactor = 0.90,
        )
        val p = Prescription(loadKg = 80.0, sets = 3, reps = 6, repCeiling = 9)

        val first = ProgressionEngine.next(rule, p, sets(6, 5, 5), stalls = 0)
        assertEquals(80.0, first.next.loadKg)
        assertEquals(1, first.stalls)

        val second = ProgressionEngine.next(rule, first.next, sets(5, 5, 4), first.stalls)
        assertTrue(second.deloaded)
        assertEquals(72.0, second.next.loadKg)
    }

    // --- timed ------------------------------------------------------------

    @Test
    fun `timed grows the hold and then swaps to load at the ceiling`() {
        val rule = ProgressionRule.Timed(incrementSec = 5, ceilingSec = 60, loadIncrementKg = 2.5)

        val grow = ProgressionEngine.next(
            rule,
            Prescription(sets = 3, reps = 1, durationSec = 40, loadKg = 0.0),
            SessionOutcome(listOf(PerformedSet(1, 0.0, 40), PerformedSet(1, 0.0, 42))),
        )
        assertEquals(45, grow.next.durationSec)

        val ceiling = ProgressionEngine.next(
            rule,
            Prescription(sets = 3, reps = 1, durationSec = 60, loadKg = 0.0),
            SessionOutcome(listOf(PerformedSet(1, 0.0, 61))),
        )
        assertEquals(2.5, ceiling.next.loadKg)
        assertTrue("time drops back once load is added", ceiling.next.durationSec!! < 60)
    }

    @Test
    fun `timed holds the target when the hold was short`() {
        val r = ProgressionEngine.next(
            ProgressionRule.Timed(incrementSec = 5),
            Prescription(sets = 2, reps = 1, durationSec = 40),
            SessionOutcome(listOf(PerformedSet(1, null, 31), PerformedSet(1, null, 40))),
        )
        assertEquals("the weakest set decides", 40, r.next.durationSec)
    }

    // --- bodyweight -------------------------------------------------------

    @Test
    fun `bodyweight adds a set once every set reaches the ceiling`() {
        val r = ProgressionEngine.next(
            ProgressionRule.Bodyweight(repCeiling = 12, maxSets = 5),
            Prescription(loadKg = null, sets = 3, reps = 12),
            SessionOutcome(listOf(PerformedSet(12), PerformedSet(12), PerformedSet(12))),
        )
        assertEquals(4, r.next.sets)
        assertEquals(9, r.next.reps)
        assertEquals("bodyweight never invents a load", null, r.next.loadKg)
    }

    @Test
    fun `bodyweight stops adding sets at the cap and says so`() {
        val r = ProgressionEngine.next(
            ProgressionRule.Bodyweight(repCeiling = 12, maxSets = 5),
            Prescription(loadKg = null, sets = 5, reps = 12),
            SessionOutcome(List(5) { PerformedSet(12) }),
        )
        assertEquals(5, r.next.sets)
        assertTrue(r.reason.contains("add load", ignoreCase = true))
    }

    // --- invariants across every rule -------------------------------------

    @Test
    fun `no rule ever advances load on a missed session`() {
        val rules = listOf(
            ProgressionRule.Linear(stallsBeforeDeload = 99),
            ProgressionRule.GreyskullLp(stallsBeforeDeload = 99),
            ProgressionRule.DoubleProgression(stallsBeforeDeload = 99),
        )
        val start = Prescription(loadKg = 100.0, sets = 3, reps = 8, repCeiling = 10)
        rules.forEach { rule ->
            val r = ProgressionEngine.next(rule, start, sets(3, 3, 2))
            assertTrue(
                "${rule::class.simpleName} advanced the load after a missed session",
                (r.next.loadKg ?: 0.0) <= 100.0,
            )
        }
    }

    @Test
    fun `an empty session never advances anything`() {
        val r = ProgressionEngine.next(
            ProgressionRule.Linear(),
            Prescription(loadKg = 100.0, sets = 3, reps = 5),
            SessionOutcome(emptyList()),
        )
        assertEquals(100.0, r.next.loadKg)
    }

    @Test
    fun `loads always snap to a quarter kilo`() {
        val r = ProgressionEngine.next(
            ProgressionRule.Linear(deloadFactor = 0.9, stallsBeforeDeload = 1),
            Prescription(loadKg = 47.5, sets = 3, reps = 5),
            sets(4, 4, 4),
        )
        val load = r.next.loadKg!!
        assertEquals("47.5 * 0.9 = 42.75, which is already a quarter", 42.75, load, 0.0001)
        assertEquals(0.0, (load * 4) % 1.0, 0.0001)
    }
}
