package app.tide.core.data.importer

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Reading FitNotes and Strong exports into one shape.
 *
 * The two formats disagree about almost everything: column names, date format,
 * whether a workout has a name, how a warm-up is marked, and what a rep range
 * even means. All of that is resolved here, so nothing downstream has to know
 * which app the history came from.
 *
 * Nothing in this file touches the database. It turns text into [ImportedSet]
 * values and stops, which is what makes it testable without Room.
 */

/** One set as the export describes it. Exercise names are still raw here. */
data class ImportedSet(
    val performedAt: Long,
    /** Strong names its workouts. FitNotes does not, so this is null there. */
    val workoutName: String?,
    val exerciseName: String,
    val loadKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val distanceM: Double? = null,
    val rir: Int? = null,
    val isWarmUp: Boolean = false,
    val note: String? = null,
)

enum class ImportFormat { FitNotes, Strong }

data class ParseResult(
    val format: ImportFormat,
    val sets: List<ImportedSet>,
    /**
     * Rows that could not be read, with the reason. Surfaced rather than
     * swallowed: an import that silently drops a third of your history is
     * worse than one that says what it could not take.
     */
    val skipped: List<String> = emptyList(),
)

object ImportParser {

    private const val LB_TO_KG = 0.45359237

    /**
     * Picks the format from the header row.
     *
     * By column names, never by file name. People rename exports, and a wrong
     * guess here imports every column shifted.
     */
    fun detect(text: String): ImportFormat? {
        val header = Csv.parse(text).firstOrNull()?.map { it.trim().lowercase() } ?: return null
        return when {
            "exercise name" in header && "set order" in header -> ImportFormat.Strong
            "exercise" in header && "category" in header -> ImportFormat.FitNotes
            // Strong dropped "Category"; FitNotes never had "Set Order".
            "exercise" in header && "weight" in header && "reps" in header -> ImportFormat.FitNotes
            else -> null
        }
    }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): ParseResult? {
        val format = detect(text) ?: return null
        val rows = Csv.parseWithHeader(text)
        val sets = mutableListOf<ImportedSet>()
        val skipped = mutableListOf<String>()

        rows.forEachIndexed { index, row ->
            val parsed = runCatching {
                when (format) {
                    ImportFormat.FitNotes -> fitNotes(row, zone)
                    ImportFormat.Strong -> strong(row, zone)
                }
            }.getOrNull()

            if (parsed == null) {
                // Row 1 is the header, so a human counting lines sees this number.
                skipped += "row ${index + 2}: could not read ${rowSummary(row)}"
            } else {
                sets += parsed
            }
        }
        return ParseResult(format, sets, skipped)
    }

    private fun rowSummary(row: Map<String, String>): String =
        row.values.filter { it.isNotBlank() }.take(4).joinToString(", ").ifBlank { "an empty row" }

    private fun fitNotes(row: Map<String, String>, zone: ZoneId): ImportedSet? {
        val exercise = row["exercise"]?.takeIf { it.isNotBlank() } ?: return null
        val at = date(row["date"], zone) ?: return null
        val unit = row["weight unit"] ?: "kg"

        val reps = row["reps"]?.toIntOrNull()
        val load = row["weight"]?.toDoubleOrNull()?.let { toKg(it, unit) }
        val duration = duration(row["time"])
        val distance = distance(row["distance"], row["distance unit"])

        // A row carrying nothing measurable is not a set. FitNotes writes these
        // when an exercise is added to a day and never performed.
        if (reps == null && load == null && duration == null && distance == null) return null

        return ImportedSet(
            performedAt = at,
            workoutName = null,
            exerciseName = exercise,
            loadKg = load?.takeIf { it > 0 },
            reps = reps,
            durationSec = duration,
            distanceM = distance,
            note = row["comment"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun strong(row: Map<String, String>, zone: ZoneId): ImportedSet? {
        val exercise = row["exercise name"]?.takeIf { it.isNotBlank() } ?: return null
        val at = date(row["date"], zone) ?: return null
        val unit = row["weight unit"] ?: "kg"

        // Strong marks a warm-up in the set order column rather than a flag.
        val order = row["set order"].orEmpty()
        val isWarmUp = order.startsWith("W", ignoreCase = true)

        val reps = row["reps"]?.toIntOrNull()
        val load = row["weight"]?.toDoubleOrNull()?.let { toKg(it, unit) }
        val seconds = row["seconds"]?.toIntOrNull()?.takeIf { it > 0 }
        val distance = distance(row["distance"], row["distance unit"])

        if (reps == null && load == null && seconds == null && distance == null) return null

        return ImportedSet(
            performedAt = at,
            workoutName = row["workout name"]?.takeIf { it.isNotBlank() },
            exerciseName = exercise,
            loadKg = load?.takeIf { it > 0 },
            reps = reps,
            durationSec = seconds,
            distanceM = distance,
            rir = rirFromRpe(row["rpe"]),
            isWarmUp = isWarmUp,
            note = row["notes"]?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * RPE to reps in reserve, which is the same scale read from the other end.
     *
     * Only for RPE 5 and up. Below that the scale stops meaning anything a
     * lifter reports honestly, and a converted number would be invented.
     */
    private fun rirFromRpe(raw: String?): Int? {
        val rpe = raw?.toDoubleOrNull() ?: return null
        if (rpe < 5.0 || rpe > 10.0) return null
        return (10.0 - rpe).toInt().coerceIn(0, 5)
    }

    private fun toKg(value: Double, unit: String): Double {
        val u = unit.trim().lowercase()
        val kg = if (u.startsWith("lb")) value * LB_TO_KG else value
        // Loads snap to 0.25 kg, the same floor the progression engine uses,
        // and by rounding as it does. Truncating here would shade every
        // imported pound load downwards.
        return (kg * 4).roundToInt() / 4.0
    }

    private fun distance(raw: String?, unit: String?): Double? {
        val value = raw?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        return when (unit?.trim()?.lowercase()) {
            "km" -> value * 1000
            "mi", "miles" -> value * 1609.344
            "m", "metres", "meters" -> value
            else -> value * 1000
        }
    }

    /** FitNotes writes a hold as `00:01:30`, and sometimes as plain seconds. */
    private fun duration(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return null
        if (!text.contains(':')) return text.toIntOrNull()?.takeIf { it > 0 }
        val parts = text.split(':').mapNotNull { it.toIntOrNull() }
        val seconds = when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> return null
        }
        return seconds.takeIf { it > 0 }
    }

    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
    )

    private fun date(raw: String?, zone: ZoneId): Long? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null

        for (format in dateFormats) {
            runCatching {
                return LocalDateTime.parse(text, format).atZone(zone).toInstant().toEpochMilli()
            }
        }
        // A date with no time is midnight local, which is what FitNotes means.
        runCatching {
            return LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli()
        }
        return null
    }
}
