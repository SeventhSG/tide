package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetKind
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
 * The muscle map, end to end over a real database.
 *
 * The fatigue test is the one to keep: the index is a model, so it gets a bar
 * and a date, and never a number anyone could mistake for a measurement.
 */
@RunWith(RobolectricTestRunner::class)
class MuscleMapViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: MuscleMapViewModel
    private var clock = 1_700_000_000_000L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        db.exercises().upsertAll(
            listOf(
                ExerciseEntity("squat", "Back squat", Muscle.Quads, secondaryMuscles = "Glutes"),
                ExerciseEntity("bench", "Bench press", Muscle.Chest, secondaryMuscles = "Triceps"),
            ),
        )
        repo = TrainingRepository(
            exercises = db.exercises(),
            routines = db.routines(),
            sessions = db.sessions(),
            state = db.exerciseState(),
            bodyWeight = db.bodyWeight(),
            now = { clock },
        )

        val s = repo.startSession()
        repo.logSet(s, "squat", SetKind.WarmUp, loadKg = 60.0, reps = 5)
        repo.logSet(s, "squat", loadKg = 140.0, reps = 5)
        repo.logSet(s, "bench", loadKg = 100.0, reps = 5)
        repo.finishSession(s, app.tide.core.data.training.ProgressionRule.Linear(2.5))

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        vm = MuscleMapViewModel(repo, scope)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private fun await(view: MuscleView): MuscleMapUiState {
        vm.onSelectView(view)
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (s.view == view && s.rows.isNotEmpty()) return s
            vm.onSelectView(view)
            Thread.sleep(5)
        }
        throw AssertionError("no rows for $view, state was ${vm.state.value}")
    }

    @Test
    fun `balance counts the working set and not the warm-up`() {
        val quads = await(MuscleView.Balance).rows.single { it.muscle == "Quads" }
        // 140 x 5 only. The 60 kg warm-up must not appear.
        assertEquals("700 kg", quads.value)
    }

    @Test
    fun `a secondary muscle appears, at its share`() {
        val rows = await(MuscleView.Balance).rows.associateBy { it.muscle }
        assertEquals("700 kg", rows["Quads"]!!.value)
        assertEquals("a squat's glutes are not nothing", "350 kg", rows["Glutes"]!!.value)
    }

    @Test
    fun `fatigue is never given a number`() {
        val rows = await(MuscleView.Fatigue).rows
        assertTrue("there must be something to look at", rows.isNotEmpty())
        rows.forEach { row ->
            val value = row.value ?: return@forEach
            assertTrue(
                "fatigue is an index out of a model, so '$value' must be a date, not a reading",
                value == "today" || value.endsWith("d"),
            )
        }
    }

    @Test
    fun `strength shows an estimate for a muscle trained primarily`() {
        val rows = await(MuscleView.Strength).rows.associateBy { it.muscle }
        // Epley on 140 x 5 is 163.3, shown whole.
        assertEquals("163 kg, today", rows["Quads"]!!.value)
    }

    @Test
    fun `a multi word muscle reads as words`() {
        db.exercises().let { }
        val labels = await(MuscleView.Balance).rows.map { it.muscle }
        assertTrue(
            "FrontDelts would be unreadable on a chart",
            labels.none { it.contains(Regex("[a-z][A-Z]")) },
        )
    }
}
