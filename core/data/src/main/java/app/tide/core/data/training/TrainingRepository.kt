package app.tide.core.data.training

import app.tide.core.data.analysis.MuscleMap
import app.tide.core.data.analysis.MuscleReading
import app.tide.core.data.db.BodyWeightDao
import app.tide.core.data.db.ExerciseDao
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.ExerciseStateDao
import app.tide.core.data.db.ExerciseStateEntity
import app.tide.core.data.db.PlanMode
import app.tide.core.data.db.RoutineDao
import app.tide.core.data.db.RoutineDayLabelEntity
import app.tide.core.data.db.RoutineEntity
import app.tide.core.data.db.RoutineExerciseEntity
import app.tide.core.data.db.SessionDao
import app.tide.core.data.db.SessionEntity
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
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
    private val zone: ZoneId = ZoneId.systemDefault(),
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

    /** The most recent sessions, open or finished, newest first. */
    suspend fun recentSessions(): List<SessionEntity> = sessions.observeRecent().first()

    suspend fun activeSession(): SessionEntity? = sessions.activeOrNull()

    /** Every session that started in a window, for the calendar. */
    suspend fun sessionsBetween(from: Long, to: Long): List<SessionEntity> =
        sessions.between(from, to)

    /**
     * What was trained in these sessions, one line per exercise.
     *
     * Working sets only, so a day of warm-ups reads as a day of warm-ups. The
     * top set is the heaviest, or the longest set of reps when nothing was
     * loaded, because that is what anyone looking back wants to see first.
     */
    suspend fun sessionSummaries(sessionIds: List<String>): List<SessionExerciseSummary> {
        val sets = sessionIds
            .flatMap { sessions.setsFor(it) }
            .filter { it.countsTowardProgression }
        return sets.groupBy { it.exerciseId }
            .map { (id, rows) ->
                val top = rows.filter { it.loadKg != null }.maxByOrNull { it.loadKg!! }
                SessionExerciseSummary(
                    exerciseId = id,
                    exerciseName = exercises.byId(id)?.name ?: id,
                    workingSets = rows.size,
                    topLoadKg = top?.loadKg,
                    topReps = top?.reps ?: rows.mapNotNull { it.reps }.maxOrNull(),
                )
            }
            .sortedBy { it.exerciseName }
    }

    /** Total load times reps in a window. Warm-ups and cardio excluded in SQL. */
    suspend fun volumeBetween(from: Long, to: Long): Double = sessions.volumeBetween(from, to)

    /** How many sets in a window could move a target. Warm-ups are not among them. */
    suspend fun workingSetCountBetween(from: Long, to: Long): Int =
        sessions.workingSetsBetween(from, to).size

    // --- sessions ---------------------------------------------------------

    /**
     * Starts a session, or returns the one already open.
     *
     * Only one session can be open at a time. Resuming beats silently starting a
     * second one and splitting a workout across two rows, which is the kind of
     * corruption you only notice a month later.
     *
     * With no routine or day given, this reaches for today's plan itself:
     * today's weekday in Fixed mode, the next-up slot in Rotation. A caller
     * that already knows which day it means still wins, so nothing here
     * overrides an explicit choice.
     */
    suspend fun startSession(routineId: String? = null, dayIndex: Int? = null): String {
        sessions.activeOrNull()?.let { return it.id }
        var resolvedRoutineId = routineId
        var resolvedDayIndex = dayIndex
        if (resolvedRoutineId == null && resolvedDayIndex == null) {
            plannedToday()?.let {
                resolvedRoutineId = plan().id
                resolvedDayIndex = it.dayIndex
            }
        }
        val id = newId()
        sessions.upsert(
            SessionEntity(
                id = id,
                routineId = resolvedRoutineId,
                dayIndex = resolvedDayIndex,
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

    // --- the week's plan --------------------------------------------------

    /**
     * The plan, created on first use.
     *
     * One routine, seven days, because a person has one week. Several routines
     * is a real thing people want eventually and it is not what makes the first
     * version useful: what makes it useful is that Monday knows what Monday is.
     */
    suspend fun plan(): RoutineEntity {
        routines.active().firstOrNull()?.let { return it }
        val routine = RoutineEntity(
            id = newId(),
            name = "My week",
            defaultProgressionRule = RuleCodec.encode(ProgressionRule.Linear()),
            createdAt = now(),
        )
        routines.upsert(routine)
        return routine
    }

    suspend fun planMode(): PlanMode = plan().planMode

    /**
     * Switches Fixed and Rotation.
     *
     * The two modes give `dayIndex` a different meaning, a weekday against a
     * rotation position, so there is nothing honest to carry across: switching
     * clears every planned exercise and rotation label rather than guessing
     * that Monday meant slot 0.
     */
    suspend fun setPlanMode(mode: PlanMode) {
        val routine = plan()
        if (routine.planMode == mode) return
        routines.deleteAllExercises(routine.id)
        routines.deleteAllDayLabels(routine.id)
        routines.upsert(routine.copy(planMode = mode))
    }

    /** Rotation slot labels, by dayIndex. Empty outside Rotation mode. */
    suspend fun planDayLabels(): Map<Int, String> {
        val routine = plan()
        if (routine.planMode != PlanMode.Rotation) return emptyMap()
        return routines.dayLabelsFor(routine.id).associate { it.dayIndex to it.label }
    }

    /**
     * Adds a new rotation slot after every existing one and names it.
     *
     * Rotation-only. There is no unnamed slot to create ahead of time: naming
     * it is how it comes to exist, the same way a Fixed day exists only once
     * it is actually planned.
     */
    suspend fun addDay(label: String): Int {
        val routine = plan()
        val nextIndex = (routines.dayLabelsFor(routine.id).maxOfOrNull { it.dayIndex } ?: -1) + 1
        routines.upsertDayLabel(RoutineDayLabelEntity(routine.id, nextIndex, label))
        return nextIndex
    }

    suspend fun renameDay(dayIndex: Int, label: String) {
        val routine = plan()
        routines.upsertDayLabel(RoutineDayLabelEntity(routine.id, dayIndex, label))
    }

    /**
     * Deletes a rotation slot and everything planned in it, then closes the
     * gap: every later slot moves down by one, so the rotation stays
     * consecutive rather than carrying the hole a deleted slot left behind.
     */
    suspend fun removeDay(dayIndex: Int) {
        val routine = plan()
        val labels = routines.dayLabelsFor(routine.id).sortedBy { it.dayIndex }
        val allExercises = routines.exercisesFor(routine.id)

        allExercises.filter { it.dayIndex == dayIndex }.forEach { routines.deleteExercise(it.id) }
        routines.deleteDayLabel(routine.id, dayIndex)

        labels.filter { it.dayIndex > dayIndex }.forEach { label ->
            routines.deleteDayLabel(routine.id, label.dayIndex)
            routines.upsertDayLabel(label.copy(dayIndex = label.dayIndex - 1))
        }
        allExercises.filter { it.dayIndex > dayIndex }.forEach { row ->
            routines.upsertExercise(row.copy(dayIndex = row.dayIndex - 1))
        }
    }

    /**
     * Swaps two neighbouring rotation slots: their labels and every exercise
     * planned in them. Exercises keep their order inside their own slot; only
     * the slot's place in the rotation moves.
     */
    suspend fun moveDay(dayIndex: Int, up: Boolean) {
        val routine = plan()
        val labels = routines.dayLabelsFor(routine.id).associateBy { it.dayIndex }
        val here = labels[dayIndex] ?: return
        val targetIndex = if (up) dayIndex - 1 else dayIndex + 1
        val there = labels[targetIndex] ?: return

        routines.upsertDayLabel(here.copy(dayIndex = targetIndex))
        routines.upsertDayLabel(there.copy(dayIndex = dayIndex))

        routines.exercisesFor(routine.id).forEach { row ->
            when (row.dayIndex) {
                dayIndex -> routines.upsertExercise(row.copy(dayIndex = targetIndex))
                targetIndex -> routines.upsertExercise(row.copy(dayIndex = dayIndex))
            }
        }
    }

    /** Every planned exercise, by day, in the order they are meant to be done. */
    suspend fun planDays(): Map<Int, List<PlannedExercise>> {
        val routine = plan()
        val library = exercises.all().associateBy { it.id }
        return routines.exercisesFor(routine.id)
            .groupBy { it.dayIndex }
            .mapValues { (_, rows) ->
                rows.sortedBy { it.orderInDay }.map { row ->
                    PlannedExercise(
                        id = row.id,
                        exerciseId = row.exerciseId,
                        name = library[row.exerciseId]?.name ?: row.exerciseId,
                        equipment = library[row.exerciseId]?.equipment,
                        primaryMuscle = library[row.exerciseId]?.primaryMuscle,
                        orderInDay = row.orderInDay,
                        targetSets = row.targetSets,
                        targetReps = row.targetReps,
                    )
                }
            }
    }

    /** Appends to the end of a day. New work goes last until it is moved. */
    suspend fun addToPlan(dayIndex: Int, exerciseId: String, sets: Int = 3, reps: Int = 5) {
        val routine = plan()
        val existing = routines.exercisesFor(routine.id).filter { it.dayIndex == dayIndex }
        routines.upsertExercise(
            RoutineExerciseEntity(
                id = newId(),
                routineId = routine.id,
                exerciseId = exerciseId,
                dayIndex = dayIndex,
                orderInDay = (existing.maxOfOrNull { it.orderInDay } ?: -1) + 1,
                targetSets = sets,
                targetReps = reps,
            ),
        )
    }

    suspend fun removeFromPlan(plannedId: String) = routines.deleteExercise(plannedId)

    /**
     * Moves one exercise up or down inside its day.
     *
     * The order is rewritten for the whole day rather than by swapping two
     * numbers, so a plan that was already out of order because of a deletion
     * comes back consecutive instead of preserving the gap.
     */
    suspend fun movePlanned(plannedId: String, up: Boolean) {
        val routine = plan()
        val all = routines.exercisesFor(routine.id)
        val row = all.firstOrNull { it.id == plannedId } ?: return
        val day = all.filter { it.dayIndex == row.dayIndex }.sortedBy { it.orderInDay }.toMutableList()
        val index = day.indexOfFirst { it.id == plannedId }
        val target = if (up) index - 1 else index + 1
        if (target !in day.indices) return

        day.add(target, day.removeAt(index))
        day.forEachIndexed { i, entry ->
            if (entry.orderInDay != i) routines.upsertExercise(entry.copy(orderInDay = i))
        }
    }

    /** Moves an exercise to another day, landing at the end of it. */
    suspend fun movePlannedToDay(plannedId: String, dayIndex: Int) {
        val routine = plan()
        val all = routines.exercisesFor(routine.id)
        val row = all.firstOrNull { it.id == plannedId } ?: return
        val target = all.filter { it.dayIndex == dayIndex }
        routines.upsertExercise(
            row.copy(
                dayIndex = dayIndex,
                orderInDay = (target.maxOfOrNull { it.orderInDay } ?: -1) + 1,
            ),
        )
    }

    /**
     * What is planned for the slot that matters right now, if anything.
     *
     * Fixed: today's weekday, when it has anything planned. Rotation: the slot
     * after wherever the rotation last left off. Null either way when there is
     * nothing to say, same as everything else this app declines to invent.
     */
    suspend fun plannedToday(): PlannedDay? {
        val routine = plan()
        val dayIndex = when (routine.planMode) {
            PlanMode.Fixed -> Instant.ofEpochMilli(now()).atZone(zone).dayOfWeek.value - 1
            PlanMode.Rotation -> nextRotationDayIndex(routine.id) ?: return null
        }
        val exercises = planDays()[dayIndex].orEmpty()
        if (exercises.isEmpty()) return null
        return PlannedDay(dayIndex, exercises)
    }

    /**
     * The slot after whichever one the most recently finished session on this
     * routine was tagged with, wrapping around. Slot 0 when there is no prior
     * session, or its slot was since deleted: a rotation always has somewhere
     * to start.
     */
    private suspend fun nextRotationDayIndex(routineId: String): Int? {
        val order = routines.dayLabelsFor(routineId).map { it.dayIndex }.sorted()
        if (order.isEmpty()) return null
        val last = sessions.lastForRoutine(routineId)?.dayIndex
        val position = last?.let { order.indexOf(it) } ?: -1
        return if (position < 0) order.first() else order[(position + 1) % order.size]
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

/** The slot that matters right now, and what is planned in it. */
data class PlannedDay(
    val dayIndex: Int,
    val exercises: List<PlannedExercise>,
)

/** One exercise as it sits in the week's plan. */
data class PlannedExercise(
    val id: String,
    val exerciseId: String,
    val name: String,
    val equipment: app.tide.core.data.db.Equipment?,
    val primaryMuscle: app.tide.core.data.db.Muscle?,
    val orderInDay: Int,
    val targetSets: Int,
    val targetReps: Int,
)

/** One exercise as it appears looking back at a day. */
data class SessionExerciseSummary(
    val exerciseId: String,
    val exerciseName: String,
    val workingSets: Int,
    val topLoadKg: Double?,
    val topReps: Int?,
)

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
