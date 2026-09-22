package app.tide.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun setKindToString(v: SetKind): String = v.name
    @TypeConverter fun stringToSetKind(v: String): SetKind = SetKind.valueOf(v)

    @TypeConverter fun muscleToString(v: Muscle): String = v.name
    @TypeConverter fun stringToMuscle(v: String): Muscle = Muscle.valueOf(v)

    @TypeConverter fun equipmentToString(v: Equipment): String = v.name
    @TypeConverter fun stringToEquipment(v: String): Equipment = Equipment.valueOf(v)

    @TypeConverter fun scheduleKindToString(v: ScheduleKind): String = v.name
    @TypeConverter fun stringToScheduleKind(v: String): ScheduleKind = ScheduleKind.valueOf(v)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    /** The whole library at once, for the importer to index before matching. */
    @Query("SELECT * FROM exercise ORDER BY name")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun byId(id: String): ExerciseEntity?

    /** Used by the importers to match a name from a CSV against the library. */
    @Query("SELECT * FROM exercise WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun byName(name: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE name LIKE '%' || :q || '%' ORDER BY isCustom, name LIMIT 60")
    suspend fun search(q: String): List<ExerciseEntity>

    @Upsert suspend fun upsert(e: ExerciseEntity)
    @Upsert suspend fun upsertAll(e: List<ExerciseEntity>)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine WHERE archivedAt IS NULL ORDER BY createdAt")
    fun observeActive(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY dayIndex, orderInDay")
    suspend fun exercisesFor(routineId: String): List<RoutineExerciseEntity>

    @Upsert suspend fun upsert(r: RoutineEntity)
    @Upsert suspend fun upsertExercise(e: RoutineExerciseEntity)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM session ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 60): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun activeOrNull(): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    @Query("SELECT * FROM session WHERE startedAt BETWEEN :from AND :to ORDER BY startedAt")
    suspend fun between(from: Long, to: Long): List<SessionEntity>

    @Upsert suspend fun upsert(s: SessionEntity)

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId ORDER BY orderInSession")
    fun observeSets(sessionId: String): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId ORDER BY orderInSession")
    suspend fun setsFor(sessionId: String): List<WorkoutSetEntity>

    @Upsert suspend fun upsertSet(s: WorkoutSetEntity)

    @Query("DELETE FROM workout_set WHERE id = :id")
    suspend fun deleteSet(id: String)

    /**
     * The working sets of the last session that trained this exercise.
     *
     * Warm-ups and cardio are excluded in SQL rather than in Kotlin, because
     * every caller wants them excluded and the one that forgets would corrupt a
     * progression decision silently.
     */
    @Query(
        """
        SELECT ws.* FROM workout_set ws
        WHERE ws.exerciseId = :exerciseId
          AND ws.kind NOT IN ('WarmUp', 'Cardio')
          AND ws.sessionId = (
            SELECT s.id FROM session s
            JOIN workout_set w ON w.sessionId = s.id
            WHERE w.exerciseId = :exerciseId AND w.kind NOT IN ('WarmUp', 'Cardio')
              AND s.endedAt IS NOT NULL
            ORDER BY s.startedAt DESC LIMIT 1
          )
        ORDER BY ws.orderInSession
        """,
    )
    suspend fun lastWorkingSets(exerciseId: String): List<WorkoutSetEntity>

    /**
     * Working sets in a window, carrying the muscles they trained.
     *
     * Joined in SQL rather than fetched and zipped in Kotlin, and filtered
     * here too, so no caller can forget the warm-up exclusion and quietly
     * inflate every muscle on the map.
     */
    @Query(
        """
        SELECT ws.exerciseId AS exerciseId, ws.loadKg AS loadKg, ws.reps AS reps,
               ws.durationSec AS durationSec, ws.completedAt AS completedAt,
               e.primaryMuscle AS primaryMuscle, e.secondaryMuscles AS secondaryMuscles
        FROM workout_set ws
        JOIN exercise e ON e.id = ws.exerciseId
        WHERE ws.completedAt BETWEEN :from AND :to
          AND ws.kind NOT IN ('WarmUp', 'Cardio')
        ORDER BY ws.completedAt
        """,
    )
    suspend fun workingSetsBetween(from: Long, to: Long): List<SetWithMuscles>

    /**
     * The best estimated 1RM ever seen per exercise, computed from the sets.
     *
     * Epley inline, because `exercise_state` is only written when a session is
     * finished through the app, and an imported history never passes through
     * that path. Reading it from the sets themselves is the only version that
     * is right for both.
     */
    @Query(
        """
        SELECT exerciseId, MAX(loadKg * (1.0 + reps / 30.0)) AS estimated1rmKg
        FROM workout_set
        WHERE kind NOT IN ('WarmUp', 'Cardio')
          AND loadKg IS NOT NULL AND loadKg > 0 AND reps IS NOT NULL AND reps > 0
        GROUP BY exerciseId
        """,
    )
    suspend fun bestEstimated1rms(): List<Estimated1rm>

    /** The most recent working set per exercise, for "days since trained". */
    @Query(
        """
        SELECT exerciseId, MAX(completedAt) AS lastAt
        FROM workout_set
        WHERE kind NOT IN ('WarmUp', 'Cardio')
        GROUP BY exerciseId
        """,
    )
    suspend fun lastTrainedPerExercise(): List<LastTrained>

    /** Total volume in a window. Warm-ups excluded, cardio has no load. */
    @Query(
        """
        SELECT COALESCE(SUM(ws.loadKg * ws.reps), 0) FROM workout_set ws
        JOIN session s ON s.id = ws.sessionId
        WHERE s.startedAt BETWEEN :from AND :to
          AND ws.kind NOT IN ('WarmUp', 'Cardio')
          AND ws.loadKg IS NOT NULL AND ws.reps IS NOT NULL
        """,
    )
    suspend fun volumeBetween(from: Long, to: Long): Double
}

@Dao
interface ExerciseStateDao {
    @Query("SELECT * FROM exercise_state WHERE exerciseId = :id")
    suspend fun byExercise(id: String): ExerciseStateEntity?

    @Upsert suspend fun upsert(s: ExerciseStateEntity)
}

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 180): Flow<List<BodyWeightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(w: BodyWeightEntity)
}

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedule_rule WHERE archivedAt IS NULL ORDER BY createdAt")
    fun observeActive(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rule WHERE archivedAt IS NULL ORDER BY createdAt")
    suspend fun active(): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rule WHERE id = :id")
    suspend fun byId(id: String): ScheduleRuleEntity?

    @Upsert suspend fun upsert(rule: ScheduleRuleEntity)

    @Query("UPDATE schedule_rule SET archivedAt = :at WHERE id = :id")
    suspend fun archive(id: String, at: Long)

    @Query("SELECT * FROM skipped_occurrence WHERE ruleId = :ruleId")
    suspend fun skipsFor(ruleId: String): List<SkippedOccurrenceEntity>

    @Query("SELECT * FROM skipped_occurrence")
    suspend fun allSkips(): List<SkippedOccurrenceEntity>

    @Upsert suspend fun skip(occurrence: SkippedOccurrenceEntity)

    @Query(
        """
        DELETE FROM skipped_occurrence
        WHERE ruleId = :ruleId AND dueEpochDay = :dueEpochDay AND occurrenceIndex = :index
        """,
    )
    suspend fun unskip(ruleId: String, dueEpochDay: Long, index: Int)

    /**
     * The instants this rule's thing actually happened, which is what the
     * reconciler resolves against.
     *
     * Returned as timestamps and turned into local dates in Kotlin, not bucketed
     * into days here. Day bucketing in SQL needs a fixed offset, and a fixed
     * offset is wrong twice a year: a Sunday session logged the weekend the
     * clocks change would land on the wrong day and read as a missed week.
     *
     * Training evidence is a finished session. An open session is not evidence
     * yet: you are in the middle of it, and counting it would tick the box
     * before the work was done.
     */
    @Query(
        """
        SELECT startedAt FROM session
        WHERE endedAt IS NOT NULL AND startedAt BETWEEN :from AND :to
        ORDER BY startedAt
        """,
    )
    suspend fun trainingEvidenceAt(from: Long, to: Long): List<Long>

    @Query("SELECT at FROM body_weight WHERE at BETWEEN :from AND :to ORDER BY at")
    suspend fun bodyWeightEvidenceAt(from: Long, to: Long): List<Long>
}

@Dao
interface NotificationLedgerDao {

    @Query(
        """
        SELECT MAX(postedAt) FROM notification_ledger
        WHERE notificationKey = :key
        """,
    )
    suspend fun lastPostedAt(key: String): Long?

    @Query("SELECT * FROM notification_ledger WHERE postedAt >= :from ORDER BY postedAt DESC")
    suspend fun since(from: Long): List<NotificationLedgerEntity>

    @Query("SELECT * FROM notification_ledger ORDER BY postedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationLedgerEntity>>

    @Upsert suspend fun record(entry: NotificationLedgerEntity)

    /** Keeps the record from growing without bound. */
    @Query("DELETE FROM notification_ledger WHERE postedAt < :before")
    suspend fun prune(before: Long)
}

/**
 * Version 2 adds the schedule.
 *
 * Written by hand and tested, because this database holds the only copy of the
 * data and there is no server to restore it from. Both statements are pure
 * additions: nothing existing is touched, so a failure here cannot cost a
 * training history.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `schedule_rule` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `recurrence` TEXT NOT NULL,
                `anchorEpochDay` INTEGER NOT NULL,
                `untilEpochDay` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `archivedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_schedule_rule_kind` ON `schedule_rule` (`kind`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `skipped_occurrence` (
                `ruleId` TEXT NOT NULL,
                `dueEpochDay` INTEGER NOT NULL,
                `occurrenceIndex` INTEGER NOT NULL,
                `skippedAt` INTEGER NOT NULL,
                `note` TEXT,
                PRIMARY KEY(`ruleId`, `dueEpochDay`, `occurrenceIndex`),
                FOREIGN KEY(`ruleId`) REFERENCES `schedule_rule`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

/**
 * Version 3 adds the notification ledger.
 *
 * A pure addition again, and tested the same way. Nothing existing is touched.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notification_ledger` (
                `notificationKey` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `tier` TEXT NOT NULL,
                `postedAt` INTEGER NOT NULL,
                `inDigest` INTEGER NOT NULL,
                PRIMARY KEY(`notificationKey`, `postedAt`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_ledger_notificationKey` " +
                "ON `notification_ledger` (`notificationKey`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_notification_ledger_postedAt` " +
                "ON `notification_ledger` (`postedAt`)",
        )
    }
}

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        SessionEntity::class,
        WorkoutSetEntity::class,
        BodyWeightEntity::class,
        ExerciseStateEntity::class,
        ScheduleRuleEntity::class,
        SkippedOccurrenceEntity::class,
        NotificationLedgerEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TideDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun routines(): RoutineDao
    abstract fun sessions(): SessionDao
    abstract fun exerciseState(): ExerciseStateDao
    abstract fun bodyWeight(): BodyWeightDao
    abstract fun schedule(): ScheduleDao
    abstract fun notificationLedger(): NotificationLedgerDao

    companion object {
        const val NAME = "tide.db"
    }
}
