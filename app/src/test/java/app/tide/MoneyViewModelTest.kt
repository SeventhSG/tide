package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.money.MoneyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class MoneyViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: MoneyRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: MoneyViewModel

    private val zone = ZoneId.of("UTC")
    private val clock = LocalDate.of(2026, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        repo = MoneyRepository(db.subscriptions(), now = { clock })
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        vm = MoneyViewModel(repo, scope, now = { clock }, zone = zone)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private fun await(predicate: (MoneyUiState) -> Boolean): MoneyUiState {
        val deadline = System.currentTimeMillis() + 2_000
        var last = vm.state.value
        while (System.currentTimeMillis() < deadline) {
            last = vm.state.value
            if (predicate(last)) return last
            Thread.sleep(5)
        }
        throw AssertionError("state never matched, last was: $last")
    }

    @Test
    fun `nothing added means nothing to add up`() {
        val state = await { true }
        assertTrue(state.subscriptions.isEmpty())
        assertEquals(null, state.monthlyTotalLabel)
    }

    @Test
    fun `adding a monthly subscription shows its next renewal and the month's total`() {
        vm.onAdd("Gym", 45.0, Cadence.Monthly, 20)
        val state = await { it.subscriptions.isNotEmpty() }

        assertEquals("Gym", state.subscriptions[0].name)
        assertEquals("45", state.subscriptions[0].amountLabel)
        assertEquals(LocalDate.of(2026, 1, 20), state.subscriptions[0].nextRenewal)
        assertEquals("45", state.monthlyTotalLabel)
    }

    @Test
    fun `a blank name or a non-positive amount is refused`() {
        vm.onAdd("  ", 10.0, Cadence.Monthly, 1)
        vm.onAdd("Gym", 0.0, Cadence.Monthly, 1)
        vm.onAdd("Gym", -5.0, Cadence.Monthly, 1)

        val state = await { true }
        assertTrue("none of these should have been added", state.subscriptions.isEmpty())
    }

    @Test
    fun `removing a subscription takes it out of the total`() {
        vm.onAdd("Gym", 45.0, Cadence.Monthly, 20)
        val added = await { it.subscriptions.isNotEmpty() }

        vm.onRemove(added.subscriptions[0].id)
        val state = await { it.subscriptions.isEmpty() }
        assertEquals(null, state.monthlyTotalLabel)
    }
}
