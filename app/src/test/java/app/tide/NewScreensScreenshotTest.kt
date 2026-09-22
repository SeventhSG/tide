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
import app.tide.core.data.db.SetKind
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * The calendar, the settings screen and the logger's new controls, rendered
 * from real state.
 *
 * The calendar runs through its own viewmodel over a database with sessions in
 * it, so the filled days are days that were actually trained.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NewScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 24)
    private var clock = today.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private val squat = ExerciseEntity(
        id = "squat", name = "Back squat", primaryMuscle = Muscle.Quads,
        equipment = Equipment.Barbell, progressionRule = "Linear:2.5:3:0.9",
    )
    private val row = ExerciseEntity(
        id = "row", name = "Barbell row", primaryMuscle = Muscle.Back,
        equipment = Equipment.Barbell, progressionRule = "DoubleProgression:6:9:2.5:3:0.9",
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
        db.exercises().upsertAll(listOf(squat, row))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private fun trainOn(date: LocalDate) = runBlocking {
        val keep = clock
        clock = date.atStartOfDay(zone).plusHours(17).toInstant().toEpochMilli()
        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 40.0, reps = 10)
        repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }
        repeat(3) { repo.logSet(s, row.id, loadKg = 82.5, reps = 8) }
        repo.finishSession(s, ProgressionRule.Linear(2.5))
        clock = keep
    }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            TideTheme {
                Box(Modifier.size(411.dp, 891.dp)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    fun calendar() {
        listOf(14, 16, 18, 21).forEach { trainOn(LocalDate.of(2026, 9, it)) }

        val vm = CalendarViewModel(repo, scope, now = { clock }, zone = zone)
        awaitCalendar(vm) { it.days.isNotEmpty() }
        vm.onSelectDay(LocalDate.of(2026, 9, 18))
        awaitCalendar(vm) { it.selected != null }

        val state = vm.state.value
        capture("calendar") { CalendarScreen(state) }
    }

    @Test
    fun settings() {
        capture("settings") {
            SettingsScreen(
                SettingsUiState(
                    digestEnabled = true,
                    digestAt = "08:00",
                    quietHoursEnabled = true,
                    quietStart = "22:00",
                    quietEnd = "07:00",
                    permissionGranted = false,
                ),
            )
        }
    }

    @Test
    fun loggerWithWarmUpArmedAndASetPendingRemoval() {
        capture("session-warmup") {
            SessionScreen(
                SessionUiState(
                    exerciseName = "Back squat",
                    setNumber = 3,
                    targetSets = 3,
                    ruleLabel = "LINEAR +5",
                    loadKg = "100",
                    reps = "5",
                    lastTime = "97.5 kg x 5",
                    elapsed = "00:18:42",
                    restSinceLastSet = "2:05",
                    warmUp = true,
                    pendingRemoveId = "s2",
                    logged = listOf(
                        SessionUiState.LoggedSet("s1", "W", "40 kg x 10", null, true),
                        SessionUiState.LoggedSet("s2", "1", "100 kg x 5", "RIR 2", false),
                    ),
                ),
            )
        }
    }

    private fun awaitCalendar(vm: CalendarViewModel, predicate: (CalendarUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate(vm.state.value)) return
            Thread.sleep(5)
        }
        throw AssertionError("state never matched, last was: ${vm.state.value}")
    }
}
