package app.tide.core.notify

import java.time.Duration
import java.time.LocalDate

/**
 * A comparison, not a verdict: tonight against your own recent nights.
 *
 * Real numbers from Health Connect, phrased into a sentence. This is not a
 * generated insight: there is no model behind it, on-device or otherwise,
 * and the wording says only what the arithmetic actually shows. Never
 * "good" or "bad", never a streak.
 *
 * Pure, like [SleepGuardPolicy] and [NotifyPolicy]: no Android, no clock of
 * its own, so both the average and the wording are provable without a
 * device.
 */
object SleepInsightPolicy {

    /**
     * The mean of whichever nights actually have a reading.
     *
     * A night Health Connect never synced is excluded, not counted as zero:
     * a gap in tracking is not a night of no sleep, and averaging it in
     * would understate a real average with a number that looks precise.
     * Null with fewer than two nights of history, since one point is not
     * yet a pattern to compare against.
     */
    fun averageOf(nightlySleep: Map<LocalDate, Duration>): Duration? {
        if (nightlySleep.size < 2) return null
        return Duration.ofMinutes(nightlySleep.values.sumOf { it.toMinutes() } / nightlySleep.size)
    }

    /** What to say about tonight against that average, or null with nothing to say. */
    fun body(tonight: Duration?, average: Duration?): String? {
        if (tonight == null || average == null || average.toMinutes() <= 0) return null
        val diffPercent = (tonight.toMinutes() - average.toMinutes()) * 100.0 / average.toMinutes()
        val tonightLabel = format(tonight)
        val averageLabel = format(average)
        return when {
            diffPercent <= -20.0 ->
                "Slept $tonightLabel, well under your last 7 nights ($averageLabel). Worth an earlier night."
            diffPercent < -5.0 -> "Slept $tonightLabel, a bit under your last 7 nights ($averageLabel)."
            diffPercent < 5.0 -> "Slept $tonightLabel, in line with your last 7 nights."
            else -> "Slept $tonightLabel, more than your last 7 nights ($averageLabel)."
        }
    }

    fun format(d: Duration): String {
        val hours = d.toHours()
        val minutes = d.toMinutes() % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
