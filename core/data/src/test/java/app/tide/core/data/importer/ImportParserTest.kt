package app.tide.core.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Reading the two exports.
 *
 * The sample rows below are the shapes the real apps emit, including the parts
 * that break a naive reader: a quoted exercise name, a comment with a comma in
 * it, pounds, a warm-up marker, and a row with nothing measurable on it.
 */
class ImportParserTest {

    private val utc = ZoneId.of("UTC")

    private val fitNotes = """
        Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
        2024-03-04,Barbell Bench Press,Chest,60,kg,10,,,,
        2024-03-04,Barbell Bench Press,Chest,60,kg,9,,,,"felt heavy, elbow tucked"
        2024-03-06,Plank,Core,,kg,,,,00:01:30,
    """.trimIndent()

    private val strong = """
        Date,Workout Name,Duration,Exercise Name,Set Order,Weight,Weight Unit,Reps,RPE,Distance,Distance Unit,Seconds,Notes
        "2024-03-04 09:00:00","Push A","1h 2m","Bench Press (Barbell)","W","40","kg","8","","0","km","0",""
        "2024-03-04 09:00:00","Push A","1h 2m","Bench Press (Barbell)","1","60","kg","10","8","0","km","0",""
        "2024-03-04 09:00:00","Push A","1h 2m","Bench Press (Barbell)","2","135","lbs","8","","0","km","0",""
    """.trimIndent()

    @Test
    fun `detects each format from its columns, not its file name`() {
        assertEquals(ImportFormat.FitNotes, ImportParser.detect(fitNotes))
        assertEquals(ImportFormat.Strong, ImportParser.detect(strong))
        assertNull(ImportParser.detect("Name,Email\nAda,ada@example.com"))
    }

    @Test
    fun `reads FitNotes rows, including a comment containing a comma`() {
        val result = ImportParser.parse(fitNotes, utc)!!
        assertEquals(ImportFormat.FitNotes, result.format)
        assertEquals(3, result.sets.size)

        val second = result.sets[1]
        assertEquals("Barbell Bench Press", second.exerciseName)
        assertEquals(60.0, second.loadKg!!, 0.001)
        assertEquals(9, second.reps)
        assertEquals(
            "a quoted comma must not shift every later column",
            "felt heavy, elbow tucked",
            second.note,
        )
    }

    @Test
    fun `reads a FitNotes hold as seconds`() {
        val plank = ImportParser.parse(fitNotes, utc)!!.sets.last()
        assertEquals(90, plank.durationSec)
        assertNull("a hold has no reps", plank.reps)
    }

    @Test
    fun `reads Strong rows, converting pounds and marking the warm-up`() {
        val result = ImportParser.parse(strong, utc)!!
        assertEquals(3, result.sets.size)

        val warmUp = result.sets[0]
        assertTrue("a W in set order is a warm-up, and it must survive", warmUp.isWarmUp)
        assertEquals("Push A", warmUp.workoutName)

        val working = result.sets[1]
        assertEquals(false, working.isWarmUp)
        assertEquals(60.0, working.loadKg!!, 0.001)

        val pounds = result.sets[2]
        assertEquals("135 lbs is 61.25 kg, snapped to the quarter", 61.25, pounds.loadKg!!, 0.001)
    }

    @Test
    fun `turns RPE into reps in reserve, and leaves nonsense alone`() {
        val sets = ImportParser.parse(strong, utc)!!.sets
        assertEquals("RPE 8 is two reps in reserve", 2, sets[1].rir)
        assertNull("a blank RPE is not a zero", sets[0].rir)
    }

    @Test
    fun `a row with nothing measurable is not a set`() {
        val text = """
            Date,Exercise,Category,Weight,Weight Unit,Reps,Distance,Distance Unit,Time,Comment
            2024-03-04,Back Squat,Legs,,kg,,,,,
        """.trimIndent()
        val result = ImportParser.parse(text, utc)!!
        assertTrue("an exercise added and never performed is not history", result.sets.isEmpty())
        assertEquals("and it is reported rather than swallowed", 1, result.skipped.size)
    }

    @Test
    fun `dates land on the right day`() {
        val result = ImportParser.parse(fitNotes, utc)!!
        val day = java.time.Instant.ofEpochMilli(result.sets[0].performedAt)
            .atZone(utc).toLocalDate()
        assertEquals("2024-03-04", day.toString())
    }

    @Test
    fun `an unreadable file is refused rather than half read`() {
        assertNull(ImportParser.parse("not a csv at all", utc))
    }

    @Test
    fun `a byte order mark does not break the first column`() {
        val withBom = "﻿" + fitNotes
        assertNotNull(
            "a BOM would otherwise hide the Date column and fail every row",
            ImportParser.detect(withBom),
        )
        assertEquals(3, ImportParser.parse(withBom, utc)!!.sets.size)
    }
}
