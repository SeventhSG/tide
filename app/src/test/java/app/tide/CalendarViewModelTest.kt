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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * The history calendar, against a real database.
 *
 * The things worth pinning down: a day is only filled in when a session
 * actually finished, the grid starts on the right weekday, and a day's detail
 * counts working sets rather than everything logged.
 */
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: CalendarViewModel

    private val zone = ZoneId.of("UTC")

    /** Thursday 24 September 2026. September 2026 starts on a Tuesday. */
    private val today = LocalDate.of(2026, 9, 24)
    private var clock = today.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

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

    private fun awaitState(predicate: (CalendarUiState) -> Boolean): CalendarUiState {
        val deadline = System.currentTimeMillis() + 2_000
        var last = vm.state.value
        while (System.currentTimeMillis() < deadline) {
            last = vm.state.value
            if (predicate(last)) return last
            Thread.sleep(5)
        }
        throw AssertionError("state never matched, last was: $last")
    }

    private fun trainOn(date: LocalDate, finish: Boolean = true) = runBlocking {
        clock = date.atStartOfDay(zone).plusHours(17).toInstant().toEpochMilli()
        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 40.0, reps = 10)
        repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }
        if (finish) repo.finishSession(s, ProgressionRule.Linear(2.5))
        clock = today.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
    }

    private fun start() {
        vm = CalendarViewModel(repo, scope, now = { clock }, zone = zone)
    }

    @Test
    fun `the month starts on the right weekday and marks the days trained`() {
        trainOn(LocalDate.of(2026, 9, 14))
        trainOn(LocalDate.of(2026, 9, 16))

        start()
        val state = awaitState { it.days.isNotEmpty() }

        assertEquals("SEPTEMBER 2026", state.monthLabel)
        // 1 September 2026 is a Tuesday, so one blank comes first.
        assertEquals(1, state.days.takeWhile { it.date == null }.size)
        assertEquals(1 + 30, state.days.size)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 16)),
            state.days.filter { it.trained }.map { it.date },
        )
        assertEquals("2 SESSIONS", state.monthSummary)
        assertTrue(state.days.single { it.isToday }.date == today)
        assertFalse("there is no history in the future", state.canGoForward)
    }

    @Test
    fun `an unfinished session is not a day of training`() {
        trainOn(LocalDate.of(2026, 9, 14), finish = false)

        start()
        val state = awaitState { it.days.isNotEmpty() }

        assertTrue("an abandoned session is not training", state.days.none { it.trained })
        assertEquals("NOTHING LOGGED", state.monthSummary)
    }

    @Test
    fun `a tapped day shows what was done, warm-ups not counted`() {
        val day = LocalDate.of(2026, 9, 14)
        trainOn(day)

        start()
        awaitState { it.days.isNotEmpty() }
        vm.onSelectDay(day)

        val detail = requireNotNull(awaitState { it.selected != null }.selected)
        assertEquals("Monday 14 September", detail.title)
        assertEquals(1, detail.exercises.size)
        assertEquals("Back squat", detail.exercises[0].name)
        assertEquals("the warm-up is history, not a working set", "3 sets, top 100 kg x 5", detail.exercises[0].detail)
        assertEquals("1 500 kg", detail.volume)

        // Tapping the same day again closes it.
        vm.onSelectDay(day)
        assertNull(awaitState { it.selected == null }.selected)
    }

    @Test
    fun `stepping back a month reads that month, and forward is allowed again`() {
        trainOn(LocalDate.of(2026, 8, 12))

        start()
        awaitState { it.days.isNotEmpty() }
        vm.onPreviousMonth()

        val state = awaitState { it.monthLabel == "AUGUST 2026" }
        assertEquals(listOf(LocalDate.of(2026, 8, 12)), state.days.filter { it.trained }.map { it.date })
        assertTrue("August is behind us, so forward is a real move", state.canGoForward)
    }
}
