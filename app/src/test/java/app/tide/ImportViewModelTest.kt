package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.importer.TrainingImporter
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
import java.time.ZoneId

/**
 * [ImportViewModel] over a real importer and a real database.
 *
 * Reading the file is the only thing faked, because a `ContentResolver` is
 * the one part of this that genuinely needs a device.
 */
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest {

    private lateinit var db: TideDatabase
    private lateinit var importer: TrainingImporter
    private lateinit var scope: CoroutineScope
    private lateinit var vm: ImportViewModel

    private val utc = ZoneId.of("UTC")
    private var files = mutableMapOf<String, String?>()

    private val strong = """
        Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Weight Unit,Reps,RPE,Distance,Distance Unit,Seconds,Notes
        "2024-03-04 09:00:00","Push A","1h","Bench Press (Barbell)","1","60","kg","5","","0","km","0",""
        "2024-03-04 09:00:00","Push A","1h","Zercher Carry","1","70","kg","6","","0","km","0",""
        "2024-05-06 09:00:00","Pull A","1h","Barbell Row","1","70","kg","8","","0","km","0",""
    """.trimIndent()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        db.exercises().upsertAll(
            listOf(
                ExerciseEntity("bench", "Bench press", Muscle.Chest),
                ExerciseEntity("row", "Barbell row", Muscle.Back),
            ),
        )
        // Ids must actually differ, or every row upserts over the last one.
        var ids = 0
        importer = TrainingImporter(db.exercises(), db.sessions(), utc) { "id-${ids++}" }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        files["good.csv"] = strong
        files["empty.csv"] = ""
        files["nonsense.csv"] = "Name,Email\nAda,ada@example.com"

        vm = ImportViewModel(importer, scope, { files[it] }, utc)
    }

    @After
    fun tearDown() = runBlocking {
        // Cancel and wait, in that order, before closing. A coroutine still
        // inside a Room call when the database shuts under it throws, and the
        // exception surfaces in whichever test happens to run next.
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    private inline fun <reified T : ImportUiState> await(timeoutMs: Long = 5_000): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val s = vm.state.value
            if (s is T) return s
            Thread.sleep(5)
        }
        throw AssertionError("never reached ${T::class.simpleName}, stuck at ${vm.state.value}")
    }

    @Test
    fun `a good file previews without writing anything`() {
        vm.onFileChosen("good.csv")
        val ready = await<ImportUiState.Ready>()

        assertEquals("Strong", ready.format)
        assertEquals(2, ready.sessions)
        assertEquals(3, ready.sets)
        assertEquals(listOf("Zercher Carry"), ready.unmatched)
        assertEquals("March 2024 to May 2024", ready.span)

        val written = runBlocking { db.sessions().between(0, Long.MAX_VALUE) }
        assertTrue("a preview must not write a session", written.isEmpty())
    }

    @Test
    fun `confirming writes the history`() {
        vm.onFileChosen("good.csv")
        await<ImportUiState.Ready>()

        vm.onConfirm()
        val done = await<ImportUiState.Done>()

        assertEquals(2, done.sessions)
        assertEquals(3, done.sets)
        assertEquals(listOf("Zercher Carry"), done.created)

        val written = runBlocking { db.sessions().between(0, Long.MAX_VALUE) }
        assertEquals(2, written.size)
    }

    @Test
    fun `a file that is not an export says which ones are`() {
        vm.onFileChosen("nonsense.csv")
        val failed = await<ImportUiState.Failed>()
        assertTrue(
            "naming the formats is the only part of this message that helps",
            failed.reason.contains("FitNotes") && failed.reason.contains("Strong"),
        )
    }

    @Test
    fun `an empty file fails rather than importing nothing quietly`() {
        vm.onFileChosen("empty.csv")
        val failed = await<ImportUiState.Failed>()
        assertTrue(failed.reason.contains("empty"))
    }

    @Test
    fun `a file that cannot be opened fails rather than crashing`() {
        vm.onFileChosen("does-not-exist.csv")
        await<ImportUiState.Failed>()
    }

    @Test
    fun `a single month history reads as one month, not a range`() {
        files["onemonth.csv"] = """
            Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
            2024-03-04,Barbell Row,Back,70,kg,8,,,,
            2024-03-06,Barbell Row,Back,70,kg,8,,,,
        """.trimIndent()

        vm.onFileChosen("onemonth.csv")
        val ready = await<ImportUiState.Ready>()
        assertEquals("FitNotes", ready.format)
        assertEquals("March 2024", ready.span)
    }
}
