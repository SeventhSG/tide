package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SessionViewModel] against a real in-memory database, the way
 * [app.tide.core.data.training.TrainingRepositoryTest] proves the repository
 * one layer down.
 *
 * The viewmodel does real background work, on Room's own executors, not the
 * test thread, so this waits for state rather than asserting immediately
 * after a call returns. [awaitState] is a short, bounded poll instead of a
 * fixed sleep, so it is fast when the database is fast and it still fails
 * loudly, with the last state seen, if the wiring breaks.
 */
@RunWith(RobolectricTestRunner::class)
class SessionViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: SessionViewModel
    private var clock = 1_000_000L

    private val squat = ExerciseEntity(
        id = "squat", name = "Back squat", primaryMuscle = Muscle.Quads,
        progressionRule = "Linear:2.5:3:0.9",
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
        // Cancel and wait, in that order, before closing. A coroutine still
        // inside a Room call when the database shuts under it throws, and the
        // exception surfaces in whichever test happens to run next.
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private fun awaitState(
        timeoutMs: Long = 2_000,
        predicate: (SessionUiState) -> Boolean,
    ): SessionUiState {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = vm.state.value
        while (System.currentTimeMillis() < deadline) {
            last = vm.state.value
            if (predicate(last)) return last
            Thread.sleep(5)
        }
        throw AssertionError("state never matched within ${timeoutMs}ms, last was: $last")
    }

    @Test
    fun `opening the screen starts a session and loads the exercise`() {
        vm = SessionViewModel(squat.id, repo, scope, now = { clock })
        val state = awaitState { it.exerciseName.isNotEmpty() }

        assertEquals("Back squat", state.exerciseName)
        assertEquals("LINEAR +2.5", state.ruleLabel)
        assertEquals(1, state.setNumber)
        assertTrue("no history yet, nothing logged", state.logged.isEmpty())
    }

    @Test
    fun `logging a set reaches the database, not only the screen`() {
        vm = SessionViewModel(squat.id, repo, scope, now = { clock })
        awaitState { it.exerciseName.isNotEmpty() }

        vm.onLoadChange(100.0)
        vm.onRepsChange(5)
        vm.onLogSet()

        val state = awaitState { it.logged.size == 1 }
        assertEquals("100 kg x 5", state.logged[0].summary)
        assertEquals("the next set is number two", 2, state.setNumber)

        val persisted = runBlocking {
            val sessionId = requireNotNull(repo.observeActiveSession().first())
            repo.observeSets(sessionId.id).first()
        }
        assertEquals(
            "a screen showing a set that is not really in the database is a lie",
            1,
            persisted.size,
        )
    }

    @Test
    fun `resuming after a stored prescription prefills next time`() {
        runBlocking {
            val s = repo.startSession()
            repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }
            repo.finishSession(s, ProgressionRule.Linear(2.5))
        }
        clock += 86_400_000

        vm = SessionViewModel(squat.id, repo, scope, now = { clock })
        val state = awaitState { it.loadKg.isNotEmpty() }

        assertEquals("102.5", state.loadKg)
        assertEquals("5", state.reps)
    }
}
