package app.tide.core.notify

/**
 * Helping quiet hours actually start, not just enforcing them once they have.
 *
 * Quiet hours stops the app disturbing you inside a window. This is the
 * opposite direction: as the window approaches, say so, twice, quietly. If it
 * has already started and the phone is still on, say so more plainly. It
 * reuses quiet hours' own start time as bedtime rather than asking for a
 * second clock, because a second bedtime is one more thing to set and forget.
 *
 * Pure, like [NotifyPolicy]: no Android, no clock of its own, so every rule
 * here is proven by moving a number rather than by waiting on a device.
 */
object SleepGuardPolicy {

    /** Minutes before bedtime each wind-down reminder fires. Two, not a stream of them. */
    val WIND_DOWN_MINUTES_BEFORE: List<Long> = listOf(20L, 5L)

    /** Minutes after bedtime an alert re-checks whether the phone is still awake. */
    val ALERT_MINUTES_AFTER: List<Long> = listOf(0L, 30L)

    /** What to say approaching bedtime. Always sent; there is no condition on this one. */
    fun windDownBody(minutesUntil: Long): String = when (minutesUntil) {
        1L -> "Quiet hours start in a minute."
        else -> "Quiet hours start in $minutesUntil minutes."
    }

    /**
     * Whether bedtime having arrived is worth mentioning.
     *
     * Only when the phone is actually being used. A phone already asleep needs
     * no reminder to be asleep, and checking this is the entire point: without
     * it, this would just be a second daily digest with worse timing.
     */
    fun alertNeeded(isDeviceInteractive: Boolean): Boolean = isDeviceInteractive

    /** What to say once bedtime has arrived and the phone is still on. */
    fun alertBody(minutesSinceBedtime: Long): String = when {
        minutesSinceBedtime <= 0L -> "Quiet hours started, and the phone is still on."
        else -> "Quiet hours started $minutesSinceBedtime minutes ago, and the phone is still on."
    }
}
