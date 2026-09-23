package app.tide.core.data.money

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.TideDatabase
import app.tide.core.schedule.Recurrence
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Subscriptions, and the renewals they produce, against a real database.
 *
 * The point worth pinning down: a renewal is read straight from
 * `Schedule.expand`, not resolved against evidence, so an unwatched
 * subscription's due date passing never turns into a `Missed` reading the
 * way a training rule's would.
 */
@RunWith(RobolectricTestRunner::class)
class MoneyRepositoryTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: MoneyRepository
    private var clock = 1_000_000L
    private var ids = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        repo = MoneyRepository(
            subscriptions = db.subscriptions(),
            now = { clock },
            newId = { "id-${ids++}" },
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a monthly subscription renews once a month, on its day`() = runTest {
        repo.add("Gym", 45.0, Recurrence.MonthlyByDay(1), LocalDate.of(2026, 1, 1))

        val renewals = repo.upcoming(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
            renewals.map { it.date },
        )
        assertTrue(renewals.all { it.name == "Gym" && it.amount == 45.0 })
    }

    @Test
    fun `an archived subscription stops renewing`() = runTest {
        val id = repo.add("Streaming", 12.0, Recurrence.MonthlyByDay(15), LocalDate.of(2026, 1, 15))
        repo.archive(id)

        val renewals = repo.upcoming(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))
        assertTrue("an archived subscription renews nothing", renewals.isEmpty())
    }

    @Test
    fun `the monthly total sums only what actually renews that month`() = runTest {
        repo.add("Gym", 45.0, Recurrence.MonthlyByDay(1), LocalDate.of(2026, 1, 1))
        repo.add("Annual domain", 15.0, Recurrence.MonthlyByDay(dayOfMonth = 20, everyMonths = 12), LocalDate.of(2026, 3, 20))

        assertEquals(
            "January has only the monthly charge",
            45.0,
            repo.monthlyTotal(LocalDate.of(2026, 1, 15)),
            0.001,
        )
        assertEquals(
            "March has the monthly charge and the annual one landing that month",
            60.0,
            repo.monthlyTotal(LocalDate.of(2026, 3, 15)),
            0.001,
        )
    }

    @Test
    fun `two subscriptions on the same day both appear`() = runTest {
        repo.add("Gym", 45.0, Recurrence.MonthlyByDay(1), LocalDate.of(2026, 1, 1))
        repo.add("Phone", 30.0, Recurrence.MonthlyByDay(1), LocalDate.of(2026, 1, 1))

        val renewals = repo.upcoming(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1))
        assertEquals(2, renewals.size)
        assertEquals(setOf("Gym", "Phone"), renewals.map { it.name }.toSet())
    }
}
