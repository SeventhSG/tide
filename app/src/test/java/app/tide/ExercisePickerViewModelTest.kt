package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.TrainingRepository
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

/**
 * [ExercisePickerViewModel] against a real database, like the logger's test
 * one screen over. Search is a SQL query, so filtering in memory would prove
 * nothing about what the screen will show.
 */
@RunWith(RobolectricTestRunner::class)
class ExercisePickerViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: ExercisePickerViewModel
    private var clock = 1_700_000_000_000L

    private val squat = ExerciseEntity(
        id = "squat", name = "Back squat", primaryMuscle = Muscle.Quads,
        equipment = Equipment.Barbell,
    )
    private val row = ExerciseEntity(
        id = "row", name = "Barbell row", primaryMuscle = Muscle.Back,
        equipment = Equipment.Barbell,
    )
    private val bench = ExerciseEntity(
        id = "bench", name = "Bench press", primaryMuscle = Muscle.Chest,
        equipment = Equipment.Barbell,
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
        db.exercises().upsertAll(listOf(squat, row, bench))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private fun awaitState(predicate: (ExercisePickerUiState) -> Boolean): ExercisePickerUiState {
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
    fun `the library arrives grouped by muscle`() {
        vm = ExercisePickerViewModel(repo, scope, now = { clock })
        val state = awaitState { it.sections.isNotEmpty() }

        assertEquals(3, state.libraryCount)
        assertEquals(listOf("CHEST", "BACK", "QUADS"), state.sections.map { it.title })
        assertEquals("Bench press", state.sections[0].rows[0].name)
        assertEquals("BARBELL", state.sections[0].rows[0].detail)
        assertEquals(
            "a lift never trained shows no date rather than never",
            null,
            state.sections[0].rows[0].lastTrained,
        )
    }

    @Test
    fun `what was trained recently comes first, and says when`() {
        runBlocking {
            val s = repo.startSession()
            repo.logSet(s, row.id, loadKg = 80.0, reps = 8)
            repo.finishSession(s, app.tide.core.data.training.ProgressionRule.Linear(2.5))
        }
        clock += 3 * 86_400_000L

        vm = ExercisePickerViewModel(repo, scope, now = { clock })
        val state = awaitState { it.sections.isNotEmpty() }

        assertEquals("RECENT", state.sections[0].title)
        assertEquals("Barbell row", state.sections[0].rows[0].name)
        assertEquals("3 DAYS AGO", state.sections[0].rows[0].lastTrained)
        assertEquals(
            "the muscle is named where no section header says it",
            "BARBELL, BACK",
            state.sections[0].rows[0].detail,
        )
    }

    @Test
    fun `typing searches the whole library, not the visible rows`() {
        vm = ExercisePickerViewModel(repo, scope, now = { clock })
        awaitState { it.sections.isNotEmpty() }

        vm.onQueryChange("press")
        val state = awaitState { it.searching && it.sections.isNotEmpty() }

        assertEquals("1 MATCH", state.sections[0].title)
        assertEquals("Bench press", state.sections[0].rows[0].name)
    }

    @Test
    fun `a query that matches nothing says so rather than showing the library`() {
        vm = ExercisePickerViewModel(repo, scope, now = { clock })
        awaitState { it.sections.isNotEmpty() }

        vm.onQueryChange("zercher")
        val state = awaitState { it.searching && it.sections.isEmpty() }

        assertTrue(state.sections.isEmpty())
        assertEquals("zercher", state.query)
    }

    @Test
    fun `an open session is stated, because a pick joins it`() {
        runBlocking { repo.startSession() }

        vm = ExercisePickerViewModel(repo, scope, now = { clock })
        val state = awaitState { it.inSession }

        assertTrue(state.inSession)
    }
}
