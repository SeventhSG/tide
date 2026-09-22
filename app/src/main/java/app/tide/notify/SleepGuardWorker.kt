package app.tide.notify

import android.content.Context
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tide.core.data.Tide
import app.tide.core.notify.SleepGuardPolicy
import app.tide.core.notify.Tier
import app.tide.core.notify.TideNotification
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Bedtime, enforced in the direction quiet hours does not cover.
 *
 * Four instances of this worker run, each anchored to a fixed offset from
 * [app.tide.notify.NotifyPreferences.quietStart]: two wind-down reminders
 * before it, two awake-checks after it. Each instance is told which it is
 * through [Data] rather than the code branching on the clock itself, which is
 * what lets [SleepGuardPolicy] stay pure and this worker stay a thin edge.
 *
 * **The alert is the one thing in this app allowed to break quiet hours on
 * purpose.** It only exists to protect the window quiet hours describes, and
 * it only fires when the phone is actually being used, which
 * [PowerManager.isInteractive] is the closest signal Android gives without a
 * foreground service watching the screen continuously.
 */
class SleepGuardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = NotifyPreferences(applicationContext)
        if (!prefs.sleepGuardEnabled) return Result.success()

        val notifier = Tide.notifier(applicationContext)
        if (!notifier.canPost()) return Result.success()

        val minutes = inputData.getLong(KEY_MINUTES, 0L)

        when (inputData.getString(KEY_KIND)) {
            KIND_WIND_DOWN -> notifier.post(
                TideNotification(
                    key = "sleep.windDown.$minutes",
                    title = "Getting late",
                    body = SleepGuardPolicy.windDownBody(minutes),
                    tier = Tier.Default,
                    createdAt = Instant.now(),
                ),
            )

            KIND_ALERT -> {
                val interactive = (applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.isInteractive == true
                if (SleepGuardPolicy.alertNeeded(interactive)) {
                    notifier.post(
                        TideNotification(
                            key = "sleep.alert.$minutes",
                            title = "Time to sleep",
                            body = SleepGuardPolicy.alertBody(minutes),
                            // Deliberately Urgent. See the class note: this is
                            // the one exception, and it is a narrow one.
                            tier = Tier.Urgent,
                            createdAt = Instant.now(),
                        ),
                    )
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val KEY_KIND = "kind"
        private const val KEY_MINUTES = "minutes"
        private const val KIND_WIND_DOWN = "wind_down"
        private const val KIND_ALERT = "alert"
        private const val NAME_PREFIX = "tide.sleepguard."

        /**
         * (Re)schedules all four checkpoints against [quietStart].
         *
         * Called whenever sleep guard is turned on or [quietStart] changes,
         * same as [DigestWorker.schedule]: each checkpoint is its own unique
         * periodic job, and `UPDATE` means a changed bedtime replaces the old
         * checkpoints rather than piling up beside them.
         */
        fun schedule(context: Context, quietStart: LocalTime, zone: ZoneId = ZoneId.systemDefault()) {
            SleepGuardPolicy.WIND_DOWN_MINUTES_BEFORE.forEach { minutes ->
                enqueue(
                    context,
                    name = "$NAME_PREFIX$KIND_WIND_DOWN.$minutes",
                    at = quietStart.minusMinutes(minutes),
                    zone = zone,
                    kind = KIND_WIND_DOWN,
                    minutes = minutes,
                )
            }
            SleepGuardPolicy.ALERT_MINUTES_AFTER.forEach { minutes ->
                enqueue(
                    context,
                    name = "$NAME_PREFIX$KIND_ALERT.$minutes",
                    at = quietStart.plusMinutes(minutes),
                    zone = zone,
                    kind = KIND_ALERT,
                    minutes = minutes,
                )
            }
        }

        fun cancel(context: Context) {
            val manager = WorkManager.getInstance(context)
            SleepGuardPolicy.WIND_DOWN_MINUTES_BEFORE.forEach {
                manager.cancelUniqueWork("$NAME_PREFIX$KIND_WIND_DOWN.$it")
            }
            SleepGuardPolicy.ALERT_MINUTES_AFTER.forEach {
                manager.cancelUniqueWork("$NAME_PREFIX$KIND_ALERT.$it")
            }
        }

        private fun enqueue(
            context: Context,
            name: String,
            at: LocalTime,
            zone: ZoneId,
            kind: String,
            minutes: Long,
        ) {
            // LocalTime's own plusMinutes/minusMinutes already wrap across
            // midnight correctly (5 minutes before 00:20 is 23:55), so [at]
            // needs no adjustment here. DigestWorker.delayUntil then picks
            // today or tomorrow for it, exactly as it does for the digest.
            val data = Data.Builder()
                .putString(KEY_KIND, kind)
                .putLong(KEY_MINUTES, minutes)
                .build()
            val request = PeriodicWorkRequestBuilder<SleepGuardWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(DigestWorker.delayUntil(at, zone).toMinutes(), TimeUnit.MINUTES)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
