package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.notify.RoomNotificationLedger
import app.tide.core.notify.Notifier
import app.tide.notify.DigestWorker
import app.tide.notify.NotifyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * The notification settings.
 *
 * The behaviour that matters is the default: **off**. Nothing about this app
 * opts anyone into being interrupted, and the digest is only scheduled once
 * someone asks for it.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var prefs: NotifyPreferences
    private lateinit var notifier: Notifier
    private lateinit var scope: CoroutineScope
    private lateinit var vm: SettingsViewModel

    private var scheduled: Pair<Boolean, LocalTime>? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, TideDatabase::class.java)
            .allowMainThreadQueries().build()

        context.getSharedPreferences("tide.notify", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()

        prefs = NotifyPreferences(context)
        notifier = Notifier(context, RoomNotificationLedger(db.notificationLedger()))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        vm = SettingsViewModel(
            prefs = prefs,
            notifier = notifier,
            scope = scope,
            onScheduleChanged = { enabled, at -> scheduled = enabled to at },
        )
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    @Test
    fun `the summary is off until it is asked for`() {
        assertFalse("nothing here opts anyone in", vm.state.value.digestEnabled)
        assertEquals("08:00", vm.state.value.digestAt)
        assertTrue("quiet hours are on by default", vm.state.value.quietHoursEnabled)
        assertEquals(null, scheduled)
    }

    @Test
    fun `turning it on schedules it, turning it off cancels it`() {
        vm.onToggleDigest()
        assertEquals(true to LocalTime.of(8, 0), scheduled)
        assertTrue(prefs.digestEnabled)

        vm.onToggleDigest()
        assertEquals(false to LocalTime.of(8, 0), scheduled)
        assertFalse(prefs.digestEnabled)
    }

    @Test
    fun `changing the time reschedules it and survives a new screen`() {
        vm.onToggleDigest()
        vm.onDigestLater()

        assertEquals("09:00", vm.state.value.digestAt)
        assertEquals(true to LocalTime.of(9, 0), scheduled)

        // A settings screen that needs a save button loses changes.
        val reopened = SettingsViewModel(NotifyPreferences(
            ApplicationProvider.getApplicationContext(),
        ), notifier, scope, { _, _ -> })
        assertEquals("09:00", reopened.state.value.digestAt)
    }

    @Test
    fun `quiet hours are stored as whole hours and wrap around midnight`() {
        repeat(3) { vm.onQuietStartLater() }   // 22:00 to 01:00
        assertEquals("01:00", vm.state.value.quietStart)
        assertTrue(
            "a window crossing midnight is the normal case",
            prefs.settings().quietHours.contains(LocalTime.of(3, 0)),
        )
    }

    @Test
    fun `a test notification goes through the ledger like any other`() = runBlocking {
        vm.onSendTest()

        val recorded = RoomNotificationLedger(db.notificationLedger())
            .since(Instant.now().minusSeconds(60))
        assertEquals(1, recorded.size)
        assertEquals("Tide", recorded[0].title)
    }

    @Test
    fun `the digest waits for the chosen hour, tomorrow if it has passed today`() {
        val zone = ZoneId.of("UTC")
        val nineAm = Instant.parse("2026-09-24T09:00:00Z")

        assertEquals(
            "two hours later the same day",
            120,
            DigestWorker.delayUntil(LocalTime.of(11, 0), zone, nineAm).toMinutes(),
        )
        assertEquals(
            "an hour already past waits for tomorrow",
            23 * 60,
            DigestWorker.delayUntil(LocalTime.of(8, 0), zone, nineAm).toMinutes(),
        )
    }
}
