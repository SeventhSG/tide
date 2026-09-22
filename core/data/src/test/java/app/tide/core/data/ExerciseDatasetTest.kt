package app.tide.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.tide.core.data.db.Equipment
import app.tide.core.data.db.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The full exercise library's mapping from the bundled dataset onto Tide's
 * own schema.
 *
 * The dataset's own values (target, equipment, secondary muscles) are
 * strings from a source this app does not control, so every branch of the
 * mapping is worth pinning down directly rather than trusting it by reading
 * the `when`.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseDatasetTest {

    private fun row(
        id: String = "ds-0001",
        name: String = "Test exercise",
        target: String = "abs",
        equipment: String = "body weight",
        secondary: List<String> = emptyList(),
        instructions: List<String> = emptyList(),
    ): String {
        val sec = secondary.joinToString(",") { "\"$it\"" }
        val instr = instructions.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
        return """
            [{"id":"$id","name":"$name","body_part":"waist","equipment":"$equipment",
              "target":"$target","secondary_muscles":[$sec],"instructions":[$instr]}]
        """.trimIndent()
    }

    @Test
    fun `parsing produces one entity per record, with the id and name kept as given`() {
        val entities = ExerciseDataset.parse(row(id = "ds-0042", name = "3-4 sit-up"))
        assertEquals(1, entities.size)
        assertEquals("ds-0042", entities[0].id)
        assertEquals("3-4 sit-up", entities[0].name)
        assertEquals(
            "no rule is guessed for a bulk-imported exercise",
            null,
            entities[0].progressionRule,
        )
        assertEquals(false, entities[0].isCustom)
    }

    @Test
    fun `instructions become a numbered list in notes, or null when there are none`() {
        val withSteps = ExerciseDataset.parse(
            row(instructions = listOf("Lie down.", "Sit up.")),
        ).single()
        assertEquals("1. Lie down.\n2. Sit up.", withSteps.notes)

        val withoutSteps = ExerciseDataset.parse(row(instructions = emptyList())).single()
        assertNull(withoutSteps.notes)
    }

    @Test
    fun `most targets are a direct rename onto Muscle`() {
        assertEquals(Muscle.Abductors, ExerciseDataset.mapTarget("abductors"))
        assertEquals(Muscle.Quads, ExerciseDataset.mapTarget("quads"))
        assertEquals(Muscle.Chest, ExerciseDataset.mapTarget("pectorals"))
        assertEquals("a chest adjacent target with no Muscle of its own", Muscle.Chest, ExerciseDataset.mapTarget("serratus anterior"))
        assertEquals(Muscle.LowerBack, ExerciseDataset.mapTarget("spine"))
        assertEquals(Muscle.Back, ExerciseDataset.mapTarget("upper back"))
        assertEquals("no cardio muscle exists, so it falls back honestly", Muscle.FullBody, ExerciseDataset.mapTarget("cardiovascular system"))
    }

    @Test
    fun `a generic delt target is disambiguated by the exercise's own name`() {
        assertEquals(Muscle.FrontDelts, ExerciseDataset.mapTarget("delts", "Overhead press"))
        assertEquals(Muscle.SideDelts, ExerciseDataset.mapTarget("delts", "Lateral raise"))
        assertEquals(Muscle.RearDelts, ExerciseDataset.mapTarget("delts", "Rear delt fly"))
        assertEquals(Muscle.RearDelts, ExerciseDataset.mapTarget("delts", "Face pull"))
        assertEquals(
            "an unrecognised delt name still gets a real muscle, not a crash",
            Muscle.FrontDelts,
            ExerciseDataset.mapTarget("delts", "Some unusual delt machine"),
        )
    }

    @Test
    fun `equipment collapses cleanly onto Tide's eleven values`() {
        assertEquals(Equipment.Barbell, ExerciseDataset.mapEquipment("olympic barbell"))
        assertEquals(Equipment.EzBar, ExerciseDataset.mapEquipment("ez barbell"))
        assertEquals(Equipment.Cable, ExerciseDataset.mapEquipment("rope"))
        assertEquals(Equipment.Bodyweight, ExerciseDataset.mapEquipment("body weight"))
        assertEquals(Equipment.Smith, ExerciseDataset.mapEquipment("smith machine"))
        assertEquals(Equipment.Machine, ExerciseDataset.mapEquipment("stationary bike"))
        assertEquals("an equipment this app has no bucket for is Other, not a guess", Equipment.Other, ExerciseDataset.mapEquipment("tire"))
    }

    @Test
    fun `bodyweight is a fact read from the equipment string, not the muscle`() {
        val bodyweight = ExerciseDataset.parse(row(equipment = "body weight")).single()
        assertTrue(bodyweight.isBodyweight)

        val assisted = ExerciseDataset.parse(row(equipment = "assisted")).single()
        assertTrue("an assisted machine is still a bodyweight movement pattern", assisted.isBodyweight)

        val loaded = ExerciseDataset.parse(row(equipment = "barbell")).single()
        assertEquals(false, loaded.isBodyweight)
    }

    @Test
    fun `secondary muscles are mapped, deduplicated and capped at two`() {
        val entity = ExerciseDataset.parse(
            row(secondary = listOf("lats", "latissimus dorsi", "biceps", "rear deltoids")),
        ).single()
        val listed = entity.secondaryMuscles.split(",").filter { it.isNotEmpty() }
        assertEquals("lats and latissimus dorsi map to the same Muscle and collapse", 2, listed.size)
    }

    @Test
    fun `an unrecognised secondary muscle is dropped rather than invented`() {
        assertNull(ExerciseDataset.mapSecondary("something the mapping has never seen"))
        val entity = ExerciseDataset.parse(row(secondary = listOf("something new"))).single()
        assertEquals("", entity.secondaryMuscles)
    }

    @Test
    fun `the real bundled asset loads, is large, and every row survives mapping`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entities = ExerciseDataset.loadFromAssets(context)

        assertTrue("the full library, not a stub", entities.size > 1000)
        assertEquals("no id collides with another", entities.size, entities.map { it.id }.toSet().size)
        assertEquals("no name collides with another", entities.size, entities.map { it.name.lowercase() }.toSet().size)
        assertTrue("every row is namespaced as coming from the dataset", entities.all { it.id.startsWith("ds-") })
        assertTrue("no starter library exercise was overwritten", entities.none { it.name == "Back squat" })
    }
}
