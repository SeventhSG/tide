package app.tide.core.data.importer

import app.tide.core.data.db.Equipment
import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Matching export names onto the library.
 *
 * The cases here are the ones that decide whether an import is worth doing.
 * The last test is the most important: an ambiguous name must not be guessed,
 * because a wrong match merges two lifts and quietly corrupts the load on both.
 */
class ExerciseMatcherTest {

    private fun e(id: String, name: String, equipment: Equipment) = ExerciseEntity(
        id = id, name = name, primaryMuscle = Muscle.FullBody, equipment = equipment,
    )

    private val library = listOf(
        e("bench", "Bench press", Equipment.Barbell),
        e("squat", "Back squat", Equipment.Barbell),
        e("ohp", "Overhead press", Equipment.Barbell),
        e("row", "Barbell row", Equipment.Barbell),
        e("db-row", "Dumbbell row", Equipment.Dumbbell),
        e("lat-pulldown", "Lat pulldown", Equipment.Cable),
        e("rdl", "Romanian deadlift", Equipment.Barbell),
        e("pullup", "Pull-up", Equipment.Bodyweight),
        e("leg-curl", "Lying leg curl", Equipment.Machine),
        e("triceps-pushdown", "Triceps pushdown", Equipment.Cable),
    )

    private val matcher = ExerciseMatcher(library)

    private fun matchId(name: String) = matcher.match(name)?.id

    @Test
    fun `Strong's bracketed equipment matches the library`() {
        assertEquals("bench", matchId("Bench Press (Barbell)"))
        assertEquals("squat", matchId("Squat (Barbell)"))
        assertEquals("lat-pulldown", matchId("Lat Pulldown (Cable)"))
    }

    @Test
    fun `FitNotes' equipment prefix matches the same entries`() {
        assertEquals("bench", matchId("Barbell Bench Press"))
        assertEquals("row", matchId("Barbell Row"))
    }

    @Test
    fun `equipment decides between two lifts sharing a name`() {
        // Both normalise to "row", so the bracket is the only thing telling
        // them apart. Getting this wrong merges two histories.
        assertEquals("row", matchId("Row (Barbell)"))
        assertEquals("db-row", matchId("Row (Dumbbell)"))
        assertEquals("db-row", matchId("Dumbbell Row"))
    }

    @Test
    fun `known aliases resolve`() {
        assertEquals("ohp", matchId("OHP"))
        assertEquals("ohp", matchId("Military Press"))
        assertEquals("rdl", matchId("RDL"))
        assertEquals("rdl", matchId("Stiff Leg Deadlift"))
        assertEquals("leg-curl", matchId("Leg Curl (Machine)"))
        assertEquals("triceps-pushdown", matchId("Tricep Pushdown (Cable)"))
    }

    @Test
    fun `punctuation and plurals do not prevent a match`() {
        assertEquals("pullup", matchId("Pull Up"))
        assertEquals("pullup", matchId("Pull-Ups"))
        assertEquals("pullup", matchId("pullup"))
    }

    @Test
    fun `case and spacing do not prevent a match`() {
        assertEquals("bench", matchId("  bench   PRESS  "))
    }

    @Test
    fun `an ambiguous name is refused rather than guessed`() {
        // "Row" alone could be either row in the library, and nothing in the
        // name says which. Refusing makes it a visible custom exercise instead
        // of a silent, wrong merge.
        assertNull(matchId("Row"))
    }

    @Test
    fun `a lift that is genuinely not in the library stays unmatched`() {
        assertNull(matchId("Zercher Carry"))
        assertNull(matchId("Jefferson Curl"))
    }

    @Test
    fun `equipment is read off a name for a custom exercise`() {
        assertEquals(Equipment.Dumbbell, matcher.equipmentOf("Zercher Carry (Dumbbell)"))
        assertEquals(Equipment.Other, matcher.equipmentOf("Jefferson Curl"))
    }
}
