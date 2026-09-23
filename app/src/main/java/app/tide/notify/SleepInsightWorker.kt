package app.tide.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tide.body.AndroidHealthSource
import app.tide.body.HealthSource
import app.tide.core.data.Tide
import app.tide.core.notify.SleepInsightPolicy
import app.tide.core.notify.Tier
import app.tide.core.notify.TideNotification
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * One sentence about last night, against your own recent nights.
 *
 * Real numbers read from Health Connect through [SleepInsightPolicy], the
 * same policy [app.tide.BodyViewModel] uses, so the screen and the
 * notification never disagree. Not generated: there is no model behind it,
 * see `docs/roadmap.md` on why on-device inference is not wired up. Off by
 * default, like every notification here, and it posts nothing when there is
 * nothing real to compare tonight against.
 */
class SleepInsightWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = NotifyPreferences(applicationContext)
        if (!prefs.sleepInsightEnabled) return Result.success()

        val notifier = Tide.notifier(applicationContext)
        if (!notifier.canPost()) return Result.success()

        val source: HealthSource = AndroidHealthSource(applicationContext)
        if (source.availability() != HealthSource.Availability.Available) return Result.success()
        if (!source.hasPermissions()) return Result.success()

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val startOfToday = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        val tonight = source.read(startOfToday, now).sleep
        val sessions = source.sleepSessions(startOfToday.minus(Duration.ofDays(7)), startOfToday)
        val byNight = sessions
            .groupBy { it.end.atZone(zone).toLocalDate() }
            .mapValues { (_, night) -> night.fold(Duration.ZERO) { total, s -> total.plus(s.duration) } }
        val average = SleepInsightPolicy.averageOf(byNight)

        val body = SleepInsightPolicy.body(tonight, average) ?: return Result.success()
        notifier.post(
            TideNotification(
                key = "sleep.insight.${now.atZone(zone).toLocalDate()}",
                title = "Sleep",
                body = body,
                tier = Tier.Quiet,
                createdAt = now,
            ),
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "tide.sleepinsight"

        /** Once a day, same shape as [DigestWorker.schedule]. */
        fun schedule(context: Context, at: LocalTime = LocalTime.of(9, 0), zone: ZoneId = ZoneId.systemDefault()) {
            val request = PeriodicWorkRequestBuilder<SleepInsightWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(DigestWorker.delayUntil(at, zone).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
