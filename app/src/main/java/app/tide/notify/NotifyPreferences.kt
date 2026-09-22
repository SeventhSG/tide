package app.tide.notify

import android.content.Context
import android.content.SharedPreferences
import app.tide.core.notify.NotifySettings
import app.tide.core.notify.QuietHours
import java.time.LocalTime

/**
 * Where the notification settings live.
 *
 * `SharedPreferences` rather than a Room table, deliberately. These are five
 * values that belong to the device, not to the training history: putting them
 * in the database would mean a migration to add a digest time, and would put
 * device settings inside the one file that is the only copy of a year of
 * training.
 *
 * The defaults are the stance written down: a digest at 08:00, quiet hours
 * from 22:00 to 07:00, and **off until the person turns it on**. Nothing here
 * opts anyone into being interrupted.
 */
class NotifyPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tide.notify", Context.MODE_PRIVATE)

    var digestEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIGEST_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DIGEST_ENABLED, value).apply()

    var digestAt: LocalTime
        get() = LocalTime.of(prefs.getInt(KEY_DIGEST_HOUR, 8), prefs.getInt(KEY_DIGEST_MINUTE, 0))
        set(value) = prefs.edit()
            .putInt(KEY_DIGEST_HOUR, value.hour)
            .putInt(KEY_DIGEST_MINUTE, value.minute)
            .apply()

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QUIET_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    var quietStart: LocalTime
        get() = LocalTime.of(prefs.getInt(KEY_QUIET_START, 22), 0)
        set(value) = prefs.edit().putInt(KEY_QUIET_START, value.hour).apply()

    var quietEnd: LocalTime
        get() = LocalTime.of(prefs.getInt(KEY_QUIET_END, 7), 0)
        set(value) = prefs.edit().putInt(KEY_QUIET_END, value.hour).apply()

    /**
     * Off by default, like every notification in this app. Reminders as
     * [quietStart] approaches, and an alert if the phone is still on past it.
     */
    var sleepGuardEnabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEP_GUARD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEP_GUARD_ENABLED, value).apply()

    /** The shape `:core:notify` decides with. */
    fun settings(): NotifySettings = NotifySettings(
        quietHours = QuietHours(
            start = quietStart,
            end = quietEnd,
            enabled = quietHoursEnabled,
        ),
        digestAt = digestAt,
    )

    private companion object {
        const val KEY_DIGEST_ENABLED = "digest.enabled"
        const val KEY_DIGEST_HOUR = "digest.hour"
        const val KEY_DIGEST_MINUTE = "digest.minute"
        const val KEY_QUIET_ENABLED = "quiet.enabled"
        const val KEY_QUIET_START = "quiet.start"
        const val KEY_QUIET_END = "quiet.end"
        const val KEY_SLEEP_GUARD_ENABLED = "sleepguard.enabled"
    }
}
