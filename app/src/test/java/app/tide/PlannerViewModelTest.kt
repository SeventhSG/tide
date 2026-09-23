package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.PlanMode
import app.tide.core.data.db.ScheduleKind
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.schedule.ScheduleRepository
import app.tide.core.data.training.TrainingRepository
import app.tide.notify.PlanSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * The week planner, and the schedule rule it writes.
 *
 * Order is the thing worth pinning down: a plan is "squats, then rows", so
 * moving a line has to survive a round trip through the database rather than
 * only looking right on screen.
 */
@RunWith(RobolectricTestRunner::class)
class PlannerViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var schedule: ScheduleRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: PlannerViewModel
    private var failure: Throwable? = null

    private val zone = ZoneId.of("UTC")

    /** Wednesday 23 September 2026, so "today" is the third chip. */
    private val clock = LocalDate.of(2026, 9, 23)
        .atStartOfDay(zone).plusHours(9).toInstant().toEpochMilli()

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
        schedule = ScheduleRepository(db.schedule(), now = { clock })
        db.exercises().upsertAll(
            listOf(
                ExerciseEntity("squat", "Back squat", Muscle.Quads, equipment = Equipment.Barbell),
                ExerciseEntity("bench", "Bench press", Muscle.Chest, equipment = Equipment.Barbell),
                ExerciseEntity("row", "Barbell row", Muscle.Back, equipment = Equipment.Barbell),
            ),
        )
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Unconfined +
                kotlinx.coroutines.CoroutineExceptionHandler { _, e -> failure = e },
        )
        vm = PlannerViewModel(
            repository = repo,
            scope = scope,
            planSchedule = PlanSchedule(repo, schedule),
            now = { clock },
            zone = zone,
        )
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    /**
     * The viewmodel writes through Room, which resumes on its own executor, so
     * every assertion waits for the state rather than racing it.
     */
    private fun await(predicate: (PlannerUiState) -> Boolean): PlannerUiState {
        val deadline = System.currentTimeMillis() + 2_000
        var last = vm.state.value
        while (System.currentTimeMillis() < deadline) {
            last = vm.state.value
            if (predicate(last)) return last
            Thread.sleep(5)
        }
        throw AssertionError("state never matched, last was: $last, failure was: $failure")
    }

    private fun awaitRules(count: Int): List<app.tide.core.data.db.ScheduleRuleEntity> {
        val deadline = System.currentTimeMillis() + 2_000
        var last = runBlocking { schedule.rules() }
        while (System.currentTimeMillis() < deadline) {
            last = runBlocking { schedule.rules() }
            if (last.size == count) return last
            Thread.sleep(5)
        }
        throw AssertionError("expected $count rules, found ${last.size}")
    }

    @Test
    fun `a day with nothing in it is a rest day, and says nothing else`() {
        val state = await { it.slots.isNotEmpty() }
        assertTrue(state.exercises.isEmpty())
        assertEquals(0, state.plannedTotal)
        assertTrue("an empty plan writes no rule", awaitRules(0).isEmpty())
        assertEquals("today is Wednesday", 2, state.slots.indexOfFirst { it.isCurrent })
    }

    @Test
    fun `exercises are added in order, and keep it`() {
        vm.onSelectDay(0)
        vm.onAdd("squat")
        await { it.exercises.size == 1 }
        vm.onAdd("bench")
        await { it.exercises.size == 2 }
        vm.onAdd("row")

        val lines = await { it.exercises.size == 3 }.exercises
        assertEquals(listOf("Back squat", "Bench press", "Barbell row"), lines.map { it.name })
        assertEquals(listOf(1, 2, 3), lines.map { it.position })
        assertFalse("the first line cannot move up", lines[0].canMoveUp)
        assertFalse("the last line cannot move down", lines[2].canMoveDown)
    }

    @Test
    fun `moving a line up survives the database, not just the screen`() {
        vm.onSelectDay(0)
        vm.onAdd("squat")
        await { it.exercises.size == 1 }
        vm.onAdd("bench")
        val two = await { it.exercises.size == 2 }

        vm.onMoveUp(two.exercises[1].id)
        assertEquals(
            listOf("Bench press", "Back squat"),
            await { it.exercises.firstOrNull()?.name == "Bench press" }.exercises.map { it.name },
        )

        val fromDatabase = runBlocking { repo.planDays()[0].orEmpty() }
        assertEquals(listOf("Bench press", "Back squat"), fromDatabase.map { it.name })
        assertEquals("the order is rewritten consecutively", listOf(0, 1), fromDatabase.map { it.orderInDay })
    }

    @Test
    fun `a line can move to the next day and lands at the end of it`() {
        vm.onSelectDay(0)
        vm.onAdd("squat")
        await { it.exercises.size == 1 }
        vm.onAdd("bench")
        await { it.exercises.size == 2 }
        vm.onSelectDay(1)
        vm.onAdd("row")
        await { it.exercises.size == 1 }

        vm.onSelectDay(0)
        val monday = await { it.exercises.size == 2 }
        vm.onMoveToDay(monday.exercises[0].id, 1)

        assertEquals(listOf("Bench press"), await { it.exercises.size == 1 }.exercises.map { it.name })
        vm.onSelectDay(1)
        assertEquals(
            listOf("Barbell row", "Back squat"),
            await { it.exercises.size == 2 }.exercises.map { it.name },
        )
    }

    @Test
    fun `removing a line closes the gap it left`() {
        vm.onSelectDay(3)
        vm.onAdd("squat")
        await { it.exercises.size == 1 }
        vm.onAdd("bench")
        await { it.exercises.size == 2 }
        vm.onAdd("row")
        val three = await { it.exercises.size == 3 }

        vm.onRemove(three.exercises[1].id)
        val two = await { it.exercises.size == 2 }
        assertEquals(listOf("Back squat", "Barbell row"), two.exercises.map { it.name })

        vm.onMoveUp(two.exercises[1].id)
        assertEquals(
            listOf("Barbell row", "Back squat"),
            await { it.exercises.firstOrNull()?.name == "Barbell row" }.exercises.map { it.name },
        )
    }

    @Test
    fun `the plan writes one schedule rule, so the summary has something true to say`() {
        vm.onSelectDay(0)
        vm.onAdd("squat")
        await { it.exercises.size == 1 }
        vm.onSelectDay(3)
        vm.onAdd("bench")
        await { it.plannedTotal == 2 }

        val rules = awaitRules(1)
        assertEquals("one rule, rewritten, never a pile of them", 1, rules.size)
        assertEquals("Planned training", rules[0].title)

        // Monday and Thursday are planned, and the rule is anchored today, so it
        // claims the Thursday ahead and the Monday after it. A plan made on
        // Wednesday does not reach back and declare that Monday was missed.
        val outlook = runBlocking {
            schedule.outlook(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 30))
        }
        assertEquals(
            listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 28)),
            outlook.map { it.occurrence.due },
        )
    }

    @Test
    fun `emptying the plan takes its rule with it`() {
        vm.onSelectDay(0)
        vm.onAdd("squat")
        val planned = await { it.exercises.size == 1 }
        assertEquals(1, awaitRules(1).size)

        vm.onRemove(planned.exercises[0].id)
        await { it.exercises.isEmpty() }
        assertTrue(
            "a plan with nothing in it must not leave a rule behind",
            awaitRules(0).isEmpty(),
        )
    }

    // --- rotation -------------------------------------------------------

    @Test
    fun `switching to rotation clears the fixed plan and starts empty`() {
        vm.onSelectDay(0)
        vm.onAdd("squat")
        await { it.exercises.size == 1 }

        vm.onSwitchMode(PlanMode.Rotation)
        val state = await { it.mode == PlanMode.Rotation }
        assertTrue("a fresh rotation has no slots until one is named", state.slots.isEmpty())
        assertTrue("switching modes must not carry the old plan across", state.exercises.isEmpty())
    }

    @Test
    fun `a rotation day is created by naming it, and selected`() {
        vm.onSwitchMode(PlanMode.Rotation)
        await { it.mode == PlanMode.Rotation }

        vm.onAddDay("Push")
        val state = await { it.slots.isNotEmpty() }
        assertEquals(listOf("Push"), state.slots.map { it.name })
        assertEquals(0, state.selectedDay)
    }

    @Test
    fun `renaming a rotation day updates its label without moving it`() {
        vm.onSwitchMode(PlanMode.Rotation)
        await { it.mode == PlanMode.Rotation }
        vm.onAddDay("Push")
        await { it.slots.isNotEmpty() }

        vm.onRenameDay(0, "Upper")
        assertEquals("Upper", await { it.slots.firstOrNull()?.name == "Upper" }.slots[0].name)
    }

    @Test
    fun `moving a rotation day swaps its neighbour and the selection follows it`() {
        vm.onSwitchMode(PlanMode.Rotation)
        await { it.mode == PlanMode.Rotation }
        vm.onAddDay("Push")
        await { it.slots.size == 1 }
        vm.onAddDay("Pull")
        await { it.slots.size == 2 }

        vm.onMoveDayRight(0)
        val state = await { it.slots.map { s -> s.name } == listOf("Pull", "Push") }
        assertEquals("Pull was selected and moved to slot 0, so selection follows it there", 0, state.selectedDay)
    }

    @Test
    fun `deleting a rotation day compacts the rest and closes the gap in selection`() {
        vm.onSwitchMode(PlanMode.Rotation)
        await { it.mode == PlanMode.Rotation }
        vm.onAddDay("Push")
        await { it.slots.size == 1 }
        vm.onAddDay("Pull")
        await { it.slots.size == 2 }
        vm.onAddDay("Legs")
        await { it.slots.size == 3 }
        vm.onSelectDay(2)
        await { it.selectedDay == 2 }

        vm.onRemoveDay(0)
        val state = await { it.slots.size == 2 }
        assertEquals(listOf("Pull", "Legs"), state.slots.map { it.name })
        assertEquals("selection shifts down with everything after the deleted slot", 1, state.selectedDay)
    }

    @Test
    fun `a line moved to the neighbouring day wraps against the real rotation length, not seven`() {
        vm.onSwitchMode(PlanMode.Rotation)
        await { it.mode == PlanMode.Rotation }
        vm.onAddDay("Push")
        await { it.slots.size == 1 }
        vm.onAddDay("Pull")
        await { it.slots.size == 2 }

        vm.onSelectDay(0)
        vm.onAdd("squat")
        val one = await { it.exercises.size == 1 }

        vm.onMoveToDay(one.exercises[0].id, -1)
        vm.onSelectDay(1)
        assertEquals(
            "wrapping left from slot 0 with two slots lands on slot 1, not slot 6",
            listOf("Back squat"),
            await { it.exercises.size == 1 }.exercises.map { it.name },
        )
    }

    @Test
    fun `a rotation plan writes a TimesPerWeek quota, not a weekday rule`() {
        vm.onSwitchMode(PlanMode.Rotation)
        await { it.mode == PlanMode.Rotation }
        vm.onAddDay("Push")
        await { it.slots.size == 1 }
        vm.onAdd("squat")
        await { it.exercises.size == 1 }
        vm.onAddDay("Pull")
        await { it.slots.size == 2 }
        vm.onAdd("bench")
        await { it.plannedTotal == 2 }

        val rules = awaitRules(1)
        assertEquals("Planned training", rules[0].title)
        assertEquals(ScheduleKind.Training, rules[0].kind)
        assertEquals("TimesPerWeek:2", rules[0].recurrence)
    }
}
