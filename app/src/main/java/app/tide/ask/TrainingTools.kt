package app.tide.ask

import app.tide.core.data.training.TrainingRepository
import java.time.Instant
import java.time.ZoneId

/**
 * What Ask can actually say today, with no model at all.
 *
 * The roadmap for the assistant was always three steps, in this order: "The
 * SQL-backed tool layer first, against a populated database and testable
 * without a model at all. Then the engine. Then the chat surface." No engine
 * exists yet, so this is as far as that plan goes tonight, and it is a real
 * step rather than a placeholder: every function here answers a genuine
 * question straight from the training database, in a sentence, and every one
 * is provable by moving data rather than by asking a model anything.
 *
 * Once an engine exists, these become its tools rather than being replaced by
 * it: the model's job is choosing which of these to call and phrasing the
 * result, never inventing a number these could have answered.
 */
object TrainingTools {

    suspend fun sessionsThisWeek(
        repository: TrainingRepository,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
        val from = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = repository.sessionsBetween(from, now).count { it.endedAt != null }
        return when (sessions) {
            0 -> "Nothing logged this week yet."
            1 -> "1 session this week."
            else -> "$sessions sessions this week."
        }
    }

    suspend fun volumeLast7Days(repository: TrainingRepository, now: Long): String {
        val volume = repository.volumeBetween(now - 7 * 86_400_000L, now)
        if (volume <= 0) return "No volume in the last 7 days."
        return "${volume.toInt().grouped()} kg in the last 7 days."
    }

    /**
     * What you did last time on a named lift.
     *
     * The name is matched the same way the picker's search does, in SQL, so
     * "row" finds "Barbell row" the same way it would on that screen. Several
     * matches or none are both said plainly rather than guessed at.
     */
    suspend fun lastPerformance(repository: TrainingRepository, exerciseQuery: String): String {
        val matches = repository.library(exerciseQuery)
        val exercise = when {
            matches.isEmpty() -> return "Nothing in the library matches \"$exerciseQuery\"."
            matches.size == 1 -> matches.single()
            else -> matches.firstOrNull { it.name.equals(exerciseQuery, ignoreCase = true) }
                ?: return "\"$exerciseQuery\" matches ${matches.size} exercises: " +
                    matches.take(5).joinToString(", ") { it.name } + "."
        }
        val last = repository.lastPerformance(exercise.id)
            ?: return "No history yet for ${exercise.name}."
        val load = last.loadKg?.let { "${trim(it)} kg x ${last.reps}" } ?: "x ${last.reps}"
        val sets = if (last.sets == 1) "1 set" else "${last.sets} sets"
        return "${exercise.name}, last time: $load for $sets."
    }

    suspend fun muscleBalance(repository: TrainingRepository): String {
        val readings = repository.muscleMap()
        val worked = readings.filter { it.volumeKg > 0 }
        if (worked.isEmpty()) return "No working sets in the current window yet."
        val top = worked.maxBy { it.volumeKg }
        // Same fortnight threshold the muscle map screen itself draws as stale.
        val stale = readings.filter { (it.daysSinceTrained ?: Int.MAX_VALUE) > 14 }
        val staleNote = if (stale.isEmpty()) {
            ""
        } else {
            " Untouched in over two weeks: " + stale.joinToString(", ") { label(it.muscle) } + "."
        }
        return "Most worked: ${label(top.muscle)}, ${top.volumeKg.toInt().grouped()} kg.$staleNote"
    }

    private fun label(muscle: app.tide.core.data.db.Muscle): String =
        muscle.name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
}

private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(" ").reversed()
