package app.tide.core.data.training

import app.tide.core.data.analysis.MuscleMap
import app.tide.core.data.analysis.MuscleReading
import app.tide.core.data.db.BodyWeightDao
import app.tide.core.data.db.ExerciseDao
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.ExerciseStateDao
import app.tide.core.data.db.ExerciseStateEntity
import app.tide.core.data.db.RoutineDao
import app.tide.core.data.db.SessionDao
import app.tide.core.data.db.SessionEntity
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Where the progression engine meets the database.
 *
 * The engine is pure and the DAOs are dumb. Everything that decides what happens
 * to your training lives here, in one place, so there is exactly one path a
 * logged set can take and one place to look when a number is wrong.
 */
class TrainingRepository(
    private val exercises: ExerciseDao,
    private val routines: RoutineDao,
    private val sessions: SessionDao,
    private val state: ExerciseStateDao,
    private val bodyWeight: BodyWeightDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeExercises(): Flow<List<ExerciseEntity>> = exercises.observeAll()
    fun observeActiveSession(): Flow<SessionEntity?> = sessions.observeActive()
    fun observeSets(sessionId: String): Flow<List<WorkoutSetEntity>> = sessions.observeSets(sessionId)
    fun observeRecentSessions() = sessions.observeRecent()

    suspend fun searchExercises(q: String) =
        if (q.isBlank()) emptyList() else exercises.search(q)

    suspend fun exerciseById(exerciseId: String): ExerciseEntity? = exercises.byId(exerciseId)

    /**
     * The library, filtered by what was typed. A blank query returns all of it.
     *
     * Filtering happens in SQL rather than over a list in a screen, because the
     * starter set is 38 and the library this is built for is about 1,300.
     */
    suspend fun library(query: String = ""): List<ExerciseEntity> =
        if (query.isBlank()) exercises.all() else exercises.search(query.trim())

    /** When each exercise was last worked, warm-ups excluded, as SQL decides. */
    suspend fun lastTrainedByExercise(): Map<String, Long> =
        sessions.lastTrainedPerExercise().associate { it.exerciseId to it.lastAt }

    // --- sessions ---------------------------------------------------------

    /**
     * Starts a session, or returns the one already open.
     *
     * Only one session can be open at a time. Resuming beats silently starting a
     * second one and splitting a workout across two rows, which is the kind of
     * corruption you only notice a month later.
     */
    suspend fun startSession(routineId: String? = null, dayIndex: Int? = null): String {
        sessions.activeOrNull()?.let { return it.id }
        val id = newId()
        sessions.upsert(
            SessionEntity(
                id = id,
                routineId = routineId,
                dayIndex = dayIndex,
                startedAt = now(),
            ),
        )
        return id
    }

    /**
     * Ends the session and runs progression for every exercise it touched.
     *
     * This is the moment the app earns its keep, so it happens once, here, and
     * not scattered across screens.
     *
     * Once is enforced, not assumed. A session that has already ended returns
     * nothing and changes nothing, because a double tap on Finish that ran the
     * engine twice would add the increment twice and prescribe a weight nobody
     * earned.
     *
     * A session with no sets in it is deleted rather than ended. Opening the
     * logger and backing out is not a workout, and keeping the row would show up
     * later as a session that happened.
     */
    suspend fun finishSession(sessionId: String, defaultRule: ProgressionRule): List<ExerciseProgression> {
        val session = sessions.byId(sessionId) ?: return emptyList()
        if (session.endedAt != null) return emptyList()
        val sets = sessions.setsFor(sessionId)
        if (sets.isEmpty()) {
            sessions.delete(sessionId)
            return emptyList()
        }
        sessions.upsert(session.copy(endedAt = now()))

        val results = mutableListOf<ExerciseProgression>()
        sets.map { it.exerciseId }.distinct().forEach { exerciseId ->
            val result = applyProgression(exerciseId, sets, defaultRule)
            if (result != null) results += ExerciseProgression(exerciseId, result)
        }
        return results
    }

    // --- sets -------------------------------------------------------------

    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        kind: SetKind = SetKind.Standard,
        loadKg: Double? = null,
        reps: Int? = null,
        durationSec: Int? = null,
        rir: Int? = null,
        supersetGroup: Int? = null,
    ): String {
        val existing = sessions.setsFor(sessionId)
        val id = newId()
        sessions.upsertSet(
            WorkoutSetEntity(
                id = id,
                sessionId = sessionId,
                exerciseId = exerciseId,
                orderInSession = existing.size,
                kind = kind,
                loadKg = loadKg,
                reps = reps,
                durationSec = durationSec,
                rir = rir,
                supersetGroup = supersetGroup,
                completedAt = now(),
            ),
        )
        return id
    }

    suspend fun deleteSet(id: String) = sessions.deleteSet(id)

    // --- analysis ---------------------------------------------------------

    /**
     * The muscle map over a window ending now.
     *
     * The window bounds balance and fatigue only. "Days since trained" reads
     * the whole history, because a muscle you have not touched in five weeks
     * is exactly what the map exists to show, and a fortnight's window would
     * report it as never trained.
     */
    suspend fun muscleMap(
        windowDays: Int = 14,
        halfLifeHours: Double = MuscleMap.DEFAULT_HALF_LIFE_HOURS,
    ): List<MuscleReading> {
        val to = now()
        val from = to - windowDays * 86_400_000L
        return MuscleMap.analyse(
            sets = sessions.workingSetsBetween(from, to),
            library = exercises.all(),
            best1rmByExercise = sessions.bestEstimated1rms()
                .associate { it.exerciseId to it.estimated1rmKg },
            lastTrainedByExercise = sessions.lastTrainedPerExercise()
                .associate { it.exerciseId to it.lastAt },
            now = to,
            halfLifeHours = halfLifeHours,
        )
    }

    // --- progression ------------------------------------------------------

    /**
     * What to put in front of the user for this exercise right now.
     *
     * Falls back, in order, to stored state, then the last session's working
     * sets, then an empty prescription. It never invents a load: an exercise
     * with no history shows blank fields rather than a made-up number, because
     * a guessed starting weight is worse than no suggestion.
     */
    suspend fun prescriptionFor(exerciseId: String): Prescription? {
        state.byExercise(exerciseId)?.let {
            return Prescription(
                loadKg = it.nextLoadKg,
                sets = it.nextSets,
                reps = it.nextReps,
                repCeiling = it.nextRepCeiling,
                durationSec = it.nextDurationSec,
            )
        }
        val last = sessions.lastWorkingSets(exerciseId)
        if (last.isEmpty()) return null
        return Prescription(
            loadKg = last.mapNotNull { it.loadKg }.maxOrNull(),
            sets = last.size,
            reps = last.mapNotNull { it.reps }.minOrNull() ?: 0,
            durationSec = last.mapNotNull { it.durationSec }.minOrNull(),
        )
    }

    /**
     * What you actually did last time, regardless of what is prescribed next.
     *
     * Separate from [prescriptionFor], which prefers the stored decision and,
     * once one exists, never reaches session history at all. The logger's
     * "last time" reference needs the history itself, not the decision made
     * from it.
     */
    suspend fun lastPerformance(exerciseId: String): Prescription? {
        val last = sessions.lastWorkingSets(exerciseId)
        if (last.isEmpty()) return null
        return Prescription(
            loadKg = last.mapNotNull { it.loadKg }.maxOrNull(),
            sets = last.size,
            reps = last.mapNotNull { it.reps }.minOrNull() ?: 0,
            durationSec = last.mapNotNull { it.durationSec }.minOrNull(),
        )
    }

    private suspend fun applyProgression(
        exerciseId: String,
        sessionSets: List<WorkoutSetEntity>,
        defaultRule: ProgressionRule,
    ): ProgressionResult? {
        val exercise = exercises.byId(exerciseId) ?: return null
        val rule = RuleCodec.decode(exercise.progressionRule) ?: defaultRule

        // Warm-ups and cardio never count. This is the single most important
        // filter in the module.
        val working = sessionSets.filter {
            it.exerciseId == exerciseId && it.countsTowardProgression
        }
        if (working.isEmpty()) return null

        val current = prescriptionFor(exerciseId) ?: Prescription(
            loadKg = working.mapNotNull { it.loadKg }.maxOrNull(),
            sets = working.size,
            reps = working.mapNotNull { it.reps }.minOrNull() ?: 0,
        )
        val outcome = SessionOutcome(
            working.map { PerformedSet(it.reps ?: 0, it.loadKg, it.durationSec) },
        )
        val prior = state.byExercise(exerciseId)?.stalls ?: 0
        val result = ProgressionEngine.next(rule, current, outcome, prior)

        val best = working.mapNotNull { s ->
            val l = s.loadKg; val r = s.reps
            if (l != null && r != null && r > 0) OneRepMax.epley(l, r) else null
        }.maxOrNull()

        state.upsert(
            ExerciseStateEntity(
                exerciseId = exerciseId,
                nextLoadKg = result.next.loadKg,
                nextReps = result.next.reps,
                nextSets = result.next.sets,
                nextRepCeiling = result.next.repCeiling,
                nextDurationSec = result.next.durationSec,
                stalls = result.stalls,
                lastSessionAt = now(),
                bestEstimated1rmKg = maxOf(
                    best ?: 0.0,
                    state.byExercise(exerciseId)?.bestEstimated1rmKg ?: 0.0,
                ).takeIf { it > 0.0 },
            ),
        )
        return result
    }

}

/** The decision made for one exercise when a session finished. */
data class ExerciseProgression(
    val exerciseId: String,
    val result: ProgressionResult,
)

/**
 * Estimated one-rep max.
 *
 * Epley, because it is the one most lifters recognise and it is close enough in
 * the 1 to 10 rep range where almost all logged sets live. It is an estimate and
 * the app says so wherever it shows one: no app should print 137.4 kg and imply
 * it measured something.
 */
object OneRepMax {
    fun epley(loadKg: Double, reps: Int): Double {
        if (reps <= 0) return 0.0
        if (reps == 1) return loadKg
        return loadKg * (1.0 + reps / 30.0)
    }

    /** Display value. Whole kilos, because the precision is not real. */
    fun display(loadKg: Double, reps: Int): Int = epley(loadKg, reps).roundToInt()
}
