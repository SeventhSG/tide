package app.tide.core.data.importer

import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity

/**
 * Matching an exported exercise name against the library.
 *
 * The three apps write the same lift three ways. Strong puts the equipment in
 * brackets, `Bench Press (Barbell)`. FitNotes puts it in front,
 * `Barbell Bench Press`. Tide calls it `Bench press`. Whether those become one
 * exercise or three decides whether an import is worth doing at all: three
 * copies of a lift split its history and every progression decision made from
 * it.
 *
 * **A wrong match is worse than no match.** Matching `Row (Dumbbell)` onto
 * `Barbell row` silently merges two lifts and corrupts the load on both, and
 * nobody notices for a month. So an ambiguous name is left unmatched and
 * becomes a custom exercise, which is visible, reversible, and honest.
 */
class ExerciseMatcher(library: List<ExerciseEntity>) {

    /** Words that say how a lift is loaded, not which lift it is. */
    private val equipmentWords = mapOf(
        "barbell" to Equipment.Barbell,
        "bb" to Equipment.Barbell,
        "dumbbell" to Equipment.Dumbbell,
        "db" to Equipment.Dumbbell,
        "cable" to Equipment.Cable,
        "machine" to Equipment.Machine,
        "lever" to Equipment.Machine,
        "smith" to Equipment.Smith,
        "ezbar" to Equipment.EzBar,
        "ez" to Equipment.EzBar,
        "trapbar" to Equipment.TrapBar,
        "kettlebell" to Equipment.Kettlebell,
        "kb" to Equipment.Kettlebell,
        "band" to Equipment.Band,
        "bodyweight" to Equipment.Bodyweight,
        "weighted" to Equipment.Bodyweight,
    )

    /**
     * Names normalisation cannot reach, because they share no words with the
     * library entry. Kept small and explicit: every line here is a claim that
     * two names mean the same lift, and a wrong one corrupts data quietly.
     */
    private val aliases = mapOf(
        // A bare "Squat" means the back squat in every log that writes it.
        "squat" to "back squat",
        "bench" to "bench press",
        "ohp" to "overhead press",
        "military press" to "overhead press",
        "shoulder press" to "overhead press",
        "standing press" to "overhead press",
        "rdl" to "romanian deadlift",
        "stiff leg deadlift" to "romanian deadlift",
        "bent over row" to "row",
        "pendlay" to "pendlay row",
        "chin up" to "chin up",
        "pull down" to "lat pulldown",
        "pulldown" to "lat pulldown",
        "seated row" to "seated cable row",
        "lying leg curl" to "lying leg curl",
        "leg curl" to "lying leg curl",
        "hamstring curl" to "lying leg curl",
        "quad extension" to "leg extension",
        "calf raise" to "standing calf raise",
        "tricep pushdown" to "triceps pushdown",
        "tricep extension" to "skullcrusher",
        "lying triceps extension" to "skullcrusher",
        "bicep curl" to "barbell curl",
        "preacher curl" to "barbell curl",
        "rear delt fly" to "face pull",
        "shrug" to "barbell shrug",
        "hip thrust" to "hip thrust",
        "split squat" to "bulgarian split squat",
        "sled leg press" to "leg press",
        "crunch" to "cable crunch",
        "plank" to "plank",
        "farmers walk" to "farmer's walk",
        "farmers carry" to "farmer's walk",
        "hyperextension" to "back extension",
    )

    /** Full normalised name to entity, for the unambiguous case. */
    private val byFullName: Map<String, ExerciseEntity> =
        library.associateBy { normalise(it.name).core }

    /**
     * The same names with spaces removed, because `pullup`, `pull up` and
     * `Pull-Up` are one lift and three apps spell it three ways.
     */
    private val byCompactName: Map<String, List<ExerciseEntity>> =
        library.groupBy { compact(normalise(it.name).core) }

    /** Equipment stripped, so `Barbell row` and `Dumbbell row` both land on `row`. */
    private val byCoreName: Map<String, List<ExerciseEntity>> =
        library.groupBy { stripEquipment(normalise(it.name).core).core }

    private fun compact(text: String): String = text.replace(" ", "").replace("'", "")

    /**
     * The library entry this export name means, or null to make it custom.
     *
     * Tiered deliberately, most certain first, so a confident match is never
     * beaten by a fuzzy one.
     */
    fun match(rawName: String): ExerciseEntity? {
        val normalised = normalise(rawName)

        // 1. The whole name, as written, is a library name.
        byFullName[normalised.core]?.let { return it }

        // 2. The same name spelled without spaces, `pullup` onto `Pull-up`.
        byCompactName[compact(normalised.core)]?.singleOrNull()?.let { return it }

        // 3. A known alias, resolved then looked up the same way.
        aliases[normalised.core]?.let { alias ->
            byFullName[alias]?.let { return it }
            byCompactName[compact(alias)]?.singleOrNull()?.let { return it }
            byCoreName[stripEquipment(alias).core]?.singleOrNull()?.let { return it }
        }

        // 4. Equipment stripped from both sides. The bracket in
        //    `Bench Press (Barbell)` and the prefix in `Barbell Bench Press`
        //    are the same fact, and both end up here.
        val stripped = stripEquipment(normalised.core)
        val equipment = normalised.equipment ?: stripped.equipment
        val candidates = byCoreName[stripped.core].orEmpty()

        when {
            candidates.size == 1 -> return candidates.single()
            candidates.size > 1 && equipment != null ->
                // Several lifts share the name, so the equipment decides.
                candidates.firstOrNull { it.equipment == equipment }?.let { return it }
        }

        // 5. An alias of the stripped name, for `Bent Over Row (Barbell)`.
        aliases[stripped.core]?.let { alias ->
            val aliasCandidates = byCoreName[stripEquipment(alias).core].orEmpty()
            when {
                aliasCandidates.size == 1 -> return aliasCandidates.single()
                aliasCandidates.size > 1 && equipment != null ->
                    aliasCandidates.firstOrNull { it.equipment == equipment }?.let { return it }
            }
        }

        // Ambiguous, and guessing here is how histories get merged by mistake.
        return null
    }

    /** The equipment an export name implies, for building a custom exercise. */
    fun equipmentOf(rawName: String): Equipment {
        val n = normalise(rawName)
        return n.equipment ?: stripEquipment(n.core).equipment ?: Equipment.Other
    }

    private data class Normalised(val core: String, val equipment: Equipment?)

    /**
     * Lowercase, punctuation flattened, bracketed equipment pulled out.
     *
     * `Bench Press (Barbell)` becomes `bench press` plus [Equipment.Barbell],
     * and `Push-Ups` becomes `push up`.
     */
    private fun normalise(raw: String): Normalised {
        var text = raw.trim().lowercase()
        var equipment: Equipment? = null

        // Strong's bracketed equipment, which is the most reliable signal there
        // is, because the app put it there rather than the user typing it.
        val bracket = Regex("\\(([^)]*)\\)").find(text)
        if (bracket != null) {
            val inside = bracket.groupValues[1].replace(Regex("[^a-z ]"), "").trim()
            equipment = equipmentWords[inside.replace(" ", "")]
                ?: inside.split(' ').firstNotNullOfOrNull { equipmentWords[it] }
            text = text.removeRange(bracket.range).trim()
        }

        text = text
            .replace('-', ' ')
            .replace(Regex("[^a-z' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return Normalised(singularise(text), equipment)
    }

    /**
     * Plurals only, and only on the last word. `Dips` is `dip`, `Press` is not
     * `pres`, and `Pull Ups` is `pull up`, which is why the length floor is
     * three characters and not four.
     */
    private fun singularise(text: String): String {
        val words = text.split(' ').toMutableList()
        if (words.isEmpty()) return text
        val last = words.last()
        words[words.lastIndex] = when {
            last == "flies" || last == "flyes" -> "fly"
            last.endsWith("ss") -> last
            last.length > 2 && last.endsWith("s") -> last.dropLast(1)
            else -> last
        }
        return words.joinToString(" ")
    }

    private fun stripEquipment(text: String): Normalised {
        var found: Equipment? = null
        val kept = text.split(' ').filter { word ->
            val match = equipmentWords[word]
            if (match != null && found == null) found = match
            match == null
        }
        // Never strip a name down to nothing: `Barbell` alone stays as it is.
        val core = kept.joinToString(" ").trim()
        return if (core.isEmpty()) Normalised(text, found) else Normalised(core, found)
    }
}
