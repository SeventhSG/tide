package app.tide.core.data.analysis

import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetWithMuscles
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * What each muscle has had done to it lately.
 *
 * Three questions, kept separate because they answer different things and
 * mixing them into one score would hide all three:
 *
 *  - **Balance.** Volume per muscle over a window. Whether you are training
 *    the whole body or the half of it you enjoy.
 *  - **Fatigue.** How much recent work is still sitting on a muscle, decaying
 *    smoothly with time rather than dropping out of a hard seven day window.
 *  - **Strength.** Days since it was trained, and the best estimated 1RM of
 *    the lifts that train it.
 *
 * **Fatigue here is an index, not a measurement.** It is unitless, it is only
 * meaningful compared against your own other muscles and your own recent
 * weeks, and it is not a percentage of anything. No app can measure your
 * recovery from a set count, and printing "73% recovered" would be exactly the
 * invented precision this project refuses. The UI must present it as a
 * relative reading, never as a number with a unit.
 */
object MuscleMap {

    /**
     * What a secondary muscle is credited with, against the primary's one.
     *
     * A convention, and stated as one rather than dressed up as physiology.
     * A row trains the lats primarily and the biceps genuinely but less, and
     * counting the biceps at zero makes pulling days look like they never
     * touch them, which is worse than counting them at a declared half.
     */
    const val SECONDARY_SHARE = 0.5

    /**
     * Fatigue halves every two days.
     *
     * Chosen to sit inside the range where most people can train a muscle
     * again, and applied smoothly, so a session six days ago fades rather
     * than vanishing at the stroke of a window boundary.
     */
    const val DEFAULT_HALF_LIFE_HOURS = 48.0

    /**
     * @param library every exercise, so a muscle trained before the window
     *   still resolves for "days since trained". Without it, a muscle last
     *   worked a month ago reads as never trained at all.
     */
    fun analyse(
        sets: List<SetWithMuscles>,
        library: List<ExerciseEntity> = emptyList(),
        best1rmByExercise: Map<String, Double> = emptyMap(),
        lastTrainedByExercise: Map<String, Long> = emptyMap(),
        now: Long,
        halfLifeHours: Double = DEFAULT_HALF_LIFE_HOURS,
    ): List<MuscleReading> {
        val volume = mutableMapOf<Muscle, Double>()
        val setCount = mutableMapOf<Muscle, Double>()
        val fatigue = mutableMapOf<Muscle, Double>()
        val lastAt = mutableMapOf<Muscle, Long>()
        val best = mutableMapOf<Muscle, Double>()

        for (set in sets) {
            val work = workOf(set)
            val intensity = intensityWeight(set, best1rmByExercise[set.exerciseId])
            val decay = decay(now - set.completedAt, halfLifeHours)

            for ((muscle, share) in musclesOf(set)) {
                volume[muscle] = (volume[muscle] ?: 0.0) + work * share
                setCount[muscle] = (setCount[muscle] ?: 0.0) + share
                fatigue[muscle] = (fatigue[muscle] ?: 0.0) + work * share * intensity * decay

                val at = set.completedAt
                if (at > (lastAt[muscle] ?: Long.MIN_VALUE)) lastAt[muscle] = at

                best1rmByExercise[set.exerciseId]?.let { e1rm ->
                    // Only the primary lift speaks for a muscle's strength. A
                    // row's 1RM is not a number about your biceps.
                    if (share == 1.0 && e1rm > (best[muscle] ?: 0.0)) best[muscle] = e1rm
                }
            }
        }

        // A muscle trained before the window still has a "last trained", and
        // leaving it out would read as never trained.
        val lastOutside = lastTrainedFromHistory(lastTrainedByExercise, library)
        for ((muscle, at) in lastOutside) {
            if (at > (lastAt[muscle] ?: Long.MIN_VALUE)) lastAt[muscle] = at
        }

        val touched = volume.keys + fatigue.keys + lastAt.keys
        return touched.map { muscle ->
            MuscleReading(
                muscle = muscle,
                volumeKg = volume[muscle] ?: 0.0,
                sets = (setCount[muscle] ?: 0.0),
                fatigueIndex = fatigue[muscle] ?: 0.0,
                daysSinceTrained = lastAt[muscle]?.let { daysBetween(it, now) },
                bestEstimated1rmKg = best[muscle],
            )
        }.sortedByDescending { it.volumeKg }
    }

    /**
     * The work a set represents.
     *
     * Load times reps where there is a load. A bodyweight set has no external
     * load, and counting it as zero would make chin-up days invisible, so reps
     * stand in at a declared weight of one. That is a convention too, and the
     * only honest thing to say about it is that it is one.
     */
    private fun workOf(set: SetWithMuscles): Double {
        val reps = set.reps
        val load = set.loadKg
        return when {
            load != null && load > 0 && reps != null -> load * reps
            reps != null -> reps.toDouble()
            // A timed hold, counted in seconds so a plank is not nothing.
            set.durationSec != null -> set.durationSec.toDouble()
            else -> 0.0
        }
    }

    /**
     * How hard the set was, relative to what that lift has ever done.
     *
     * A set at the top of your range costs more than the same volume taken
     * light. Ranges from 1.0 up to 2.0 at a true single.
     *
     * With no reference, the weight is 1.0 and not a guessed middle: an
     * unknown intensity adds nothing rather than inventing something.
     */
    private fun intensityWeight(set: SetWithMuscles, best1rm: Double?): Double {
        val load = set.loadKg ?: return 1.0
        if (best1rm == null || best1rm <= 0.0) return 1.0
        return 1.0 + (load / best1rm).coerceIn(0.0, 1.0)
    }

    /** Smooth exponential decay. No cliff at the edge of a window. */
    private fun decay(elapsedMillis: Long, halfLifeHours: Double): Double {
        if (elapsedMillis <= 0) return 1.0
        val hours = elapsedMillis / 3_600_000.0
        return exp(-ln(2.0) * hours / halfLifeHours)
    }

    private fun musclesOf(set: SetWithMuscles): List<Pair<Muscle, Double>> =
        split(set.primaryMuscle, set.secondaryMuscles)

    private fun split(primary: Muscle, secondaries: String): List<Pair<Muscle, Double>> {
        val result = mutableListOf(primary to 1.0)
        secondaries.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { name ->
                // An unreadable muscle name is skipped rather than throwing.
                // A typo in seeded data should cost one bar on a chart, not
                // the whole screen.
                runCatching { Muscle.valueOf(name) }.getOrNull()?.let {
                    if (it != primary) result += it to SECONDARY_SHARE
                }
            }
        return result
    }

    private fun lastTrainedFromHistory(
        lastTrainedByExercise: Map<String, Long>,
        library: List<ExerciseEntity>,
    ): Map<Muscle, Long> {
        if (lastTrainedByExercise.isEmpty() || library.isEmpty()) return emptyMap()
        val byId = library.associateBy { it.id }
        val result = mutableMapOf<Muscle, Long>()
        for ((exerciseId, at) in lastTrainedByExercise) {
            val exercise = byId[exerciseId] ?: continue
            for ((muscle, _) in split(exercise.primaryMuscle, exercise.secondaryMuscles)) {
                if (at > (result[muscle] ?: Long.MIN_VALUE)) result[muscle] = at
            }
        }
        return result
    }

    private fun daysBetween(from: Long, to: Long): Int =
        ((to - from).coerceAtLeast(0) / 86_400_000L).toInt()
}

/**
 * One muscle's reading.
 *
 * [fatigueIndex] is unitless and comparable only against the other muscles in
 * the same result. It is not a percentage and must never be shown as one.
 */
data class MuscleReading(
    val muscle: Muscle,
    val volumeKg: Double,
    val sets: Double,
    val fatigueIndex: Double,
    val daysSinceTrained: Int?,
    val bestEstimated1rmKg: Double?,
) {
    /** Whole kilos. The precision past that is not real. */
    val volumeDisplayKg: Int get() = volumeKg.roundToInt()
}
