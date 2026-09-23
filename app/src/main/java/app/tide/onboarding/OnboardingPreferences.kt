package app.tide.onboarding

import android.content.Context

/**
 * Whether the first-run screen has already run.
 *
 * `SharedPreferences`, the same choice as [app.tide.notify.NotifyPreferences]
 * and for the same reason: this belongs to the device, not to the training
 * history, and does not deserve a migration of the one database that matters.
 */
class OnboardingPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("tide.onboarding", Context.MODE_PRIVATE)

    var completed: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPLETED, value).apply()

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
