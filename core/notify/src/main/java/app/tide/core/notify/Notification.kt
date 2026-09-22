package app.tide.core.notify

import java.time.Instant

/**
 * What the app is allowed to interrupt you for.
 *
 * The whole module is built on one assumption: **an interruption has a cost,
 * and the app pays it, not the user.** Every notification a life dashboard
 * sends is one it chose to send about data you could have opened the app to
 * see. So the default answer is a digest at a civilised hour, and anything
 * louder has to earn it.
 *
 * There are no engagement notifications here. Nothing says "you have not
 * opened Tide in 3 days", nothing congratulates a streak, and nothing is sent
 * to bring you back. A notification carries a fact you asked to be told, or it
 * does not go.
 */

/**
 * How loud, and what that costs.
 *
 * Deliberately three. A longer list invites picking the loud one because it
 * feels important while you are writing the feature, which is how every app
 * ends up buzzing about nothing.
 */
enum class Tier {
    /**
     * In the shade, silent, no buzz. The right answer for almost everything.
     *
     * Rolled into the digest rather than posted on its own.
     */
    Quiet,

    /**
     * Makes a sound, once, and waits for quiet hours to end.
     *
     * For something with a time on it that you asked to be reminded about.
     */
    Default,

    /**
     * Breaks quiet hours.
     *
     * For a real deadline with a real cost that cannot wait until morning: a
     * payment that will fail overnight, not a workout. If you find yourself
     * reaching for this while writing a feature, the feature is probably not
     * urgent, you are just close to it.
     */
    Urgent,
}

/**
 * One thing the app wants to say.
 *
 * [key] is what stops the same fact being said twice. Two notifications with
 * the same key inside the dedupe window are the same notification, however
 * many times the thing that generated it ran.
 */
data class TideNotification(
    val key: String,
    val title: String,
    val body: String,
    val tier: Tier = Tier.Quiet,
    val createdAt: Instant,
    /**
     * After this, saying it is worse than not saying it: a reminder about a
     * session whose day has passed is noise plus a small accusation.
     */
    val expiresAt: Instant? = null,
    /** Where tapping it should land. Null opens the app. */
    val deepLink: String? = null,
)

/** What the policy decided to do, and why. Every branch carries its reason. */
sealed interface Delivery {

    /** Post it now. */
    data class Now(val notification: TideNotification) : Delivery

    /**
     * Hold it until quiet hours end, then post.
     *
     * Not dropped. You still get told, at a time you chose.
     */
    data class Hold(val notification: TideNotification, val until: Instant) : Delivery

    /** Fold it into the next digest rather than posting it alone. */
    data class Digest(val notification: TideNotification, val at: Instant) : Delivery

    /** Do not send it at all. */
    data class Drop(val notification: TideNotification, val reason: DropReason) : Delivery
}

enum class DropReason {
    /** Already said, inside the dedupe window. */
    AlreadySaid,

    /** Its moment has passed, and saying it now would only be a reproach. */
    Expired,

    /** The user turned this kind off. */
    Muted,
}

/**
 * A record of what was actually sent.
 *
 * Kept for two reasons. It is how the same fact is not said twice, and it is
 * how a person can check what the app has been doing without taking its word
 * for it. An app that notifies you and keeps no record is one you cannot
 * audit.
 */
data class LedgerEntry(
    val key: String,
    val title: String,
    val tier: Tier,
    val postedAt: Instant,
    /** Set when it went out inside a digest rather than on its own. */
    val inDigest: Boolean = false,
)

/** Reading and writing the record of what was sent. Implemented over Room. */
interface NotificationLedger {
    suspend fun lastPosted(key: String): Instant?
    suspend fun record(entry: LedgerEntry)
    suspend fun since(from: Instant): List<LedgerEntry>
}
