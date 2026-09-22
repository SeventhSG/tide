package app.tide.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.ZoneId

/**
 * The only place in this app that posts a notification.
 *
 * Everything else asks this module and gets a decision. The pre-ship gate
 * enforces it: `NotificationManager` anywhere outside `:core:notify` fails the
 * build. Four modules each growing their own notification funnel is how an app
 * ends up buzzing three times about one thing, and it is why this module was
 * planned before anything had something to say.
 *
 * The policy lives in [NotifyPolicy] and is pure. This file is the thin edge
 * that talks to Android, so the interesting decisions stay testable without a
 * device, which matters here because there is no usable emulator.
 */
class Notifier(
    private val context: Context,
    private val ledger: NotificationLedger,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Instant = Instant::now,
) {

    /**
     * One channel per tier, because a channel is the only control Android
     * actually gives the user. Split any finer and the settings screen becomes
     * a list nobody reads; split any coarser and turning off the noisy thing
     * also turns off the one that mattered.
     */
    enum class Channel(val id: String, val label: String, val description: String) {
        Quiet(
            "tide.quiet",
            "Daily summary",
            "One quiet summary a day. Never makes a sound.",
        ),
        Default(
            "tide.default",
            "Reminders",
            "Things you asked to be reminded about, at a reasonable hour.",
        ),
        Urgent(
            "tide.urgent",
            "Deadlines",
            "Only things with a real deadline. These can arrive during quiet hours.",
        ),
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        Channel.entries.forEach { channel ->
            val importance = when (channel) {
                Channel.Quiet -> NotificationManager.IMPORTANCE_LOW
                Channel.Default -> NotificationManager.IMPORTANCE_DEFAULT
                Channel.Urgent -> NotificationManager.IMPORTANCE_HIGH
            }
            manager.createNotificationChannel(
                NotificationChannel(channel.id, channel.label, importance).apply {
                    description = channel.description
                    // The digest must never buzz. It is a summary you read when
                    // you pick the phone up, not an interruption.
                    if (channel == Channel.Quiet) {
                        enableVibration(false)
                        setSound(null, null)
                    }
                },
            )
        }
    }

    /** Whether the user has granted the runtime permission. */
    fun canPost(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun channelFor(tier: Tier): Channel = when (tier) {
        Tier.Quiet -> Channel.Quiet
        Tier.Default -> Channel.Default
        Tier.Urgent -> Channel.Urgent
    }

    /**
     * Posts it and writes it down.
     *
     * The ledger entry is written whether or not Android accepted it, because
     * the record is of what the app decided to say, and a notification silently
     * swallowed by a revoked permission is exactly the case you want visible
     * when working out why nothing arrived.
     */
    suspend fun post(notification: TideNotification, inDigest: Boolean = false): Boolean {
        val posted = runCatching { postToSystem(notification) }.getOrDefault(false)
        ledger.record(
            LedgerEntry(
                key = notification.key,
                title = notification.title,
                tier = notification.tier,
                postedAt = now(),
                inDigest = inDigest,
            ),
        )
        return posted
    }

    /**
     * Runs a notification through the policy and acts on the answer.
     *
     * Returns the decision so the caller can schedule the held and digested
     * ones. This module does not own a scheduler: WorkManager belongs to the
     * app, and a library that quietly schedules background work is a library
     * you cannot reason about from the outside.
     */
    suspend fun offer(
        notification: TideNotification,
        settings: NotifySettings,
    ): Delivery {
        val decision = NotifyPolicy.decide(
            notification = notification,
            settings = settings,
            now = now(),
            zone = zone,
            lastPosted = ledger.lastPosted(notification.key),
        )
        if (decision is Delivery.Now) post(decision.notification)
        return decision
    }

    private fun postToSystem(notification: TideNotification): Boolean {
        if (!canPost()) return false
        val manager = NotificationManagerCompat.from(context)
        val builder = androidx.core.app.NotificationCompat.Builder(
            context,
            channelFor(notification.tier).id,
        )
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle().bigText(notification.body),
            )
            .setAutoCancel(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)

        // A notification whose moment passes should leave on its own rather
        // than sitting in the shade as a reproach.
        notification.expiresAt?.let { builder.setTimeoutAfter(it.toEpochMilli() - now().toEpochMilli()) }

        return runCatching {
            manager.notify(notification.key.hashCode(), builder.build())
            true
        }.getOrDefault(false)
    }
}
