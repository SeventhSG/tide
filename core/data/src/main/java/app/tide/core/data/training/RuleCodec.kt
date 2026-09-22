package app.tide.core.data.training

/**
 * Progression rules, as a string the database can hold.
 *
 * Deliberately not JSON and deliberately not kotlinx.serialization. The format
 * is one line you can read in a SQLite browser and fix by hand:
 *
 *     Linear:2.5:3:0.9
 *     DoubleProgression:6:9:2.5:3:0.9
 *
 * This database holds the only copy of the data, so being able to repair it
 * without the app is worth more than the convenience of a serialisation library.
 *
 * Unknown or corrupt values decode to null rather than throwing, and the caller
 * falls back to the routine default. A bad rule string should cost you a
 * progression decision, not the app.
 */
object RuleCodec {

    fun encode(rule: ProgressionRule): String = when (rule) {
        is ProgressionRule.Linear ->
            "Linear:${rule.incrementKg}:${rule.stallsBeforeDeload}:${rule.deloadFactor}"

        is ProgressionRule.GreyskullLp ->
            "GreyskullLp:${rule.incrementKg}:${rule.doubleJumpAtReps}:" +
                "${rule.stallsBeforeDeload}:${rule.deloadFactor}"

        is ProgressionRule.DoubleProgression ->
            "DoubleProgression:${rule.minReps}:${rule.maxReps}:${rule.incrementKg}:" +
                "${rule.stallsBeforeDeload}:${rule.deloadFactor}"

        is ProgressionRule.Timed ->
            "Timed:${rule.incrementSec}:${rule.ceilingSec}:${rule.loadIncrementKg}"

        is ProgressionRule.Bodyweight ->
            "Bodyweight:${rule.repCeiling}:${rule.maxSets}"
    }

    fun decode(raw: String?): ProgressionRule? {
        if (raw.isNullOrBlank()) return null
        val p = raw.split(':')
        return runCatching {
            when (p[0]) {
                "Linear" -> ProgressionRule.Linear(p[1].toDouble(), p[2].toInt(), p[3].toDouble())
                "GreyskullLp" -> ProgressionRule.GreyskullLp(
                    p[1].toDouble(), p[2].toInt(), p[3].toInt(), p[4].toDouble(),
                )
                "DoubleProgression" -> ProgressionRule.DoubleProgression(
                    p[1].toInt(), p[2].toInt(), p[3].toDouble(), p[4].toInt(), p[5].toDouble(),
                )
                "Timed" -> ProgressionRule.Timed(p[1].toInt(), p[2].toInt(), p[3].toDouble())
                "Bodyweight" -> ProgressionRule.Bodyweight(p[1].toInt(), p[2].toInt())
                else -> null
            }
        }.getOrNull()
    }
}
