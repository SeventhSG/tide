package app.tide

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.ask.ModelInstaller
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.ProgressionRule
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
 * Ask's chat, before any model exists.
 *
 * Every reply here comes from [app.tide.ask.TrainingTools], so these tests
 * are really about routing: does the right question reach the right tool,
 * and does an unrecognised one say plainly that it cannot hold a real
 * conversation, rather than guessing at an answer.
 */
@RunWith(RobolectricTestRunner::class)
class AskChatTest {

    private lateinit var db: TideDatabase
    private lateinit var repo: TrainingRepository
    private lateinit var scope: CoroutineScope
    private lateinit var vm: AskViewModel

    private val squat = ExerciseEntity(
        id = "squat", name = "Back squat", primaryMuscle = Muscle.Quads,
        equipment = Equipment.Barbell,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, TideDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = TrainingRepository(
            exercises = db.exercises(),
            routines = db.routines(),
            sessions = db.sessions(),
            state = db.exerciseState(),
            bodyWeight = db.bodyWeight(),
        )
        runBlocking { db.exercises().upsert(squat) }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        vm = AskViewModel(ModelInstaller(context), scope, repository = repo)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
        db.close()
    }

    /**
     * A reply reads the database through Room's own executor, not the test
     * thread, so this waits for it rather than asserting the instant [send]
     * returns, the same way every other viewmodel test here does.
     */
    private fun awaitMessages(count: Int): List<AskUiState.ChatMessage> {
        val deadline = System.currentTimeMillis() + 2_000
        var last = vm.state.value.messages
        while (System.currentTimeMillis() < deadline) {
            last = vm.state.value.messages
            if (last.size >= count) return last
            Thread.sleep(5)
        }
        throw AssertionError("expected $count messages, found ${last.size}: $last")
    }

    @Test
    fun `sending a message shows both sides of the exchange`() {
        vm.onSend("How many sessions this week?")
        val messages = awaitMessages(2)
        assertTrue(messages[0].fromUser)
        assertEquals("How many sessions this week?", messages[0].text)
        assertTrue(!messages[1].fromUser)
        assertEquals("Nothing logged this week yet.", messages[1].text)
    }

    @Test
    fun `a question about a specific lift routes to that lift's history`() = runBlocking {
        val s = repo.startSession()
        repo.logSet(s, squat.id, loadKg = 100.0, reps = 5)
        repo.finishSession(s, ProgressionRule.Linear(2.5))

        vm.onSend("last time I did squat")
        assertEquals("Back squat, last time: 100 kg x 5 for 1 set.", awaitMessages(2).last().text)
    }

    @Test
    fun `an unrecognised question says plainly that it cannot really converse`() {
        vm.onSend("what is the meaning of life")
        assertEquals(
            "There is no real conversation yet, only direct lookups. Try asking about " +
                "sessions this week, volume, muscle balance, or \"last time I did <exercise>\".",
            awaitMessages(2).last().text,
        )
    }

    @Test
    fun `blank input sends nothing`() {
        vm.onSend("   ")
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun `removing the model keeps the conversation`() {
        vm.onSend("volume")
        assertEquals(2, awaitMessages(2).size)

        vm.onRemove()
        assertEquals("removing the weights answers no question that was asked", 2, vm.state.value.messages.size)
    }
}
