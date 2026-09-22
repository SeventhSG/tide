package app.tide.core.data

import android.content.Context
import androidx.room.Room
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.MIGRATION_1_2
import app.tide.core.data.db.MIGRATION_2_3
import app.tide.core.data.db.TideDatabase
import app.tide.core.data.importer.TrainingImporter
import app.tide.core.data.schedule.ScheduleRepository
import app.tide.core.data.training.ProgressionRule
import app.tide.core.data.training.RuleCodec
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app's object graph, by hand.
 *
 * No dependency injection framework. There is one database, one repository and
 * one process, and a DI container would be ceremony around a single field. If
 * this grows past a handful of dependencies it should become Hilt, but adding
 * it now would be building for an app that does not exist yet.
 */
object Tide {

    @Volatile private var database: TideDatabase? = null
    @Volatile private var repository: TrainingRepository? = null
    @Volatile private var scheduleRepository: ScheduleRepository? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun db(context: Context): TideDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                TideDatabase::class.java,
                TideDatabase.NAME,
            )
                // No destructive fallback, ever. This database is the only copy
                // of the data and there is no server to restore it from, so a
                // missing migration must fail loudly rather than wipe a year of
                // training.
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also {
                    database = it
                    scope.launch { seedIfEmpty(it) }
                }
        }

    fun training(context: Context): TrainingRepository =
        repository ?: synchronized(this) {
            repository ?: db(context).let { d ->
                TrainingRepository(
                    exercises = d.exercises(),
                    routines = d.routines(),
                    sessions = d.sessions(),
                    state = d.exerciseState(),
                    bodyWeight = d.bodyWeight(),
                ).also { repository = it }
            }
        }

    fun schedule(context: Context): ScheduleRepository =
        scheduleRepository ?: synchronized(this) {
            scheduleRepository ?: ScheduleRepository(db(context).schedule())
                .also { scheduleRepository = it }
        }

    /**
     * Not cached, unlike the repository. An importer holds no state between
     * runs, and one is built for a file and then finished with.
     */
    fun importer(context: Context): TrainingImporter =
        db(context).let { TrainingImporter(exercises = it.exercises(), sessions = it.sessions()) }

    private suspend fun seedIfEmpty(db: TideDatabase) {
        if (db.exercises().byName("Back squat") != null) return
        db.exercises().upsertAll(StarterLibrary.exercises)
    }
}

/**
 * A starter exercise library.
 *
 * Deliberately small. The full ExerciseDB set is roughly 1,300 entries and will
 * be bundled as a seeded asset later; this is enough to train on from the first
 * launch without a browsing screen existing yet.
 *
 * Default progression rules are set per exercise because the right rule depends
 * on the lift: compound barbell work progresses linearly, accessories respond
 * better to a rep range, and bodyweight work cannot be loaded in small steps.
 */
private object StarterLibrary {

    private val linear = RuleCodec.encode(ProgressionRule.Linear(2.5))
    private val linearBig = RuleCodec.encode(ProgressionRule.Linear(5.0))
    private val greyskull = RuleCodec.encode(ProgressionRule.GreyskullLp(2.5))
    private val range = RuleCodec.encode(ProgressionRule.DoubleProgression(8, 12, 2.5))
    private val rangeLow = RuleCodec.encode(ProgressionRule.DoubleProgression(6, 9, 2.5))
    private val bw = RuleCodec.encode(ProgressionRule.Bodyweight(12, 5))
    private val timed = RuleCodec.encode(ProgressionRule.Timed(5, 90))

    private fun e(
        id: String,
        name: String,
        muscle: Muscle,
        equipment: Equipment,
        rule: String,
        secondary: String = "",
        bodyweight: Boolean = false,
    ) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscle = muscle,
        secondaryMuscles = secondary,
        equipment = equipment,
        isBodyweight = bodyweight,
        progressionRule = rule,
    )

    val exercises: List<ExerciseEntity> = listOf(
        // Lower, barbell
        e("squat", "Back squat", Muscle.Quads, Equipment.Barbell, linearBig, "Glutes,LowerBack"),
        e("front-squat", "Front squat", Muscle.Quads, Equipment.Barbell, linear, "Abs"),
        e("deadlift", "Deadlift", Muscle.Hamstrings, Equipment.Barbell, linearBig, "Glutes,Back,Traps"),
        e("rdl", "Romanian deadlift", Muscle.Hamstrings, Equipment.Barbell, linear, "Glutes"),
        e("hip-thrust", "Hip thrust", Muscle.Glutes, Equipment.Barbell, linear, "Hamstrings"),
        e("bulgarian", "Bulgarian split squat", Muscle.Quads, Equipment.Dumbbell, range, "Glutes"),
        e("leg-press", "Leg press", Muscle.Quads, Equipment.Machine, range, "Glutes"),
        e("leg-curl", "Lying leg curl", Muscle.Hamstrings, Equipment.Machine, range),
        e("leg-ext", "Leg extension", Muscle.Quads, Equipment.Machine, range),
        e("calf-raise", "Standing calf raise", Muscle.Calves, Equipment.Machine, range),

        // Push
        e("bench", "Bench press", Muscle.Chest, Equipment.Barbell, greyskull, "Triceps,FrontDelts"),
        e("incline-bench", "Incline bench press", Muscle.Chest, Equipment.Barbell, linear, "FrontDelts"),
        e("db-bench", "Dumbbell bench press", Muscle.Chest, Equipment.Dumbbell, rangeLow, "Triceps"),
        e("ohp", "Overhead press", Muscle.FrontDelts, Equipment.Barbell, greyskull, "Triceps"),
        e("db-shoulder", "Dumbbell shoulder press", Muscle.FrontDelts, Equipment.Dumbbell, rangeLow),
        e("lateral-raise", "Lateral raise", Muscle.SideDelts, Equipment.Dumbbell, range),
        e("dip", "Dip", Muscle.Chest, Equipment.Bodyweight, bw, "Triceps", bodyweight = true),
        e("pushup", "Push-up", Muscle.Chest, Equipment.Bodyweight, bw, "Triceps", bodyweight = true),
        e("cable-fly", "Cable fly", Muscle.Chest, Equipment.Cable, range),
        e("triceps-pushdown", "Triceps pushdown", Muscle.Triceps, Equipment.Cable, range),
        e("skullcrusher", "Skullcrusher", Muscle.Triceps, Equipment.EzBar, range),

        // Pull
        e("row", "Barbell row", Muscle.Back, Equipment.Barbell, rangeLow, "Lats,Biceps"),
        e("pendlay", "Pendlay row", Muscle.Back, Equipment.Barbell, linear, "Lats"),
        e("db-row", "Dumbbell row", Muscle.Lats, Equipment.Dumbbell, range, "Biceps"),
        e("pullup", "Pull-up", Muscle.Lats, Equipment.Bodyweight, bw, "Biceps", bodyweight = true),
        e("chinup", "Chin-up", Muscle.Lats, Equipment.Bodyweight, bw, "Biceps", bodyweight = true),
        e("lat-pulldown", "Lat pulldown", Muscle.Lats, Equipment.Cable, range, "Biceps"),
        e("cable-row", "Seated cable row", Muscle.Back, Equipment.Cable, range, "Biceps"),
        e("face-pull", "Face pull", Muscle.RearDelts, Equipment.Cable, range, "Traps"),
        e("shrug", "Barbell shrug", Muscle.Traps, Equipment.Barbell, range),
        e("curl", "Barbell curl", Muscle.Biceps, Equipment.Barbell, range, "Forearms"),
        e("db-curl", "Dumbbell curl", Muscle.Biceps, Equipment.Dumbbell, range),
        e("hammer-curl", "Hammer curl", Muscle.Biceps, Equipment.Dumbbell, range, "Forearms"),

        // Core and carries
        e("plank", "Plank", Muscle.Abs, Equipment.Bodyweight, timed, "Obliques", bodyweight = true),
        e("hanging-leg-raise", "Hanging leg raise", Muscle.Abs, Equipment.Bodyweight, bw, "", true),
        e("cable-crunch", "Cable crunch", Muscle.Abs, Equipment.Cable, range),
        e("farmer", "Farmer's walk", Muscle.Forearms, Equipment.Dumbbell, timed, "Traps,Abs"),
        e("back-ext", "Back extension", Muscle.LowerBack, Equipment.Bodyweight, bw, "Glutes", true),
    )
}
