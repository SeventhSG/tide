package app.tide.core.data.analysis

import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetWithMuscles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The muscle map, as pure arithmetic.
 *
 * No database here. The queries feed this and are tested separately; what
 * matters in this file is that the three readings mean what they claim,
 * especially fatigue, which is the one a reader is most likely to mistake for
 * a measurement.
 */
class MuscleMapTest {

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun set(
        exerciseId: String = "row",
        primary: Muscle = Muscle.Back,
        secondary: String = "",
        loadKg: Double? = 100.0,
        reps: Int? = 5,
        durationSec: Int? = null,
        agoMillis: Long = 0,
    ) = SetWithMuscles(
        exerciseId = exerciseId,
        loadKg = loadKg,
        reps = reps,
        durationSec = durationSec,
        completedAt = now - agoMillis,
        primaryMuscle = primary,
        secondaryMuscles = secondary,
    )

    private fun readings(vararg sets: SetWithMuscles, halfLife: Double = 48.0) =
        MuscleMap.analyse(sets.toList(), now = now, halfLifeHours = halfLife)
            .associateBy { it.muscle }

    @Test
    fun `volume is load times reps, on the primary muscle`() {
        val r = readings(set(loadKg = 100.0, reps = 5))
        assertEquals(500.0, r[Muscle.Back]!!.volumeKg, 0.001)
    }

    @Test
    fun `a secondary muscle is credited at the declared share, not at zero`() {
        val r = readings(set(primary = Muscle.Lats, secondary = "Biceps"))

        assertEquals(500.0, r[Muscle.Lats]!!.volumeKg, 0.001)
        assertEquals(
            "counting the biceps at zero makes pulling days look like they never touch them",
            250.0,
            r[Muscle.Biceps]!!.volumeKg,
            0.001,
        )
    }

    @Test
    fun `a muscle listed as both primary and secondary is not counted twice`() {
        val r = readings(set(primary = Muscle.Back, secondary = "Back,Biceps"))
        assertEquals(500.0, r[Muscle.Back]!!.volumeKg, 0.001)
    }

    @Test
    fun `an unreadable muscle name costs one bar, not the whole screen`() {
        val r = readings(set(primary = Muscle.Back, secondary = "Biceps,Tentacles"))
        assertEquals(500.0, r[Muscle.Back]!!.volumeKg, 0.001)
        assertEquals(250.0, r[Muscle.Biceps]!!.volumeKg, 0.001)
        assertEquals("the bad name is skipped, the good one survives", 2, r.size)
    }

    @Test
    fun `fatigue decays smoothly, with no cliff at a window edge`() {
        val fresh = readings(set(agoMillis = 0))[Muscle.Back]!!.fatigueIndex
        val twoDays = readings(set(agoMillis = 2 * day))[Muscle.Back]!!.fatigueIndex
        val fourDays = readings(set(agoMillis = 4 * day))[Muscle.Back]!!.fatigueIndex

        // Half life is two days, so each step halves it.
        assertEquals(fresh / 2, twoDays, fresh * 0.01)
        assertEquals(fresh / 4, fourDays, fresh * 0.01)
        assertTrue("and it never reaches zero, which is the point", fourDays > 0.0)
    }

    @Test
    fun `identical volume taken heavier carries more fatigue`() {
        // Same work, 100x5 against 50x10, against a 120 kg best.
        val best = mapOf("row" to 120.0)
        fun fatigueOf(load: Double, reps: Int) = MuscleMap.analyse(
            listOf(set(loadKg = load, reps = reps)),
            best1rmByExercise = best,
            now = now,
        ).single().fatigueIndex

        val heavy = fatigueOf(100.0, 5)
        val light = fatigueOf(50.0, 10)
        assertEquals("the volume really is the same", 500.0, 100.0 * 5, 0.001)
        assertTrue(
            "a set near the top of the range costs more than the same volume taken light",
            heavy > light,
        )
    }

    @Test
    fun `an unknown best 1RM adds no intensity rather than guessing a middle`() {
        val withoutReference = MuscleMap.analyse(listOf(set()), now = now).single()
        val withReference = MuscleMap.analyse(
            listOf(set()),
            best1rmByExercise = mapOf("row" to 100.0),
            now = now,
        ).single()

        // At the full reference the weight doubles; with no reference it is 1.
        assertEquals(500.0, withoutReference.fatigueIndex, 0.001)
        assertEquals(1000.0, withReference.fatigueIndex, 0.001)
    }

    @Test
    fun `a bodyweight set counts its reps rather than nothing`() {
        val r = readings(set(loadKg = null, reps = 8, primary = Muscle.Lats))
        assertEquals(
            "counting an unloaded chin-up as zero makes the day invisible",
            8.0,
            r[Muscle.Lats]!!.volumeKg,
            0.001,
        )
    }

    @Test
    fun `a timed hold counts its seconds`() {
        val r = readings(set(loadKg = null, reps = null, durationSec = 90, primary = Muscle.Abs))
        assertEquals(90.0, r[Muscle.Abs]!!.volumeKg, 0.001)
    }

    @Test
    fun `strength reads only from the lift that trains the muscle primarily`() {
        val r = MuscleMap.analyse(
            listOf(set(primary = Muscle.Lats, secondary = "Biceps")),
            best1rmByExercise = mapOf("row" to 120.0),
            now = now,
        ).associateBy { it.muscle }

        assertEquals(120.0, r[Muscle.Lats]!!.bestEstimated1rmKg!!, 0.001)
        assertNull(
            "a row's 1RM is not a number about your biceps",
            r[Muscle.Biceps]!!.bestEstimated1rmKg,
        )
    }

    @Test
    fun `days since trained counts whole days`() {
        val r = readings(set(agoMillis = 3 * day + 5 * hour))
        assertEquals(3, r[Muscle.Back]!!.daysSinceTrained)
    }

    @Test
    fun `a muscle trained before the window still reports when it was trained`() {
        // Nothing in the window at all, and a squat five weeks back.
        val library = listOf(
            ExerciseEntity("squat", "Back squat", Muscle.Quads, secondaryMuscles = "Glutes"),
        )
        val readings = MuscleMap.analyse(
            sets = emptyList(),
            library = library,
            lastTrainedByExercise = mapOf("squat" to now - 35 * day),
            now = now,
        ).associateBy { it.muscle }

        assertEquals(
            "a muscle untouched for five weeks is exactly what this map is for",
            35,
            readings[Muscle.Quads]!!.daysSinceTrained,
        )
        assertEquals(0.0, readings[Muscle.Quads]!!.volumeKg, 0.001)
    }

    @Test
    fun `no training at all is an empty map, not a crash`() {
        assertTrue(MuscleMap.analyse(emptyList(), now = now).isEmpty())
    }

    @Test
    fun `readings come back heaviest first`() {
        val list = MuscleMap.analyse(
            listOf(
                set(primary = Muscle.Back, loadKg = 100.0, reps = 5),
                set(primary = Muscle.Chest, loadKg = 100.0, reps = 10),
            ),
            now = now,
        )
        assertEquals(Muscle.Chest, list.first().muscle)
    }
}
