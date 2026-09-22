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
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun setKindToString(v: SetKind): String = v.name
    @TypeConverter fun stringToSetKind(v: String): SetKind = SetKind.valueOf(v)

    @TypeConverter fun muscleToString(v: Muscle): String = v.name
    @TypeConverter fun stringToMuscle(v: String): Muscle = Muscle.valueOf(v)

    @TypeConverter fun equipmentToString(v: Equipment): String = v.name
    @TypeConverter fun stringToEquipment(v: String): Equipment = Equipment.valueOf(v)
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

@Database(
    entities = [
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        SessionEntity::class,
        WorkoutSetEntity::class,
        BodyWeightEntity::class,
        ExerciseStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TideDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun routines(): RoutineDao
    abstract fun sessions(): SessionDao
    abstract fun exerciseState(): ExerciseStateDao
    abstract fun bodyWeight(): BodyWeightDao

    companion object {
        const val NAME = "tide.db"
    }
}
