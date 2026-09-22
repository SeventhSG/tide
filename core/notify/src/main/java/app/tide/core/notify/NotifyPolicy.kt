package app.tide.core.notify

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When something may be said, and how loudly.
 *
 * Pure. No Android, no database, no clock of its own: everything it needs is
 * passed in, so every rule below can be tested by moving an argument rather
 * than by waiting.
 */

/**
 * The hours the phone is left alone.
 *
 * [start] after [end] means the window crosses midnight, which is the normal
 * case: 22:00 to 07:00.
 */
data class QuietHours(
    val start: LocalTime = LocalTime.of(22, 0),
    val end: LocalTime = LocalTime.of(7, 0),
    val enabled: Boolean = true,
) {
    fun contains(time: LocalTime): Boolean {
        if (!enabled) return false
        if (start == end) return false
        return if (start < end) {
            time >= start && time < end
        } else {
            // Crosses midnight, so it is either late enough or early enough.
            time >= start || time < end
        }
    }

    /**
     * The next moment the phone may be disturbed again.
     *
     * Outside the window that is now: there is nothing to wait for. Inside it,
     * it is the end time, today if that is still ahead and tomorrow otherwise,
     * which is what makes a window crossing midnight work.
     */
    fun endsAfter(now: ZonedDateTime): Instant {
        if (!contains(now.toLocalTime())) return now.toInstant()
        val endToday = now.with(end)
        return if (endToday.toInstant() > now.toInstant()) {
            endToday.toInstant()
        } else {
            endToday.plusDays(1).toInstant()
        }
    }
}

/**
 * The settings a person actually controls.
 *
 * [digestAt] is the heart of it. Most of what this app has to say is worth
 * knowing once a day, not the moment it becomes true, so the default is one
 * quiet summary rather than a trickle of buzzes.
 */
data class NotifySettings(
    val quietHours: QuietHours = QuietHours(),
    val digestAt: LocalTime = LocalTime.of(8, 0),
    /** Keys muted by the user. A mute is absolute and beats every tier. */
    val muted: Set<String> = emptySet(),
    /**
     * The same key is not repeated inside this.
     *
     * Twenty hours rather than twenty-four, so a daily digest is not
     * suppressed by yesterday's running a few minutes late.
     */
    val dedupeWindow: Duration = Duration.ofHours(20),
)

object NotifyPolicy {

    /**
     * What to do with one notification, right now.
     *
     * Order matters and is deliberate. Mute beats everything, because it is an
     * instruction rather than a preference. Expiry comes next, since a stale
     * reminder is worse than silence. Only then does loudness get considered.
     */
    fun decide(
        notification: TideNotification,
        settings: NotifySettings,
        now: Instant,
        zone: ZoneId,
        lastPosted: Instant? = null,
    ): Delivery {
        if (notification.key in settings.muted) {
            return Delivery.Drop(notification, DropReason.Muted)
        }

        // A reminder about something whose moment has gone is noise carrying a
        // small accusation. It does not go out late.
        if (notification.expiresAt != null && !now.isBefore(notification.expiresAt)) {
            return Delivery.Drop(notification, DropReason.Expired)
        }

        if (lastPosted != null &&
            Duration.between(lastPosted, now) < settings.dedupeWindow
        ) {
            return Delivery.Drop(notification, DropReason.AlreadySaid)
        }

        val local = now.atZone(zone)

        return when (notification.tier) {
            // Urgent is the only thing that may break quiet hours, and it is
            // meant to be rare enough that seeing one is information in itself.
            Tier.Urgent -> Delivery.Now(notification)

            Tier.Default -> if (settings.quietHours.contains(local.toLocalTime())) {
                Delivery.Hold(notification, settings.quietHours.endsAfter(local))
            } else {
                Delivery.Now(notification)
            }

            // Quiet never posts alone. It waits for the digest, which is the
            // difference between an app that tells you things and one that
            // interrupts you.
            Tier.Quiet -> {
                val at = nextDigest(local, settings)
                // Sending a digest after the thing has expired is pointless.
                if (notification.expiresAt != null && !at.isBefore(notification.expiresAt)) {
                    Delivery.Drop(notification, DropReason.Expired)
                } else {
                    Delivery.Digest(notification, at)
                }
            }
        }
    }

    /** The next time the daily summary is due. */
    fun nextDigest(now: ZonedDateTime, settings: NotifySettings): Instant {
        val todaysDigest = now.with(settings.digestAt)
        val target = if (todaysDigest.toInstant() > now.toInstant()) {
            todaysDigest
        } else {
            todaysDigest.plusDays(1)
        }
        // A digest set inside quiet hours waits for them to end, rather than
        // being the one thing that wakes you up.
        return if (settings.quietHours.contains(target.toLocalTime())) {
            settings.quietHours.endsAfter(target)
        } else {
            target.toInstant()
        }
    }

    /**
     * One notification summarising several.
     *
     * Returns null for an empty list rather than an empty digest, because
     * "nothing needs you" is not worth a notification. The app does not check
     * in.
     */
    fun digest(items: List<TideNotification>, now: Instant): TideNotification? {
        if (items.isEmpty()) return null
        if (items.size == 1) return items.single().copy(tier = Tier.Quiet)

        // Counted and listed, not characterised. No "you are falling behind",
        // because a count is a fact and that sentence is a judgement.
        val titles = items.take(3).joinToString(", ") { it.title }
        val body = if (items.size > 3) {
            "$titles, and ${items.size - 3} more."
        } else {
            "$titles."
        }
        return TideNotification(
            key = "digest",
            title = "${items.size} things need you",
            body = body,
            tier = Tier.Quiet,
            createdAt = now,
        )
    }
}
