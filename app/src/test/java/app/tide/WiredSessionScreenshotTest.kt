package app.tide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.TrainingRepository
import app.tide.core.design.TideTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The session logger rendered from the database, not from sample data.
 *
 * [TodayScreenshotTest] renders a hand written [SessionUiState], which proves
 * the layout and nothing else. This one runs the real [SessionViewModel] over a
 * real repository, logs real sets through it, and photographs whatever the
 * screen actually shows. If the wiring breaks, this picture changes.
 *
 * It is the closest thing to a running app available here, since the emulator
 * needs a hypervisor driver this machine does not have.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WiredSessionScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private var clock = 1_000_000L

    private val row = ExerciseEntity(
        id = "row", name = "Barbell row", primaryMuscle = Muscle.Back,
        secondaryMuscles = "Lats,Biceps",
        progressionRule = "DoubleProgression:6:9:2.5:3:0.9",
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
        db.exercises().upsert(row)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun awaitState(vm: SessionViewModel, predicate: (SessionUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate(vm.state.value)) return
            Thread.sleep(5)
        }
        throw AssertionError("state never matched, last was: ${vm.state.value}")
    }

    @Test
    fun sessionMidWorkout() {
        // A session as it looks partway in: a warm-up, two working sets behind
        // you, and the third prefilled and waiting. Every value below is put
        // through the repository, so nothing on this screen is decoration.
        val vm = SessionViewModel(row.id, repo, scope, now = { clock })
        awaitState(vm) { it.exerciseName.isNotEmpty() }

        val sessionId = runBlocking { repo.startSession() }
        runBlocking {
            repo.logSet(sessionId, row.id, SetKind.WarmUp, loadKg = 60.0, reps = 8)
            repo.logSet(sessionId, row.id, loadKg = 82.5, reps = 8, rir = 2)
            repo.logSet(sessionId, row.id, loadKg = 82.5, reps = 7, rir = 1)
        }
        vm.onLoadChange(82.5)
        vm.onRepsChange(7)
        awaitState(vm) { it.logged.size == 3 }

        // The clock reads a real elapsed time, not a typed in one.
        clock += 42 * 60 * 1000 + 18 * 1000
        vm.tick()

        val state = vm.state.value
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    SessionScreen(state)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/session-wired.png")
    }

    @Test
    fun sessionFirstEverSet() {
        // The honest empty state: an exercise with no history suggests no load
        // at all, because a guessed starting weight is worse than no suggestion.
        val vm = SessionViewModel(row.id, repo, scope, now = { clock })
        awaitState(vm) { it.exerciseName.isNotEmpty() }

        val state = vm.state.value
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) {
                    SessionScreen(state)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/session-wired-empty.png")
    }
}
