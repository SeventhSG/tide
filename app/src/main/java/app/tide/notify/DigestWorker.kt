package app.tide.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tide.core.data.Tide
import app.tide.core.notify.NotifyPolicy
import app.tide.core.notify.Tier
import app.tide.core.notify.TideNotification
import app.tide.core.schedule.OccurrenceState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The daily summary, and the only thing in this app that posts without being
 * asked in the moment.
 *
 * It says one thing a day, or nothing. **Nothing is the common case and that is
 * correct**: with no outstanding commitment there is no message, because an app
 * that checks in to say all is well is an app you turn off. There is no streak
 * in here, no "you have not opened Tide", and there never will be.
 *
 * The decision is [NotifyPolicy]'s, not this worker's. This assembles what is
 * outstanding and hands it over.
 */
class DigestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = NotifyPreferences(applicationContext)
        if (!prefs.digestEnabled) return Result.success()

        val notifier = Tide.notifier(applicationContext)
        if (!notifier.canPost()) return Result.success()

        val schedule = Tide.schedule(applicationContext)
        val today = LocalDate.now(ZoneId.systemDefault())

        // Today and tomorrow: what is due now, and what is due next, so the one
        // message a day can mention a deadline before the morning it lands.
        val outstanding = runCatching { schedule.outstanding(today, today.plusDays(1)) }
            .getOrDefault(emptyList())
            .filter { it.state != OccurrenceState.Done }

        // An occurrence knows its rule, not its name, so the titles come from
        // the rules themselves rather than being invented here.
        val titles = runCatching { schedule.rules().associate { it.id to it.title } }
            .getOrDefault(emptyMap())

        val items = outstanding.map { resolved ->
            TideNotification(
                key = "schedule.${resolved.occurrence.ruleId}.${resolved.occurrence.due}",
                title = titles[resolved.occurrence.ruleId] ?: "Scheduled",
                body = "Due ${resolved.occurrence.due}.",
                tier = Tier.Quiet,
                createdAt = Instant.now(),
            )
        }

        // No items, no notification. The app does not check in.
        val digest = NotifyPolicy.digest(items, Instant.now()) ?: return Result.success()
        notifier.post(digest, inDigest = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "tide.digest"

        /**
         * One periodic run a day, first fired at the chosen time.
         *
         * WorkManager is not a precise alarm and is not meant to be: the digest
         * is a summary read when the phone is next picked up, so a few minutes
         * either way costs nothing. Anything needing a real deadline would use
         * an alarm, and nothing in this app does yet.
         */
        fun schedule(context: Context, at: LocalTime, zone: ZoneId = ZoneId.systemDefault()) {
            val request = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntil(at, zone).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                // The time can change, and the new time replaces the old one
                // rather than queueing a second daily summary next to it.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        /** How long until the next [at], today if it is still ahead, tomorrow otherwise. */
        fun delayUntil(at: LocalTime, zone: ZoneId, now: Instant = Instant.now()): Duration {
            val local = now.atZone(zone)
            val todays = local.with(at)
            val next = if (todays.toInstant().isAfter(now)) todays else todays.plusDays(1)
            return Duration.between(now, next.toInstant())
        }
    }
}
