package app.tide.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The training schema.
 *
 * Shaped around two things the progression engine needs and most logging apps
 * do not persist: a **warm-up flag on every set**, without which every derived
 * number in the module is wrong, and a **stall count per exercise**, without
 * which a deload can never fire.
 *
 * Set taxonomy studied from openGym (AGPL-3.0) and reimplemented. It is more
 * complete than the obvious one, and the completeness is the point: a schema
 * that cannot express a drop set will have drop sets logged as lies.
 */

enum class SetKind {
    /** Reps at a load. The common case. */
    Standard,

    /** Excluded from estimated 1RM, from progression and from fatigue. */
    WarmUp,

    /** Planks, hangs, carries. Duration instead of reps. */
    Timed,

    /** Same exercise, reduced load, taken straight after the set before it. */
    DropSet,

    /** One set with a pause and a burst, logged as its own row. */
    RestPause,

    /** Lunges, single-arm rows. Reps are entered as a total and shown per side. */
    PerSide,

    /** Load optional. Progresses in reps to a ceiling, then in sets. */
    Bodyweight,

    /** Duration and speed instead of reps and load. */
    Cardio,
}

enum class Muscle {
    Chest, Back, Lats, Traps, FrontDelts, SideDelts, RearDelts,
    Biceps, Triceps, Forearms, Quads, Hamstrings, Glutes, Calves,
    Abs, Obliques, LowerBack, Adductors, Abductors, Neck, FullBody,
}

enum class Equipment {
    Barbell, Dumbbell, Machine, Cable, Bodyweight, Kettlebell,
    Band, Smith, EzBar, TrapBar, Other,
}

@Entity(tableName = "exercise", indices = [Index("name", unique = true)])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val primaryMuscle: Muscle,
    /** Comma separated. Room stores it flat; the repository exposes a list. */
    val secondaryMuscles: String = "",
    val equipment: Equipment = Equipment.Other,
    val isBodyweight: Boolean = false,
    /** True for anything the user added rather than the seeded library. */
    val isCustom: Boolean = false,
    /** Serialised ProgressionRule. Null means fall back to the routine default. */
    val progressionRule: String? = null,
    val notes: String? = null,
)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Serialised ProgressionRule used by any exercise without an override. */
    val defaultProgressionRule: String,
    /**
     * A deloaded routine is exempt from auto-progression and uses its own target
     * loads, so a planned light week does not read as a stall.
     */
    val isDeload: Boolean = false,
    val createdAt: Long,
    val archivedAt: Long? = null,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    /** Which day of the week's plan. Rescheduling a day never reorders the week. */
    val dayIndex: Int,
    val orderInDay: Int,
    val targetSets: Int,
    val targetReps: Int,
    val repCeiling: Int? = null,
    val targetLoadKg: Double? = null,
    val targetDurationSec: Int? = null,
    /** Overrides both the exercise and the routine rule when set. */
    val progressionRule: String? = null,
    /** Exercises sharing a group are a superset, and rest once per round. */
    val supersetGroup: Int? = null,
)

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(RoutineEntity::class, ["id"], ["routineId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("routineId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    /** Null for a freestyle session. */
    val routineId: String? = null,
    val dayIndex: Int? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val bodyWeightKg: Double? = null,
    val note: String? = null,
    /** True when logged after the fact rather than lived through. */
    val isRetroactive: Boolean = false,
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sessionId"), Index("exerciseId"), Index("sessionId", "orderInSession")],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val orderInSession: Int,
    val kind: SetKind = SetKind.Standard,

    val loadKg: Double? = null,
    val reps: Int? = null,
    /** For [SetKind.PerSide]: reps is the total, this is what one side did. */
    val repsPerSide: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    /** Reps in reserve. Null when not rated. */
    val rir: Int? = null,

    val supersetGroup: Int? = null,
    /** Set this drop came off, so a drop chain can be reconstructed. */
    val dropOfSetId: String? = null,
    val completedAt: Long,
) {
    /**
     * The one derived property worth putting on the entity, because getting it
     * wrong silently corrupts every chart in the app.
     */
    val countsTowardProgression: Boolean
        get() = kind != SetKind.WarmUp && kind != SetKind.Cardio
}

/**
 * What a schedule rule is about, so evidence can be matched to it.
 *
 * Stored as a string, so a later domain adds a value without a migration.
 */
enum class ScheduleKind {
    /** Satisfied by a logged training session. */
    Training,

    /** Satisfied by a body weight entry. */
    BodyWeight,

    /** Satisfied by a recorded payment or renewal. */
    Money,

    /** Nothing in the database can satisfy it, so it is ticked by hand. */
    Manual,
}

/**
 * A recurring commitment.
 *
 * The recurrence is a string that a person can read and repair in a SQLite
 * browser, for the same reason the progression rule is. Dates are epoch days
 * rather than millis: a rule falls on a date, not at an instant, and storing
 * a time would invent a precision the rule does not have.
 */
@Entity(tableName = "schedule_rule", indices = [Index("kind")])
data class ScheduleRuleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: ScheduleKind,
    /** Serialised Recurrence. See RecurrenceCodec. */
    val recurrence: String,
    val anchorEpochDay: Long,
    val untilEpochDay: Long? = null,
    val createdAt: Long,
    val archivedAt: Long? = null,
)

/**
 * An occurrence the person deliberately set aside.
 *
 * Only skips are stored. Everything else about an occurrence is derived from
 * the rule and the record every time, because a materialised calendar and a
 * rule that has since changed will disagree, and the stale table always wins
 * by accident.
 */
@Entity(
    tableName = "skipped_occurrence",
    primaryKeys = ["ruleId", "dueEpochDay", "occurrenceIndex"],
    foreignKeys = [
        ForeignKey(ScheduleRuleEntity::class, ["id"], ["ruleId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SkippedOccurrenceEntity(
    val ruleId: String,
    val dueEpochDay: Long,
    val occurrenceIndex: Int,
    val skippedAt: Long,
    val note: String? = null,
)

/**
 * What the app has actually told you.
 *
 * Two jobs. It is how the same fact is not said twice, and it is how a person
 * can check what the app has been doing without taking its word for it. An app
 * that notifies you and keeps no record is one you cannot audit.
 *
 * Keyed by the notification's key and the moment, so the history is kept
 * rather than overwritten: the last time something was said is a query, not a
 * column that forgets everything before it.
 */
@Entity(
    tableName = "notification_ledger",
    primaryKeys = ["notificationKey", "postedAt"],
    indices = [Index("notificationKey"), Index("postedAt")],
)
data class NotificationLedgerEntity(
    val notificationKey: String,
    val title: String,
    val tier: String,
    val postedAt: Long,
    val inDigest: Boolean = false,
)

/**
 * A working set with the muscles it trained, as the muscle map reads them.
 *
 * Not a table. This is the shape of a join, kept here so the query and the
 * thing it returns sit next to each other.
 */
data class SetWithMuscles(
    val exerciseId: String,
    val loadKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val completedAt: Long,
    val primaryMuscle: Muscle,
    val secondaryMuscles: String,
)

data class Estimated1rm(val exerciseId: String, val estimated1rmKg: Double)

data class LastTrained(val exerciseId: String, val lastAt: Long)

@Entity(tableName = "body_weight", indices = [Index("at", unique = true)])
data class BodyWeightEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val kg: Double,
)

/**
 * Where an exercise currently stands: what to prescribe next, and how many
 * sessions it has failed to advance.
 *
 * Split from [ExerciseEntity] deliberately. The exercise is library data that a
 * reinstall can reseed; this is your history and it is not reproducible.
 */
@Entity(
    tableName = "exercise_state",
    foreignKeys = [
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class ExerciseStateEntity(
    @PrimaryKey val exerciseId: String,
    val nextLoadKg: Double? = null,
    val nextReps: Int,
    val nextSets: Int,
    val nextRepCeiling: Int? = null,
    val nextDurationSec: Int? = null,
    /** Consecutive sessions that failed to advance. Drives the deload. */
    val stalls: Int = 0,
    val lastSessionAt: Long? = null,
    /** Best estimated 1RM ever recorded, for the strength view. */
    val bestEstimated1rmKg: Double? = null,
)
