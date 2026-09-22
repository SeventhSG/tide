package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * Today, against a real database.
 *
 * The screen used to be entirely hardcoded, so these tests are mostly about
 * what it must **not** say: no volume when nothing was lifted, no session
 * claimed on a day nothing was logged, and no week that was not trained.
 */
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: TodayViewModel

    private val zone = ZoneId.of("UTC")

    /** Wednesday 24 September 2025, midday, so "this week" has days either side. */
    private var clock = LocalDate.of(2025, 9, 24)
        .atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private val squat = ExerciseEntity(
        id = "squat", name = "Back squat", primaryMuscle = Muscle.Quads,
        equipment = Equipment.Barbell, progressionRule = "Linear:2.5:3:0.9",
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        repo = TrainingRepository(
            exercises = db.exercises(),
            routines = db.routines(),
            sessions = db.sessions(),
            state = db.exerciseState(),
            bodyWeight = db.bodyWeight(),
            now = { clock },
        )
        db.exercises().upsert(squat)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private fun awaitState(predicate: (TodayUiState) -> Boolean): TodayUiState {
        val deadline = System.currentTimeMillis() + 2_000
        var last = vm.state.value
        while (System.currentTimeMillis() < deadline) {
            last = vm.state.value
            if (predicate(last)) return last
            Thread.sleep(5)
        }
        throw AssertionError("state never matched, last was: $last")
    }

    private fun start() {
        vm = TodayViewModel(repo, scope, now = { clock }, zone = zone)
    }

    @Test
    fun `an empty database shows no numbers at all`() {
        start()
        val state = awaitState { it.week.isNotEmpty() }

        assertEquals("WED 24 SEPTEMBER", state.dateLabel)
        assertEquals("No session\nyet today.", state.headline)
        assertEquals("Nothing logged yet. The first session starts the history.", state.subline)
        assertNull("zero volume is not a reading, so there is no row", state.volumeLast7Days)
        assertEquals("NONE YET", state.weekSummary)
        assertTrue("nothing trained, so no day is filled", state.week.none { it.trained })
        assertEquals("today is Wednesday, the third square", 2, state.week.indexOfFirst { it.isToday })
    }

    @Test
    fun `a session logged today is counted, not described as scheduled`() {
        runBlocking {
            val s = repo.startSession()
            repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 40.0, reps = 8)
            repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }
            repo.finishSession(s, ProgressionRule.Linear(2.5))
        }

        start()
        val state = awaitState { it.volumeLast7Days != null }

        assertEquals("Trained\ntoday.", state.headline)
        assertEquals("the warm-up is not a working set", "3 working sets logged.", state.subline)
        // 3 x 100 x 5, and the 40 kg warm-up is excluded in SQL.
        assertEquals("1 500 kg", state.volumeLast7Days)
        assertEquals("1 SESSION", state.weekSummary)
        assertTrue(state.week[2].trained)
    }

    @Test
    fun `an open session offers to resume it`() {
        runBlocking {
            val s = repo.startSession()
            repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)
        }
        clock += 24 * 60_000

        start()
        val state = awaitState { it.resuming }

        assertEquals("Session in\nprogress.", state.headline)
        assertEquals("Started 24 minutes ago.", state.subline)
    }

    @Test
    fun `an old session is dated, and its volume has aged out of the window`() {
        runBlocking {
            val s = repo.startSession()
            repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }
            repo.finishSession(s, ProgressionRule.Linear(2.5))
        }
        clock += 9 * 86_400_000L

        start()
        val state = awaitState { it.subline != null }

        assertEquals("Last session was 9 days ago.", state.subline)
        assertNull("the 7 day window has passed over it", state.volumeLast7Days)
        assertEquals("NONE YET", state.weekSummary)
    }
}
