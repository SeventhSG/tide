package app.tide.core.data.importer

import app.tide.core.data.db.ExerciseDao
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.db.SessionDao
import app.tide.core.data.db.SessionEntity
import app.tide.core.data.db.SetKind
import app.tide.core.data.db.WorkoutSetEntity
import java.time.ZoneId
import java.util.UUID

/**
 * Writing an imported history into the database.
 *
 * The point of this, stated plainly: opening the app on day one to your
 * training rather than to an empty screen. A log with no past cannot prescribe
 * anything, so until history can come in, the progression engine has nothing to
 * decide from for weeks.
 *
 * Imported sessions are marked [SessionEntity.isRetroactive], because a session
 * reconstructed from a CSV is not the same fact as one lived through with the
 * timer running, and nothing downstream should have to guess which it has.
 *
 * **Progression is deliberately not replayed.** Imported sessions carry an
 * `endedAt`, so `prescriptionFor` already falls back to the last session's
 * working sets and produces a real target. Replaying the engine over imported
 * history would also manufacture stall counts out of sessions whose warm-ups
 * were never marked, and a deload fired from an invented stall is exactly the
 * kind of quiet wrong number this app refuses to print.
 */
class TrainingImporter(
    private val exercises: ExerciseDao,
    private val sessions: SessionDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun import(text: String): ImportSummary? {
        val parsed = ImportParser.parse(text, zone) ?: return null
        val matcher = ExerciseMatcher(exercises.all())

        val resolved = mutableMapOf<String, ExerciseEntity>()
        val created = mutableListOf<String>()
        var matched = 0

        // Resolve every distinct name once, so a 4,000 row export does not do
        // 4,000 lookups of the same lift.
        for (name in parsed.sets.map { it.exerciseName }.distinct()) {
            val hit = matcher.match(name)
            if (hit != null) {
                resolved[name] = hit
                matched++
                continue
            }
            // An unmatched name becomes a custom exercise rather than being
            // dropped. byName is case insensitive, so importing the same file
            // twice reuses the one made the first time.
            val existing = exercises.byName(name)
            if (existing != null) {
                resolved[name] = existing
            } else {
                val entity = ExerciseEntity(
                    id = slug(name),
                    name = name,
                    // Unknown, and not guessed. A muscle map drawn from an
                    // invented primary muscle is worse than one with a gap.
                    primaryMuscle = Muscle.FullBody,
                    equipment = matcher.equipmentOf(name),
                    isCustom = true,
                )
                exercises.upsert(entity)
                resolved[name] = entity
                created += name
            }
        }

        var sessionsCreated = 0
        var setsImported = 0
        var duplicates = 0

        // A session is a day, or a named workout within a day where the export
        // names one. Strong logs two sessions on a Saturday as two workouts.
        val grouped = parsed.sets.groupBy { set ->
            val day = java.time.Instant.ofEpochMilli(set.performedAt)
                .atZone(zone).toLocalDate()
            day to set.workoutName.orEmpty()
        }

        for ((_, setsInSession) in grouped.entries.sortedBy { it.value.first().performedAt }) {
            val startedAt = setsInSession.minOf { it.performedAt }
            val endedAt = setsInSession.maxOf { it.performedAt }

            if (sessions.between(startedAt, startedAt).isNotEmpty()) {
                // Re-importing the same export should not double your history.
                duplicates++
                continue
            }

            val sessionId = newId()
            sessions.upsert(
                SessionEntity(
                    id = sessionId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    note = setsInSession.first().workoutName,
                    isRetroactive = true,
                ),
            )
            sessionsCreated++

            setsInSession.forEachIndexed { index, set ->
                val exercise = resolved.getValue(set.exerciseName)
                sessions.upsertSet(
                    WorkoutSetEntity(
                        id = newId(),
                        sessionId = sessionId,
                        exerciseId = exercise.id,
                        orderInSession = index,
                        kind = kindOf(set),
                        loadKg = set.loadKg,
                        reps = set.reps,
                        durationSec = set.durationSec,
                        distanceM = set.distanceM,
                        rir = set.rir,
                        completedAt = set.performedAt,
                    ),
                )
                setsImported++
            }
        }

        return ImportSummary(
            format = parsed.format,
            sessionsCreated = sessionsCreated,
            setsImported = setsImported,
            namesMatched = matched,
            exercisesCreated = created,
            duplicateSessionsSkipped = duplicates,
            rowsSkipped = parsed.skipped,
        )
    }

    private fun kindOf(set: ImportedSet): SetKind = when {
        set.isWarmUp -> SetKind.WarmUp
        set.distanceM != null -> SetKind.Cardio
        set.durationSec != null && set.reps == null -> SetKind.Timed
        else -> SetKind.Standard
    }

    private fun slug(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(48)
            .ifBlank { newId() }
}

/**
 * What the import did, in terms a person can check.
 *
 * Everything that did not come in is named rather than swallowed. An import
 * that quietly drops a third of a history is worse than one that says so.
 */
data class ImportSummary(
    val format: ImportFormat,
    val sessionsCreated: Int,
    val setsImported: Int,
    val namesMatched: Int,
    val exercisesCreated: List<String>,
    val duplicateSessionsSkipped: Int,
    val rowsSkipped: List<String>,
)
