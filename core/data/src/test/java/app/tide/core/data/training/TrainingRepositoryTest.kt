package app.tide.core.data.training

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.TideDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    private val rule = ProgressionRule.Linear(incrementKg = 2.5, stallsBeforeDeload = 2)

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
        )
        db.exercises().upsert(squat)
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
        assertEquals(102.5, results[0].next.loadKg)

        val next = repo.prescriptionFor(squat.id)
        assertNotNull(next)
        assertEquals("the decision must survive into the database", 102.5, next!!.loadKg)
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
            results[0].deloaded,
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
    fun `volume excludes warm-ups`() = runTest {
        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 100.0, reps = 10)  // 1000
        repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)                   // 500
        repo.finishSession(s, rule)

        val volume = db.sessions().volumeBetween(0, clock + 1)
        assertEquals(500.0, volume, 0.01)
    }
}
