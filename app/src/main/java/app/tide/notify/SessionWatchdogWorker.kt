package app.tide.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tide.core.data.Tide
import app.tide.core.notify.Tier
import app.tide.core.notify.TideNotification
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * A session left open is not tracked correctly.
 *
 * Every logged set is already safe on disk the moment it is logged, so a
 * session surviving the app being backgrounded, or the process being killed
 * and the phone reopened an hour later, was never the risk. The actual risk
 * is a session left open by mistake: you finish training, close the app
 * without pressing Finish, and the row sits open indefinitely, which is
 * exactly the state [app.tide.core.data.training.TrainingRepository.startSession]
 * treats as "still training" and resumes into rather than starting fresh.
 *
 * One check, a fixed time after a session opens: if it is still the same
 * session and still open, say so, once, through the same funnel as
 * everything else. If it already finished, or a different session has since
 * replaced it, this says nothing.
 */
class SessionWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.success()
        val repository = Tide.training(applicationContext)
        val active = repository.activeSession()
        if (active == null || active.id != sessionId) return Result.success()

        val notifier = Tide.notifier(applicationContext)
        if (!notifier.canPost()) return Result.success()

        val minutes = Duration.between(Instant.ofEpochMilli(active.startedAt), Instant.now()).toMinutes()
        notifier.post(
            TideNotification(
                key = "session.watchdog.$sessionId",
                title = "Still training?",
                body = "A session has been open for about $minutes minutes.",
                tier = Tier.Default,
                createdAt = Instant.now(),
            ),
        )
        return Result.success()
    }

    companion object {
        private const val KEY_SESSION_ID = "sessionId"

        /** Long enough that a real, long session never trips it by accident. */
        private const val CHECK_AFTER_MINUTES = 120L

        /**
         * Anchored to when the session actually started, not to when this is
         * called, so reopening the app mid-session reschedules the same
         * check rather than pushing it later each time.
         */
        fun schedule(context: Context, sessionId: String, startedAtMillis: Long) {
            val elapsed = (System.currentTimeMillis() - startedAtMillis) / 60_000
            val remaining = (CHECK_AFTER_MINUTES - elapsed).coerceAtLeast(1)

            val data = Data.Builder().putString(KEY_SESSION_ID, sessionId).build()
            val request = OneTimeWorkRequestBuilder<SessionWatchdogWorker>()
                .setInitialDelay(remaining, TimeUnit.MINUTES)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(sessionId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, sessionId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(sessionId))
        }

        private fun uniqueName(sessionId: String) = "tide.session-watchdog.$sessionId"
    }
}
