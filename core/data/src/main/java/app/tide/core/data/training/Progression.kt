package app.tide.core.data.training

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What to lift today, decided from what you lifted last time.
 *
 * This is the idea worth having in a training log. Most apps prefill the last
 * session's numbers, which makes them a notebook. Deciding the next
 * prescription from a stated rule makes the app worth opening, and it is why
 * the rest of the training module is built around this file rather than the
 * other way round.
 *
 * Studied from openGym (AGPL-3.0, https://gitlab.com/DuarteSantos8/opengym) and
 * reimplemented. No code was taken.
 *
 * Two invariants hold across every rule:
 *
 *  1. **Missed reps never advance the load.** If you did not earn it, you do not
 *     get it. This is the whole point of a progression scheme.
 *  2. **Warm-ups never count.** [SessionOutcome] carries working sets only; it
 *     is the caller's job to filter, and the schema's warm-up flag is what makes
 *     that possible.
 */

/** A target for one exercise in one session. */
data class Prescription(
    /** Null means bodyweight with no added load. */
    val loadKg: Double? = null,
    val sets: Int,
    val reps: Int,
    /** Upper bound for [ProgressionRule.DoubleProgression]. */
    val repCeiling: Int? = null,
    /** For timed holds and carries. */
    val durationSec: Int? = null,
)

/** One working set, as actually performed. */
data class PerformedSet(
    val reps: Int,
    val loadKg: Double? = null,
    val durationSec: Int? = null,
)

/** Working sets only. Warm-ups are filtered out before this is built. */
data class SessionOutcome(val performed: List<PerformedSet>) {
    val allSetsMet: (Int) -> Boolean get() = { target -> performed.all { it.reps >= target } }
    val topSetReps: Int get() = performed.maxOfOrNull { it.reps } ?: 0
    val lastSetReps: Int get() = performed.lastOrNull()?.reps ?: 0
    val minReps: Int get() = performed.minOfOrNull { it.reps } ?: 0
}

sealed interface ProgressionRule {

    /** Add a fixed amount every time the target is met. The simplest thing that works. */
    data class Linear(
        val incrementKg: Double = 2.5,
        val stallsBeforeDeload: Int = 3,
        val deloadFactor: Double = 0.90,
    ) : ProgressionRule

    /**
     * Greyskull LP. The last set is taken to failure, and beating the target by
     * enough earns a double jump rather than a single one.
     */
    data class GreyskullLp(
        val incrementKg: Double = 2.5,
        val doubleJumpAtReps: Int = 10,
        val stallsBeforeDeload: Int = 1,
        val deloadFactor: Double = 0.90,
    ) : ProgressionRule

    /**
     * Work up a visible rep range at a fixed load, then add weight and drop back
     * to the bottom of the range. The rep range is shown to the user, because a
     * range you cannot see is a rule you cannot follow.
     */
    data class DoubleProgression(
        val minReps: Int = 6,
        val maxReps: Int = 9,
        val incrementKg: Double = 2.5,
        val stallsBeforeDeload: Int = 3,
        val deloadFactor: Double = 0.90,
    ) : ProgressionRule

    /** Planks, hangs, carries. Time goes up, not load. */
    data class Timed(
        val incrementSec: Int = 5,
        val ceilingSec: Int = 90,
        val loadIncrementKg: Double = 2.5,
    ) : ProgressionRule

    /**
     * Bodyweight movements. Reps climb to a ceiling, then a set is added and reps
     * reset, because most people cannot load a pull-up conveniently.
     */
    data class Bodyweight(
        val repCeiling: Int = 12,
        val maxSets: Int = 5,
    ) : ProgressionRule
}

data class ProgressionResult(
    val next: Prescription,
    val stalls: Int,
    val deloaded: Boolean,
    /** Plain-language reason, shown in the app. No jargon, no invented precision. */
    val reason: String,
)

object ProgressionEngine {

    /**
     * @param stalls how many consecutive sessions have already failed to advance.
     *   The caller persists this; the engine is pure.
     */
    fun next(
        rule: ProgressionRule,
        current: Prescription,
        outcome: SessionOutcome,
        stalls: Int = 0,
    ): ProgressionResult = when (rule) {
        is ProgressionRule.Linear -> linear(rule, current, outcome, stalls)
        is ProgressionRule.GreyskullLp -> greyskull(rule, current, outcome, stalls)
        is ProgressionRule.DoubleProgression -> doubleProgression(rule, current, outcome, stalls)
        is ProgressionRule.Timed -> timed(rule, current, outcome)
        is ProgressionRule.Bodyweight -> bodyweight(rule, current, outcome)
    }

    private fun linear(
        rule: ProgressionRule.Linear,
        current: Prescription,
        outcome: SessionOutcome,
        stalls: Int,
    ): ProgressionResult {
        val met = outcome.performed.isNotEmpty() && outcome.allSetsMet(current.reps)
        if (met) {
            val load = addLoad(current.loadKg, rule.incrementKg)
            return ProgressionResult(
                current.copy(loadKg = load),
                stalls = 0,
                deloaded = false,
                reason = "Every set hit ${current.reps}. Up ${fmt(rule.incrementKg)} kg.",
            )
        }
        val newStalls = stalls + 1
        if (newStalls >= rule.stallsBeforeDeload) {
            val load = deload(current.loadKg, rule.deloadFactor)
            return ProgressionResult(
                current.copy(loadKg = load),
                stalls = 0,
                deloaded = true,
                reason = "Stalled $newStalls sessions. Back to ${fmt(load)} kg to build up again.",
            )
        }
        return ProgressionResult(
            current,
            stalls = newStalls,
            deloaded = false,
            reason = "Short of ${current.reps}. Same weight next time.",
        )
    }

    private fun greyskull(
        rule: ProgressionRule.GreyskullLp,
        current: Prescription,
        outcome: SessionOutcome,
        stalls: Int,
    ): ProgressionResult {
        // The last set is the AMRAP set, so it decides.
        val amrap = outcome.lastSetReps
        return when {
            amrap >= rule.doubleJumpAtReps -> ProgressionResult(
                current.copy(loadKg = addLoad(current.loadKg, rule.incrementKg * 2)),
                stalls = 0,
                deloaded = false,
                reason = "$amrap on the last set. Double jump, up ${fmt(rule.incrementKg * 2)} kg.",
            )

            amrap >= current.reps -> ProgressionResult(
                current.copy(loadKg = addLoad(current.loadKg, rule.incrementKg)),
                stalls = 0,
                deloaded = false,
                reason = "$amrap on the last set. Up ${fmt(rule.incrementKg)} kg.",
            )

            else -> {
                val newStalls = stalls + 1
                if (newStalls >= rule.stallsBeforeDeload) {
                    val load = deload(current.loadKg, rule.deloadFactor)
                    ProgressionResult(
                        current.copy(loadKg = load),
                        stalls = 0,
                        deloaded = true,
                        reason = "Missed the target. Back to ${fmt(load)} kg.",
                    )
                } else {
                    ProgressionResult(
                        current,
                        stalls = newStalls,
                        deloaded = false,
                        reason = "Missed the target. Same weight next time.",
                    )
                }
            }
        }
    }

    private fun doubleProgression(
        rule: ProgressionRule.DoubleProgression,
        current: Prescription,
        outcome: SessionOutcome,
        stalls: Int,
    ): ProgressionResult {
        if (outcome.performed.isEmpty()) {
            return ProgressionResult(current, stalls, false, "Nothing logged. Same target.")
        }
        val hitCeiling = outcome.performed.all { it.reps >= rule.maxReps }
        if (hitCeiling) {
            return ProgressionResult(
                current.copy(
                    loadKg = addLoad(current.loadKg, rule.incrementKg),
                    reps = rule.minReps,
                    repCeiling = rule.maxReps,
                ),
                stalls = 0,
                deloaded = false,
                reason = "Top of the range on every set. Up ${fmt(rule.incrementKg)} kg, " +
                    "back to ${rule.minReps}.",
            )
        }
        val floor = outcome.minReps
        if (floor >= rule.minReps) {
            // Inside the range: same load, aim one rep higher on the weakest set.
            val target = (floor + 1).coerceAtMost(rule.maxReps)
            return ProgressionResult(
                current.copy(reps = target, repCeiling = rule.maxReps),
                stalls = 0,
                deloaded = false,
                reason = "In range at $floor. Same weight, go for $target.",
            )
        }
        val newStalls = stalls + 1
        if (newStalls >= rule.stallsBeforeDeload) {
            val load = deload(current.loadKg, rule.deloadFactor)
            return ProgressionResult(
                current.copy(loadKg = load, reps = rule.minReps, repCeiling = rule.maxReps),
                stalls = 0,
                deloaded = true,
                reason = "Under ${rule.minReps} for $newStalls sessions. Back to ${fmt(load)} kg.",
            )
        }
        return ProgressionResult(
            current.copy(reps = rule.minReps, repCeiling = rule.maxReps),
            stalls = newStalls,
            deloaded = false,
            reason = "Under ${rule.minReps}. Same weight next time.",
        )
    }

    private fun timed(
        rule: ProgressionRule.Timed,
        current: Prescription,
        outcome: SessionOutcome,
    ): ProgressionResult {
        val target = current.durationSec ?: rule.incrementSec
        val held = outcome.performed.minOfOrNull { it.durationSec ?: 0 } ?: 0
        if (held < target) {
            return ProgressionResult(
                current,
                stalls = 0,
                deloaded = false,
                reason = "Held ${held}s of ${target}s. Same target next time.",
            )
        }
        val grown = target + rule.incrementSec
        if (grown > rule.ceilingSec) {
            // Past the ceiling, add load and drop the time back rather than
            // holding a plank for five minutes.
            return ProgressionResult(
                current.copy(
                    loadKg = addLoad(current.loadKg ?: 0.0, rule.loadIncrementKg),
                    durationSec = rule.ceilingSec - rule.incrementSec * 2,
                ),
                stalls = 0,
                deloaded = false,
                reason = "Past ${rule.ceilingSec}s. Add ${fmt(rule.loadIncrementKg)} kg " +
                    "and drop the time.",
            )
        }
        return ProgressionResult(
            current.copy(durationSec = grown),
            stalls = 0,
            deloaded = false,
            reason = "Held ${held}s. Go for ${grown}s.",
        )
    }

    private fun bodyweight(
        rule: ProgressionRule.Bodyweight,
        current: Prescription,
        outcome: SessionOutcome,
    ): ProgressionResult {
        if (outcome.performed.isEmpty()) {
            return ProgressionResult(current, 0, false, "Nothing logged. Same target.")
        }
        val allAtCeiling = outcome.performed.all { it.reps >= rule.repCeiling }
        if (allAtCeiling) {
            return if (current.sets < rule.maxSets) {
                ProgressionResult(
                    current.copy(sets = current.sets + 1, reps = max(1, rule.repCeiling - 3)),
                    stalls = 0,
                    deloaded = false,
                    reason = "${rule.repCeiling} on every set. Add a set, back to " +
                        "${max(1, rule.repCeiling - 3)} reps.",
                )
            } else {
                ProgressionResult(
                    current,
                    stalls = 0,
                    deloaded = false,
                    reason = "At ${rule.maxSets} sets of ${rule.repCeiling}. " +
                        "Time to add load or a harder variation.",
                )
            }
        }
        val floor = outcome.minReps
        val target = (floor + 1).coerceAtMost(rule.repCeiling)
        return ProgressionResult(
            current.copy(reps = target),
            stalls = 0,
            deloaded = false,
            reason = "Lowest set was $floor. Go for $target.",
        )
    }

    // --- helpers ----------------------------------------------------------

    /** Loads snap to 0.25 kg. Below that, plates do not exist. */
    private fun addLoad(load: Double?, delta: Double): Double? =
        load?.let { snap(it + delta) }

    private fun deload(load: Double?, factor: Double): Double? =
        load?.let { snap(it * factor) }

    private fun snap(kg: Double): Double = (kg * 4).roundToInt() / 4.0

    private fun fmt(kg: Double?): String {
        if (kg == null) return "0"
        return if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
    }
}
