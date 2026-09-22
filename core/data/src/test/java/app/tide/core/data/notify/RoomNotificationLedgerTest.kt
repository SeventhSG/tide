package app.tide.core.data.notify

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.TideDatabase
import app.tide.core.notify.LedgerEntry
import app.tide.core.notify.Tier
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant

/**
 * The record of what the app said, over the real database.
 *
 * The dedupe test is the one with teeth: if `lastPosted` is wrong, the policy
 * above it either repeats itself or goes silent, and both are the kind of bug
 * you only notice after it has annoyed you for a week.
 */
@RunWith(RobolectricTestRunner::class)
class RoomNotificationLedgerTest {

    private lateinit var db: TideDatabase
    private lateinit var ledger: RoomNotificationLedger

    private val t0: Instant = Instant.parse("2025-09-01T08:00:00Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()
        ledger = RoomNotificationLedger(db.notificationLedger())
    }

    @After
    fun tearDown() = db.close()

    private fun entry(key: String, at: Instant, tier: Tier = Tier.Quiet) =
        LedgerEntry(key, "Legs", tier, at)

    @Test
    fun `nothing said means nothing recorded`() = runTest {
        assertNull(ledger.lastPosted("never"))
    }

    @Test
    fun `the last time something was said is the latest, not the first`() = runTest {
        ledger.record(entry("legs", t0))
        ledger.record(entry("legs", t0.plus(Duration.ofDays(1))))
        ledger.record(entry("legs", t0.plus(Duration.ofHours(6))))

        assertEquals(
            "a stale answer here makes the policy repeat itself",
            t0.plus(Duration.ofDays(1)),
            ledger.lastPosted("legs"),
        )
    }

    @Test
    fun `history is kept rather than overwritten`() = runTest {
        ledger.record(entry("legs", t0))
        ledger.record(entry("legs", t0.plus(Duration.ofDays(1))))

        val all = ledger.since(t0.minus(Duration.ofDays(1)))
        assertEquals("both sends must still be on the record", 2, all.size)
    }

    @Test
    fun `keys do not collide with each other`() = runTest {
        ledger.record(entry("legs", t0))
        ledger.record(entry("rent", t0.plus(Duration.ofDays(2))))

        assertEquals(t0, ledger.lastPosted("legs"))
        assertEquals(t0.plus(Duration.ofDays(2)), ledger.lastPosted("rent"))
    }

    @Test
    fun `a tier survives the round trip`() = runTest {
        ledger.record(entry("rent", t0, Tier.Urgent))
        assertEquals(Tier.Urgent, ledger.since(t0.minus(Duration.ofDays(1))).single().tier)
    }

    @Test
    fun `an unreadable tier costs one line, not the screen`() = runTest {
        db.notificationLedger().record(
            app.tide.core.data.db.NotificationLedgerEntity(
                notificationKey = "odd",
                title = "Legs",
                tier = "NotATier",
                postedAt = t0.toEpochMilli(),
            ),
        )
        assertEquals(Tier.Quiet, ledger.since(t0.minus(Duration.ofDays(1))).single().tier)
    }

    @Test
    fun `since leaves out what is older than asked for`() = runTest {
        ledger.record(entry("old", t0.minus(Duration.ofDays(10))))
        ledger.record(entry("new", t0))

        val recent = ledger.since(t0.minus(Duration.ofDays(1)))
        assertEquals(listOf("new"), recent.map { it.key })
    }

    @Test
    fun `pruning drops the old and keeps the recent`() = runTest {
        ledger.record(entry("ancient", t0.minus(Duration.ofDays(200))))
        ledger.record(entry("recent", t0))

        ledger.prune(now = t0)

        val left = ledger.since(Instant.EPOCH).map { it.key }
        assertEquals(listOf("recent"), left)
    }

    @Test
    fun `a digest entry says it was one`() = runTest {
        ledger.record(entry("legs", t0).copy(inDigest = true))
        assertTrue(ledger.since(Instant.EPOCH).single().inDigest)
    }
}
