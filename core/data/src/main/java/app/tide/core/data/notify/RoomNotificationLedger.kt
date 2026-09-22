package app.tide.core.data.notify

import app.tide.core.data.db.NotificationLedgerDao
import app.tide.core.data.db.NotificationLedgerEntity
import app.tide.core.notify.LedgerEntry
import app.tide.core.notify.NotificationLedger
import app.tide.core.notify.Tier
import java.time.Duration
import java.time.Instant

/**
 * The notification ledger, over the one database.
 *
 * `:core:notify` declares the interface and knows nothing about Room. This is
 * the implementation, and it lives here because there is one database in this
 * app and a second one for notifications would be a second thing to migrate,
 * back up and get wrong.
 */
class RoomNotificationLedger(
    private val dao: NotificationLedgerDao,
    /** Entries older than this are dropped. The record is an audit, not an archive. */
    private val keepFor: Duration = Duration.ofDays(90),
) : NotificationLedger {

    override suspend fun lastPosted(key: String): Instant? =
        dao.lastPostedAt(key)?.let { Instant.ofEpochMilli(it) }

    override suspend fun record(entry: LedgerEntry) {
        dao.record(
            NotificationLedgerEntity(
                notificationKey = entry.key,
                title = entry.title,
                tier = entry.tier.name,
                postedAt = entry.postedAt.toEpochMilli(),
                inDigest = entry.inDigest,
            ),
        )
    }

    override suspend fun since(from: Instant): List<LedgerEntry> =
        dao.since(from.toEpochMilli()).map { it.toDomain() }

    fun observeRecent(limit: Int = 100) = dao.observeRecent(limit)

    suspend fun prune(now: Instant = Instant.now()) =
        dao.prune(now.minus(keepFor).toEpochMilli())
}

private fun NotificationLedgerEntity.toDomain() = LedgerEntry(
    key = notificationKey,
    title = title,
    // An unreadable tier falls back to the quietest rather than throwing. A
    // corrupt row should cost one line of history, not the audit screen.
    tier = runCatching { Tier.valueOf(tier) }.getOrDefault(Tier.Quiet),
    postedAt = Instant.ofEpochMilli(postedAt),
    inDigest = inDigest,
)
