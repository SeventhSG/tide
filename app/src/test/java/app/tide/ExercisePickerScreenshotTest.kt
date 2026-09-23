package app.tide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.TrainingRepository
import app.tide.core.design.TideTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId

/**
 * The picker photographed over the real seeded library, through the real
 * viewmodel, so the rows, the recent section and the dates are whatever the
 * database actually holds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ExercisePickerScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private var clock = 1_700_000_000_000L

    private val library = listOf(
        ExerciseEntity("squat", "Back squat", Muscle.Quads, "Glutes", Equipment.Barbell),
        ExerciseEntity("front-squat", "Front squat", Muscle.Quads, "Abs", Equipment.Barbell),
        ExerciseEntity("bench", "Bench press", Muscle.Chest, "Triceps", Equipment.Barbell),
        ExerciseEntity("incline-db", "Incline dumbbell press", Muscle.Chest, "", Equipment.Dumbbell),
        ExerciseEntity("row", "Barbell row", Muscle.Back, "Biceps", Equipment.Barbell),
        ExerciseEntity("pull-up", "Pull-up", Muscle.Lats, "Biceps", Equipment.Bodyweight),
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
            zone = ZoneId.of("UTC"),
        )
        db.exercises().upsertAll(library)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private fun awaitState(
        vm: ExercisePickerViewModel,
        predicate: (ExercisePickerUiState) -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate(vm.state.value)) return
            Thread.sleep(5)
        }
        throw AssertionError("state never matched, last was: ${vm.state.value}")
    }

    private fun capture(state: ExercisePickerUiState, name: String) {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    ExercisePickerScreen(state)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun pickerBrowsing() {
        // Two lifts with history, so the recent section is real rather than posed.
        runBlocking {
            val s = repo.startSession()
            repo.logSet(s, "row", loadKg = 80.0, reps = 8)
            repo.logSet(s, "bench", loadKg = 70.0, reps = 5)
            repo.finishSession(s, ProgressionRule.Linear(2.5))
        }
        clock += 2 * 86_400_000L

        val vm = ExercisePickerViewModel(repo, scope, now = { clock })
        awaitState(vm) { it.sections.isNotEmpty() }
        capture(vm.state.value, "picker")
    }

    @Test
    fun pickerSearching() {
        val vm = ExercisePickerViewModel(repo, scope, now = { clock })
        awaitState(vm) { it.sections.isNotEmpty() }
        vm.onQueryChange("squat")
        awaitState(vm) { it.searching && it.sections.isNotEmpty() }
        capture(vm.state.value, "picker-search")
    }

    @Test
    fun pickerNoMatch() {
        val vm = ExercisePickerViewModel(repo, scope, now = { clock })
        awaitState(vm) { it.sections.isNotEmpty() }
        vm.onQueryChange("zercher")
        awaitState(vm) { it.searching && it.sections.isEmpty() }
        capture(vm.state.value, "picker-no-match")
    }

    @Test
    fun pickerPlanned() {
        // 1 700 000 000 000 ms is a Tuesday: dayIndex 1 in the fixed week.
        runBlocking {
            repo.addToPlan(1, "squat")
            repo.addToPlan(1, "bench")
        }

        val vm = ExercisePickerViewModel(repo, scope, now = { clock })
        awaitState(vm) { it.sections.isNotEmpty() }
        capture(vm.state.value, "picker-planned")
    }
}
