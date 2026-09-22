package app.tide.core.notify

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

/**
 * The Android edge of the module.
 *
 * The policy is tested without Android next door; this covers the parts that
 * genuinely need the framework: that the channels exist and are configured the
 * way the tiers promise, and that offering a notification writes it down.
 */
@RunWith(RobolectricTestRunner::class)
class NotifierTest {

    private lateinit var context: Context
    private lateinit var ledger: FakeLedger
    private lateinit var notifier: Notifier

    private val zone = ZoneId.of("UTC")
    private var now: Instant = Instant.parse("2025-09-01T12:00:00Z")

    /** In memory, because the real one is Room's job and is tested there. */
    private class FakeLedger : NotificationLedger {
        val entries = mutableListOf<LedgerEntry>()
        override suspend fun lastPosted(key: String): Instant? =
            entries.filter { it.key == key }.maxOfOrNull { it.postedAt }
        override suspend fun record(entry: LedgerEntry) { entries += entry }
        override suspend fun since(from: Instant): List<LedgerEntry> =
            entries.filter { !it.postedAt.isBefore(from) }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ledger = FakeLedger()
        notifier = Notifier(context, ledger, zone) { now }
    }

    private fun note(tier: Tier = Tier.Default, key: String = "legs") = TideNotification(
        key = key,
        title = "Legs",
        body = "Scheduled for today.",
        tier = tier,
        createdAt = now,
    )

    @Test
    fun `every tier has a channel, and they are distinct`() {
        notifier.ensureChannels()
        val manager = context.getSystemService(NotificationManager::class.java)

        val ids = Tier.entries.map { notifier.channelFor(it).id }
        assertEquals("a tier sharing a channel cannot be turned off on its own", 3, ids.toSet().size)
        ids.forEach { assertNotNull("channel $it was never created", manager.getNotificationChannel(it)) }
    }

    @Test
    fun `the quiet channel cannot make a sound`() {
        notifier.ensureChannels()
        val manager = context.getSystemService(NotificationManager::class.java)
        val quiet = manager.getNotificationChannel(Notifier.Channel.Quiet.id)

        assertEquals(
            "the daily summary must never buzz",
            NotificationManager.IMPORTANCE_LOW,
            quiet.importance,
        )
        assertEquals(false, quiet.shouldVibrate())
        assertEquals(null, quiet.sound)
    }

    @Test
    fun `the urgent channel is the loud one`() {
        notifier.ensureChannels()
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(Notifier.Channel.Urgent.id).importance,
        )
    }

    @Test
    fun `offering at a civilised hour posts and writes it down`() = runTest {
        notifier.ensureChannels()
        val decision = notifier.offer(note(), NotifySettings())

        assertTrue(decision is Delivery.Now)
        assertEquals(1, ledger.entries.size)
        assertEquals("legs", ledger.entries.single().key)
    }

    @Test
    fun `offering the same thing twice only says it once`() = runTest {
        notifier.ensureChannels()
        notifier.offer(note(), NotifySettings())
        val second = notifier.offer(note(), NotifySettings())

        assertEquals(
            DropReason.AlreadySaid,
            (second as Delivery.Drop).reason,
        )
        assertEquals("and the repeat is not recorded as if it went out", 1, ledger.entries.size)
    }

    @Test
    fun `a held notification is not posted and not written down yet`() = runTest {
        now = Instant.parse("2025-09-01T23:30:00Z")
        notifier = Notifier(context, ledger, zone) { now }
        notifier.ensureChannels()

        val decision = notifier.offer(note(), NotifySettings())
        assertTrue(decision is Delivery.Hold)
        assertTrue(
            "holding is not sending, and the record must say so",
            ledger.entries.isEmpty(),
        )
    }

    @Test
    fun `a quiet notification waits for the digest rather than posting`() = runTest {
        notifier.ensureChannels()
        val decision = notifier.offer(note(Tier.Quiet), NotifySettings())
        assertTrue(decision is Delivery.Digest)
        assertTrue(ledger.entries.isEmpty())
    }
}
