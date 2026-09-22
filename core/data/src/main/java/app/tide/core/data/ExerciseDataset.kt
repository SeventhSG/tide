package app.tide.core.data

import android.content.Context
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import org.json.JSONArray
import org.json.JSONObject

/**
 * The full exercise library: 1,311 exercises, bundled as a seeded asset.
 *
 * The data comes from
 * [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset),
 * whose exercise metadata (names, body part, equipment, target and secondary
 * muscles, step-by-step instructions) is **MIT licensed**. That is the half of
 * that repository this app can use.
 *
 * **Its media is not in here, and cannot be.** The same repository's own
 * `LICENSE` carries an explicit media exception: the thumbnails and animation
 * GIFs are © Gym visual, redistributed to that one repository under a private
 * written permission that does not extend to anyone downstream, and its
 * `NOTICE.md` says so in as many words: "cloning this repository does not
 * grant you any license to the media." That is precisely the check exercise
 * images have to pass in this app and precisely why it fails it, exactly like
 * every other source considered before it. Every exercise still gets a
 * picture: [app.tide.core.design.ExerciseArt], drawn in this repository, from
 * the equipment and muscle this file reads out of the metadata.
 *
 * The bundled asset (`exercises.json`, in `assets/`) is a stripped copy of the
 * upstream data: English instructions only, since the app ships English only,
 * and none of the `image`, `gif_url`, `media_id` or `attribution` fields,
 * since those describe media this app does not carry. Six records that were
 * exact duplicates in the source, and seven whose name matches one of the 38
 * hand-curated [StarterLibrary] exercises already seeded (which keep their
 * own chosen progression rule rather than being overwritten), are left out.
 *
 * **No progression rule is guessed for any of these 1,311.** Assigning one
 * without knowing the lift would be exactly the kind of invented precision
 * this app refuses elsewhere: a static hold given a rep-based rule, or an
 * isolation exercise given a compound's jump size. Each falls back to the
 * routine's own default rule, which is what that fallback exists for.
 */
object ExerciseDataset {

    private const val ASSET_NAME = "exercises.json"

    /** Every exercise this asset holds, mapped into Tide's own schema. */
    fun loadFromAssets(context: Context): List<ExerciseEntity> {
        val json = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(json)
    }

    /**
     * Pure, so the mapping is provable without an asset, a device, or Room:
     * feed it text, get entities back.
     */
    fun parse(json: String): List<ExerciseEntity> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val row = array.getJSONObject(i)
            val secondary = row.optJSONArray("secondary_muscles").strings()

            val name = row.getString("name")
            ExerciseEntity(
                id = row.getString("id"),
                name = name,
                primaryMuscle = mapTarget(row.getString("target"), name),
                secondaryMuscles = secondary.mapNotNull(::mapSecondary).distinct().take(2)
                    .joinToString(","),
                equipment = mapEquipment(row.getString("equipment")),
                isBodyweight = row.getString("equipment") in BODYWEIGHT_EQUIPMENT,
                isCustom = false,
                progressionRule = null,
                notes = notesFrom(row.optJSONArray("instructions").strings()),
            )
        }
    }

    private fun JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }

    private fun notesFrom(steps: List<String>): String? {
        if (steps.isEmpty()) return null
        return steps.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("\n")
    }

    private val BODYWEIGHT_EQUIPMENT = setOf("body weight", "assisted")

    /**
     * The dataset's `target` (18 values) onto Tide's [Muscle] (21 values).
     *
     * Most are a direct rename. A few need a decision the source does not
     * make: "delts" alone does not say front, side or rear, so the exercise's
     * own name breaks the tie, the same way a person reads "overhead press"
     * as a front-delt movement and "lateral raise" as a side one.
     */
    fun mapTarget(target: String, exerciseName: String = ""): Muscle = when (target) {
        "abductors" -> Muscle.Abductors
        "abs" -> Muscle.Abs
        "adductors" -> Muscle.Adductors
        "biceps" -> Muscle.Biceps
        "calves" -> Muscle.Calves
        "cardiovascular system" -> Muscle.FullBody
        "delts" -> delts(exerciseName)
        "forearms" -> Muscle.Forearms
        "glutes" -> Muscle.Glutes
        "hamstrings" -> Muscle.Hamstrings
        "lats" -> Muscle.Lats
        "levator scapulae" -> Muscle.Neck
        "pectorals" -> Muscle.Chest
        "quads" -> Muscle.Quads
        "serratus anterior" -> Muscle.Chest
        "spine" -> Muscle.LowerBack
        "traps" -> Muscle.Traps
        "triceps" -> Muscle.Triceps
        "upper back" -> Muscle.Back
        else -> Muscle.FullBody
    }

    private fun delts(name: String): Muscle {
        val lower = name.lowercase()
        return when {
            lower.contains("rear") || lower.contains("reverse fly") || lower.contains("face pull") ->
                Muscle.RearDelts
            lower.contains("lateral") || lower.contains("raise") -> Muscle.SideDelts
            else -> Muscle.FrontDelts
        }
    }

    /** A secondary muscle name onto [Muscle], or null when there is no honest fit. */
    fun mapSecondary(name: String): Muscle? = when (name) {
        "abdominals", "lower abs", "core" -> Muscle.Abs
        "obliques" -> Muscle.Obliques
        "ankle stabilizers", "ankles", "shins", "feet" -> Muscle.Calves
        "back", "rhomboids", "upper back" -> Muscle.Back
        "biceps", "brachialis" -> Muscle.Biceps
        "calves", "soleus" -> Muscle.Calves
        "chest", "upper chest" -> Muscle.Chest
        "deltoids", "shoulders", "rotator cuff" -> Muscle.SideDelts
        "rear deltoids" -> Muscle.RearDelts
        "forearms", "wrist extensors", "wrist flexors", "wrists", "hands", "grip muscles" ->
            Muscle.Forearms
        "glutes" -> Muscle.Glutes
        "groin", "inner thighs" -> Muscle.Adductors
        "hamstrings", "quadriceps" -> Muscle.Hamstrings
        "hip flexors" -> Muscle.Abs
        "latissimus dorsi", "lats" -> Muscle.Lats
        "lower back" -> Muscle.LowerBack
        "sternocleidomastoid" -> Muscle.Neck
        "trapezius", "traps" -> Muscle.Traps
        "triceps" -> Muscle.Triceps
        else -> null
    }

    /** The dataset's `equipment` (28 values) onto Tide's [Equipment] (11 values). */
    fun mapEquipment(equipment: String): Equipment = when (equipment) {
        "barbell", "olympic barbell" -> Equipment.Barbell
        "ez barbell" -> Equipment.EzBar
        "trap bar" -> Equipment.TrapBar
        "dumbbell" -> Equipment.Dumbbell
        "cable", "rope" -> Equipment.Cable
        "body weight" -> Equipment.Bodyweight
        "assisted" -> Equipment.Machine
        "band", "resistance band" -> Equipment.Band
        "kettlebell" -> Equipment.Kettlebell
        "smith machine" -> Equipment.Smith
        "leverage machine", "sled machine", "stationary bike", "elliptical machine",
        "skierg machine", "stepmill machine", "upper body ergometer",
        -> Equipment.Machine
        else -> Equipment.Other
    }
}
