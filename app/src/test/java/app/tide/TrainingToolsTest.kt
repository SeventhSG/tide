package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.ask.TrainingTools
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

/**
 * What Ask can say with no model at all: real sentences straight out of SQL.
 *
 * The point of building this layer first is that every answer here is
 * provable against a database, so these tests are the actual proof that Ask's
 * facts are correct, independent of whether an engine ever phrases them.
 */
@RunWith(RobolectricTestRunner::class)
class TrainingToolsTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private val zone = ZoneId.of("UTC")

    /** Wednesday. */
    private var clock = LocalDate.of(2026, 9, 23)
        .atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()

    private val squat = ExerciseEntity(
        id = "squat", name = "Back squat", primaryMuscle = Muscle.Quads,
        equipment = Equipment.Barbell,
    )
    private val row = ExerciseEntity(
        id = "row", name = "Barbell row", primaryMuscle = Muscle.Back,
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
        db.exercises().upsertAll(listOf(squat, row))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sessions this week counts from Monday, and says zero plainly`() = runBlocking {
        assertEquals("Nothing logged this week yet.", TrainingTools.sessionsThisWeek(repo, clock, zone))

        val s = repo.startSession()
        repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)
        repo.finishSession(s, ProgressionRule.Linear(2.5))

        assertEquals("1 session this week.", TrainingTools.sessionsThisWeek(repo, clock, zone))
    }

    @Test
    fun `volume is absent rather than zero when nothing was lifted`() = runBlocking {
        assertEquals("No volume in the last 7 days.", TrainingTools.volumeLast7Days(repo, clock))

        val s = repo.startSession()
        repo.logSet(s, squat.id, SetKind.WarmUp, loadKg = 60.0, reps = 10)
        repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)
        repo.finishSession(s, ProgressionRule.Linear(2.5))

        assertEquals(
            "the warm-up must not inflate this",
            "500 kg in the last 7 days.",
            TrainingTools.volumeLast7Days(repo, clock),
        )
    }

    @Test
    fun `last performance matches by name the way the picker's search does`() = runBlocking {
        val s = repo.startSession()
        repeat(3) { repo.logSet(s, row.id, loadKg = 82.5, reps = 8) }
        repo.finishSession(s, ProgressionRule.Linear(2.5))

        assertEquals(
            "Barbell row, last time: 82.5 kg x 8 for 3 sets.",
            TrainingTools.lastPerformance(repo, "row"),
        )
    }

    @Test
    fun `a lift never trained says so, not a guess`() = runBlocking {
        assertEquals("No history yet for Back squat.", TrainingTools.lastPerformance(repo, "squat"))
    }

    @Test
    fun `a name matching nothing in the library says so`() = runBlocking {
        assertEquals(
            "Nothing in the library matches \"zercher press\".",
            TrainingTools.lastPerformance(repo, "zercher press"),
        )
    }

    @Test
    fun `muscle balance names the most worked muscle, in real words`() = runBlocking {
        assertEquals("No working sets in the current window yet.", TrainingTools.muscleBalance(repo))

        val s = repo.startSession()
        repeat(3) { repo.logSet(s, squat.id, loadKg = 100.0, reps = 5) }
        repo.finishSession(s, ProgressionRule.Linear(2.5))

        assertEquals(
            "an enum constant is not a sentence",
            "Most worked: Quads, 1 500 kg.",
            TrainingTools.muscleBalance(repo),
        )
    }
}
