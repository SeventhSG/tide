package app.tide.core.data.schedule

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.ScheduleKind
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.TrainingRepository
import app.tide.core.schedule.OccurrenceState
import app.tide.core.schedule.Recurrence
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * The schedule resolved against real training records.
 *
 * The point of the whole module is in the second test: a session logged
 * through the app satisfies a schedule rule without anybody being asked to
 * confirm they did the thing the database already recorded.
 */
@RunWith(RobolectricTestRunner::class)
class ScheduleRepositoryTest {

    private lateinit var db: TideDatabase
    private lateinit var schedule: ScheduleRepository
    private lateinit var training: TrainingRepository

    private val utc = ZoneId.of("UTC")
    private val monday = LocalDate.of(2025, 9, 1)
    private val sunday = monday.plusDays(6)

    private var today = monday
    private var clock = monday.atStartOfDay(utc).toInstant().toEpochMilli()
    private var ids = 0

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TideDatabase::class.java,
        ).allowMainThreadQueries().build()

        db.exercises().upsert(ExerciseEntity("squat", "Back squat", Muscle.Quads))

        schedule = ScheduleRepository(
            schedule = db.schedule(),
            zone = utc,
            today = { today },
            newId = { "rule-${ids++}" },
            now = { clock },
        )
        training = TrainingRepository(
            exercises = db.exercises(),
            routines = db.routines(),
            sessions = db.sessions(),
            state = db.exerciseState(),
            bodyWeight = db.bodyWeight(),
            now = { clock },
            newId = { "t-${ids++}" },
        )
    }

    @After
    fun tearDown() = db.close()

    /** Trains and finishes a session on [day]. */
    private suspend fun trainOn(day: LocalDate) {
        clock = day.atStartOfDay(utc).toInstant().toEpochMilli()
        val s = training.startSession()
        training.logSet(s, "squat", loadKg = 100.0, reps = 5)
        training.finishSession(s, ProgressionRule.Linear(2.5))
    }

    @Test
    fun `a rule with nothing done yet is due, not missed`() = runTest {
        schedule.addRule("Train", ScheduleKind.Training, Recurrence.TimesPerWeek(3), anchor = monday)

        val week = schedule.thisWeek(monday)
        assertEquals(3, week.size)
        assertTrue(
            "the week has only just started",
            week.all { it.state == OccurrenceState.Due },
        )
    }

    @Test
    fun `a logged session resolves the schedule without anyone being asked`() = runTest {
        schedule.addRule("Train", ScheduleKind.Training, Recurrence.TimesPerWeek(3), anchor = monday)
        trainOn(monday.plusDays(1))
        today = monday.plusDays(1)

        val week = schedule.thisWeek(monday)
        assertEquals(
            "the database already knew, so nothing should have to be confirmed",
            1,
            week.count { it.state == OccurrenceState.Done },
        )
        assertEquals(2, week.count { it.state == OccurrenceState.Due })
    }

    @Test
    fun `an unfinished session is not evidence yet`() = runTest {
        schedule.addRule("Train", ScheduleKind.Training, Recurrence.TimesPerWeek(1), anchor = monday)
        clock = monday.atStartOfDay(utc).toInstant().toEpochMilli()
        training.startSession()  // opened, never finished

        val week = schedule.thisWeek(monday)
        assertEquals(
            "being midway through a session is not the same as having done it",
            OccurrenceState.Due,
            week.single().state,
        )
    }

    @Test
    fun `training on any day satisfies a weekly quota`() = runTest {
        schedule.addRule("Train", ScheduleKind.Training, Recurrence.TimesPerWeek(2), anchor = monday)
        trainOn(monday.plusDays(1))
        trainOn(monday.plusDays(5))
        today = sunday

        val week = schedule.thisWeek(monday)
        assertTrue(
            "a Tuesday and Saturday lifter is not behind on anything",
            week.all { it.state == OccurrenceState.Done },
        )
    }

    @Test
    fun `a week that closed with nothing in it is missed`() = runTest {
        schedule.addRule("Train", ScheduleKind.Training, Recurrence.TimesPerWeek(2), anchor = monday)
        today = sunday.plusDays(1)

        val week = schedule.thisWeek(monday)
        assertTrue(week.all { it.state == OccurrenceState.Missed })
    }

    @Test
    fun `a skip survives the round trip and is not a miss`() = runTest {
        schedule.addRule(
            "Weigh in", ScheduleKind.BodyWeight,
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY)), anchor = monday,
        )
        val occurrence = schedule.thisWeek(monday).single()
        schedule.skip(occurrence.reference, note = "away")

        today = sunday.plusDays(1)
        val after = schedule.thisWeek(monday).single()
        assertEquals(OccurrenceState.Skipped, after.state)
    }

    @Test
    fun `unskipping puts it back`() = runTest {
        schedule.addRule(
            "Weigh in", ScheduleKind.BodyWeight,
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY)), anchor = monday,
        )
        val ref = schedule.thisWeek(monday).single().reference
        schedule.skip(ref)
        schedule.unskip(ref)

        assertEquals(OccurrenceState.Due, schedule.thisWeek(monday).single().state)
    }

    @Test
    fun `one session counts toward two different rules`() = runTest {
        // Both are real commitments and one session genuinely satisfies both.
        schedule.addRule("Train 3x", ScheduleKind.Training, Recurrence.TimesPerWeek(3), anchor = monday)
        schedule.addRule("Train at all", ScheduleKind.Training, Recurrence.TimesPerWeek(1), anchor = monday)
        trainOn(monday)
        today = monday

        val byRule = schedule.thisWeek(monday).groupBy { it.occurrence.ruleId }
        assertEquals(1, byRule.getValue("rule-0").count { it.state == OccurrenceState.Done })
        assertEquals(1, byRule.getValue("rule-1").count { it.state == OccurrenceState.Done })
    }

    @Test
    fun `an archived rule stops appearing`() = runTest {
        val id = schedule.addRule(
            "Train", ScheduleKind.Training, Recurrence.TimesPerWeek(3), anchor = monday,
        )
        schedule.archiveRule(id)
        assertTrue(schedule.thisWeek(monday).isEmpty())
    }

    @Test
    fun `a corrupt recurrence costs one rule, not the screen`() = runTest {
        schedule.addRule("Good", ScheduleKind.Training, Recurrence.TimesPerWeek(1), anchor = monday)
        db.schedule().upsert(
            db.schedule().active().first().copy(id = "broken", recurrence = "Nonsense:!!"),
        )

        val week = schedule.thisWeek(monday)
        assertEquals("the good rule still resolves", 1, week.size)
    }

    @Test
    fun `money and manual rules read no evidence rather than inventing some`() = runTest {
        schedule.addRule(
            "Rent", ScheduleKind.Money,
            Recurrence.MonthlyByDay(1), anchor = monday,
        )
        trainOn(monday)
        today = monday

        val out = schedule.outlook(monday, monday)
        assertEquals(
            "a training session does not pay the rent",
            OccurrenceState.Due,
            out.single().state,
        )
    }

    @Test
    fun `outstanding leaves out what is already done`() = runTest {
        schedule.addRule("Train", ScheduleKind.Training, Recurrence.TimesPerWeek(2), anchor = monday)
        trainOn(monday)
        today = monday

        val outstanding = schedule.outstanding(monday, sunday)
        assertEquals(1, outstanding.size)
        assertEquals(OccurrenceState.Due, outstanding.single().state)
    }
}
