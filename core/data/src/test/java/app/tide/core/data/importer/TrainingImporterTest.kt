package app.tide.core.data.importer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.TrainingRepository
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
 * The importer against a real database.
 *
 * The three things worth proving here are the ones that make an import worth
 * running at all: history becomes a prescription, a warm-up stays a warm-up
 * through the whole trip, and importing the same file twice does not double
 * the history.
 */
@RunWith(RobolectricTestRunner::class)
class TrainingImporterTest {

    private lateinit var db: TideDatabase
    private lateinit var importer: TrainingImporter
    private lateinit var repo: TrainingRepository
    private var ids = 0

    private val utc = ZoneId.of("UTC")

    private val strong = """
        Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Weight Unit,Reps,RPE,Distance,Distance Unit,Seconds,Notes
        "2024-03-04 09:00:00","Push A","1h","Bench Press (Barbell)","W","40","kg","8","","0","km","0",""
        "2024-03-04 09:00:00","Push A","1h","Bench Press (Barbell)","1","60","kg","5","","0","km","0",""
        "2024-03-04 09:00:00","Push A","1h","Bench Press (Barbell)","2","60","kg","5","","0","km","0",""
        "2024-03-04 09:00:00","Push A","1h","Zercher Carry","1","70","kg","6","","0","km","0",""
        "2024-03-06 09:00:00","Pull A","1h","Barbell Row","1","70","kg","8","","0","km","0",""
    """.trimIndent()

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        db.exercises().upsertAll(
            listOf(
                ExerciseEntity(
                    "bench", "Bench press", Muscle.Chest, equipment = Equipment.Barbell,
                ),
                ExerciseEntity(
                    "row", "Barbell row", Muscle.Back, equipment = Equipment.Barbell,
                ),
            ),
        )

        importer = TrainingImporter(
            exercises = db.exercises(),
            sessions = db.sessions(),
            zone = utc,
            newId = { "id-${ids++}" },
        )
        repo = TrainingRepository(
            exercises = db.exercises(),
            routines = db.routines(),
            sessions = db.sessions(),
            state = db.exerciseState(),
            bodyWeight = db.bodyWeight(),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a Strong export becomes sessions, split by workout`() = runTest {
        val summary = importer.import(strong)!!

        assertEquals(ImportFormat.Strong, summary.format)
        assertEquals("two days, two workouts, two sessions", 2, summary.sessionsCreated)
        assertEquals(5, summary.setsImported)
    }

    @Test
    fun `an unmatched lift becomes a custom exercise rather than being dropped`() = runTest {
        val summary = importer.import(strong)!!

        assertEquals(listOf("Zercher Carry"), summary.exercisesCreated)
        val custom = db.exercises().byName("Zercher Carry")
        assertNotNull(custom)
        assertTrue("it must be marked custom, not passed off as library data", custom!!.isCustom)
    }

    @Test
    fun `a warm-up survives the import and still does not count`() = runTest {
        importer.import(strong)

        val sets = db.sessions().setsFor("id-0")
        assertEquals(SetKind.WarmUp, sets.first().kind)

        // The real proof is at the other end: the warm-up was the heaviest
        // thing logged in some sessions, and it must not reach progression.
        val last = repo.lastPerformance("bench")!!
        assertEquals("the 40 kg warm-up must not be the last performance", 60.0, last.loadKg!!, 0.001)
    }

    @Test
    fun `imported history produces a real prescription`() = runTest {
        importer.import(strong)

        // This is the whole point of the feature: an imported log can be
        // trained from on day one, without logging a session first.
        val prescription = repo.prescriptionFor("bench")
        assertNotNull("an imported history must be able to prescribe", prescription)
        assertEquals(60.0, prescription!!.loadKg!!, 0.001)
        assertEquals(2, prescription.sets)
    }

    @Test
    fun `importing the same file twice does not double the history`() = runTest {
        val first = importer.import(strong)!!
        val second = importer.import(strong)!!

        assertEquals(2, first.sessionsCreated)
        assertEquals("the second run must recognise its own sessions", 0, second.sessionsCreated)
        assertEquals(2, second.duplicateSessionsSkipped)
        assertTrue("and it must not create the custom lift again", second.exercisesCreated.isEmpty())
    }

    @Test
    fun `imported sessions are marked retroactive`() = runTest {
        importer.import(strong)
        val session = db.sessions().byId("id-0")!!
        assertTrue(
            "a session rebuilt from a CSV is not one lived through with the timer running",
            session.isRetroactive,
        )
    }

    @Test
    fun `progression runs from imported history without replaying it`() = runTest {
        importer.import(strong)

        // No stalls were manufactured out of the import, so the first real
        // session starts from zero rather than from an invented deload.
        val s = repo.startSession()
        repeat(2) { repo.logSet(s, "bench", loadKg = 60.0, reps = 5) }
        val results = repo.finishSession(s, ProgressionRule.Linear(2.5))

        assertEquals(1, results.size)
        assertEquals("hitting the imported target advances it", 62.5, results[0].next.loadKg!!, 0.001)
        assertEquals(false, results[0].deloaded)
    }

    @Test
    fun `a preview reports what would happen and writes nothing`() = runTest {
        val preview = importer.preview(strong)!!

        assertEquals(ImportFormat.Strong, preview.format)
        assertEquals(2, preview.sessions)
        assertEquals(5, preview.sets)
        assertEquals(listOf("Bench Press (Barbell)", "Barbell Row").sorted(), preview.matched)
        assertEquals("the lift with no library entry is named before committing",
            listOf("Zercher Carry"), preview.unmatched)

        // The point of a preview is that nothing happened yet.
        assertTrue("no session may be written", db.sessions().between(0, Long.MAX_VALUE).isEmpty())
        assertNull("no custom exercise may be created", db.exercises().byName("Zercher Carry"))
    }

    @Test
    fun `a preview of an already imported file shows nothing left unmatched`() = runTest {
        importer.import(strong)
        val preview = importer.preview(strong)!!
        assertTrue(
            "the custom exercise made last time counts as matched now",
            preview.unmatched.isEmpty(),
        )
    }

    @Test
    fun `a FitNotes export groups a day into one session`() = runTest {
        val fitNotes = """
            Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
            2024-04-01,Barbell Bench Press,Chest,60,kg,5,,,,
            2024-04-01,Barbell Row,Back,70,kg,8,,,,
        """.trimIndent()

        val summary = importer.import(fitNotes)!!
        assertEquals(ImportFormat.FitNotes, summary.format)
        assertEquals("a day with no workout names is one session", 1, summary.sessionsCreated)
        assertEquals(2, summary.setsImported)
    }
}
