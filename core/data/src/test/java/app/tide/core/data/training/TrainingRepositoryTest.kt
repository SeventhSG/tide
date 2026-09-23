package app.tide.core.data.training

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.PlanMode
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.TideDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

/**
 * The engine and the schema, together, against a real SQLite database.
 *
 * The unit tests prove the engine is correct in isolation. These prove it is
 * wired up: that a warm-up logged through the repository really is excluded,
 * that the stall count really does survive a round trip, and that a second
 * "start" really does resume rather than fork the session.
 *
 * Those three are exactly the kind of thing that passes in isolation and breaks
 * in the app.
 */
@RunWith(RobolectricTestRunner::class)
class TrainingRepositoryTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private var clock = 1_000_000L
    private var ids = 0

    private val squat = ExerciseEntity(
        id = "squat", name = "Back squat", primaryMuscle = Muscle.Quads,
    )
    private val bench = ExerciseEntity(
        id = "bench", name = "Bench press", primaryMuscle = Muscle.Chest, equipment = Equipment.Barbell,
    )
    private val rule = ProgressionRule.Linear(incrementKg = 2.5, stallsBeforeDeload = 2)

    /** 1970-01-01T00:16:40Z, a Thursday: dayIndex 3 in the Fixed week. */
    private val todayIndex = 3

    @Before
    fun setUp() = runTest {
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
            newId = { "id-${ids++}" },
            zone = ZoneId.of("UTC"),
        )
        db.exercises().upsert(squat)
        db.exercises().upsert(bench)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `starting twice resumes rather than forking the session`() = runTest {
        val first = repo.startSession()
        val second = repo.startSession()
        assertEquals("a second start must resume the open session", first, second)
    }

    @Test
    fun `a full session advances the load and persists it`() = runTest {
        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 40.0, reps = 8)
        repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }

        val results = repo.finishSession(s, rule)
        assertEquals(1, results.size)
        assertEquals(102.5, results[0].result.next.loadKg)

        val next = repo.prescriptionFor(squat.id)
        assertNotNull(next)
        assertEquals("the decision must survive into the database", 102.5, next!!.loadKg)
    }

    @Test
    fun `finishing twice progresses once`() = runTest {
        val s = repo.startSession()
        repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }

        repo.finishSession(s, rule)
        val second = repo.finishSession(s, rule)

        assertEquals("a second finish must decide nothing", 0, second.size)
        assertEquals(
            "a double tap must not add the increment twice",
            102.5,
            repo.prescriptionFor(squat.id)!!.loadKg,
        )
    }

    @Test
    fun `an empty session is removed, not recorded`() = runTest {
        val s = repo.startSession()
        val results = repo.finishSession(s, rule)

        assertEquals(0, results.size)
        assertNull("opening the logger and leaving is not a workout", db.sessions().byId(s))
        assertNull(repo.observeActiveSession().first())
    }

    @Test
    fun `a warm-up alone is not a session and advances nothing`() = runTest {
        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 40.0, reps = 20)
        val results = repo.finishSession(s, rule)
        assertEquals("warm-ups must never drive progression", 0, results.size)
        assertNull(repo.prescriptionFor(squat.id))
    }

    @Test
    fun `a heavy warm-up cannot inflate the prescription`() = runTest {
        val s = repo.startSession()
        // A warm-up mistakenly logged heavier than the working sets. The filter
        // is what stops it becoming next week's target.
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 180.0, reps = 1)
        repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }

        repo.finishSession(s, rule)
        val next = repo.prescriptionFor(squat.id)!!
        assertEquals(102.5, next.loadKg)
    }

    @Test
    fun `stalls accumulate across sessions and then deload`() = runTest {
        // Session one: hit it, load goes to 102.5.
        var s = repo.startSession()
        repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }
        repo.finishSession(s, rule)
        clock += 86_400_000

        // Session two: miss. Load holds, one stall banked.
        s = repo.startSession()
        repeat(3) { repo.logSet(s, squat.id, loadKg = 102.5, reps = 4) }
        repo.finishSession(s, rule)
        assertEquals(102.5, repo.prescriptionFor(squat.id)!!.loadKg)
        clock += 86_400_000

        // Session three: miss again. Second stall trips the deload.
        s = repo.startSession()
        repeat(3) { repo.logSet(s, squat.id, loadKg = 102.5, reps = 4) }
        val results = repo.finishSession(s, rule)
        assertEquals(
            "the stall count must survive the round trip or a deload never fires",
            true,
            results[0].result.deloaded,
        )
        assertEquals(92.25, repo.prescriptionFor(squat.id)!!.loadKg)
    }

    @Test
    fun `an exercise with no history suggests nothing rather than guessing`() = runTest {
        assertNull(
            "a guessed starting weight is worse than no suggestion",
            repo.prescriptionFor(squat.id),
        )
    }

    @Test
    fun `the muscle map excludes warm-ups in SQL, like everything else`() = runTest {
        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 100.0, reps = 10)  // 1000
        repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)                   // 500
        repo.finishSession(s, rule)

        val quads = repo.muscleMap().single { it.muscle == Muscle.Quads }
        assertEquals(
            "a warm-up counted here would inflate every muscle on the map",
            500.0,
            quads.volumeKg,
            0.01,
        )
    }

    @Test
    fun `the muscle map reads a best 1RM straight from the sets`() = runTest {
        // Never finished through the engine, the way imported history arrives.
        val s = repo.startSession()
        repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)
        repo.finishSession(s, rule)

        val quads = repo.muscleMap().single { it.muscle == Muscle.Quads }
        // Epley: 100 * (1 + 5/30) = 116.67
        assertEquals(116.67, quads.bestEstimated1rmKg!!, 0.01)
    }

    @Test
    fun `volume excludes warm-ups`() = runTest {
        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 100.0, reps = 10)  // 1000
        repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)                   // 500
        repo.finishSession(s, rule)

        val volume = db.sessions().volumeBetween(0, clock + 1)
        assertEquals(500.0, volume, 0.01)
    }

    // --- the plan -----------------------------------------------------

    @Test
    fun `fixed mode plans today by weekday`() = runTest {
        repo.addToPlan(todayIndex, squat.id)
        val planned = repo.plannedToday()
        assertNotNull("today's weekday has something planned", planned)
        assertEquals(todayIndex, planned!!.dayIndex)
        assertEquals(listOf(squat.id), planned.exercises.map { it.exerciseId })
    }

    @Test
    fun `fixed mode says nothing when today has nothing planned`() = runTest {
        repo.addToPlan((todayIndex + 1) % 7, squat.id)
        assertNull("a different weekday's plan is not today's", repo.plannedToday())
    }

    @Test
    fun `starting a session tags it with today's fixed plan`() = runTest {
        repo.addToPlan(todayIndex, squat.id)
        val id = repo.startSession()
        val session = db.sessions().byId(id)!!
        assertEquals(repo.plan().id, session.routineId)
        assertEquals(todayIndex, session.dayIndex)
    }

    @Test
    fun `starting a session with no plan stays freestyle`() = runTest {
        val id = repo.startSession()
        val session = db.sessions().byId(id)!!
        assertNull(session.routineId)
        assertNull(session.dayIndex)
    }

    @Test
    fun `an explicit start is never overridden by the plan`() = runTest {
        repo.addToPlan(todayIndex, squat.id)
        val id = repo.startSession(routineId = null, dayIndex = 5)
        val session = db.sessions().byId(id)!!
        assertNull("a deliberate day with no routine is still deliberate", session.routineId)
        assertEquals(5, session.dayIndex)
    }

    @Test
    fun `switching plan mode clears the plan`() = runTest {
        repo.addToPlan(todayIndex, squat.id)
        repo.setPlanMode(PlanMode.Rotation)
        assertEquals(PlanMode.Rotation, repo.planMode())
        assertTrue(
            "the old fixed plan must not be reinterpreted as a rotation",
            repo.planDays().values.all { it.isEmpty() },
        )
    }

    @Test
    fun `switching to the mode already in effect changes nothing`() = runTest {
        repo.addToPlan(todayIndex, squat.id)
        repo.setPlanMode(PlanMode.Fixed)
        assertEquals(1, repo.planDays()[todayIndex]?.size)
    }

    @Test
    fun `rotation days are created by naming them, in order`() = runTest {
        repo.setPlanMode(PlanMode.Rotation)
        val push = repo.addDay("Push")
        val pull = repo.addDay("Pull")
        val legs = repo.addDay("Legs")
        assertEquals(0, push)
        assertEquals(1, pull)
        assertEquals(2, legs)
        assertEquals(mapOf(0 to "Push", 1 to "Pull", 2 to "Legs"), repo.planDayLabels())
    }

    @Test
    fun `renaming a rotation day keeps its position`() = runTest {
        repo.setPlanMode(PlanMode.Rotation)
        val push = repo.addDay("Push")
        repo.renameDay(push, "Upper")
        assertEquals("Upper", repo.planDayLabels()[push])
    }

    @Test
    fun `deleting a rotation day compacts the ones after it`() = runTest {
        repo.setPlanMode(PlanMode.Rotation)
        repo.addDay("Push")
        repo.addDay("Pull")
        repo.addDay("Legs")
        repo.addToPlan(0, squat.id)
        repo.addToPlan(1, bench.id)

        repo.removeDay(0)

        assertEquals(mapOf(0 to "Pull", 1 to "Legs"), repo.planDayLabels())
        // Pull's exercise moved from slot 1 down to slot 0 with the slot itself.
        assertEquals(listOf(bench.id), repo.planDays()[0]?.map { it.exerciseId })
        assertTrue("Push and its squat are both gone", repo.planDays()[2].orEmpty().isEmpty())
    }

    @Test
    fun `moving a rotation day swaps it with its neighbour`() = runTest {
        repo.setPlanMode(PlanMode.Rotation)
        repo.addDay("Push")
        repo.addDay("Pull")
        repo.addToPlan(0, squat.id)
        repo.addToPlan(1, bench.id)

        repo.moveDay(1, up = true)

        assertEquals(mapOf(0 to "Pull", 1 to "Push"), repo.planDayLabels())
        assertEquals(listOf(bench.id), repo.planDays()[0]?.map { it.exerciseId })
        assertEquals(listOf(squat.id), repo.planDays()[1]?.map { it.exerciseId })
    }

    @Test
    fun `rotation next up starts at the first slot with no history`() = runTest {
        repo.setPlanMode(PlanMode.Rotation)
        repo.addDay("Push")
        repo.addDay("Pull")
        repo.addToPlan(0, squat.id)
        repo.addToPlan(1, bench.id)

        assertEquals(0, repo.plannedToday()!!.dayIndex)
    }

    @Test
    fun `rotation next up follows the last finished session and wraps around`() = runTest {
        repo.setPlanMode(PlanMode.Rotation)
        repo.addDay("Push")
        repo.addDay("Pull")
        repo.addToPlan(0, squat.id)
        repo.addToPlan(1, bench.id)

        val first = repo.startSession()
        assertEquals(0, db.sessions().byId(first)!!.dayIndex)
        repo.logSet(first, squat.id, loadKg = 60.0, reps = 5)
        repo.finishSession(first, rule)

        assertEquals("after Push, next up is Pull", 1, repo.plannedToday()!!.dayIndex)

        clock += 86_400_000
        val second = repo.startSession()
        assertEquals(1, db.sessions().byId(second)!!.dayIndex)
        repo.logSet(second, bench.id, loadKg = 40.0, reps = 5)
        repo.finishSession(second, rule)

        assertEquals("after Pull, it wraps back to Push", 0, repo.plannedToday()!!.dayIndex)
    }

    @Test
    fun `rotation next up falls back to the first slot when its slot was deleted`() = runTest {
        repo.setPlanMode(PlanMode.Rotation)
        val routineId = repo.plan().id
        repo.addDay("Push")
        repo.addDay("Pull")
        repo.addDay("Legs")
        repo.addToPlan(0, squat.id)

        val s = repo.startSession(routineId, 2)
        repo.logSet(s, squat.id, loadKg = 60.0, reps = 5)
        repo.finishSession(s, rule)

        repo.removeDay(2)

        assertEquals(
            "a session tagged to a slot that no longer exists still starts the rotation over",
            0,
            repo.plannedToday()!!.dayIndex,
        )
    }
}
