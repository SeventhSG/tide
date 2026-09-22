package app.tide.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

/**
 * The codec, round-tripped.
 *
 * The corrupt-input tests matter as much as the round trips: a bad row should
 * cost one reminder, not take the app down on launch.
 */
class RecurrenceCodecTest {

    private fun roundTrip(r: Recurrence) {
        val encoded = RecurrenceCodec.encode(r)
        assertEquals("round trip failed via '$encoded'", r, RecurrenceCodec.decode(encoded))
    }

    @Test
    fun `every rule survives a round trip`() {
        roundTrip(Recurrence.Daily(3))
        roundTrip(Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), 2))
        roundTrip(Recurrence.MonthlyByDay(31, 3))
        roundTrip(Recurrence.MonthlyByWeekday(-1, DayOfWeek.FRIDAY))
        roundTrip(Recurrence.TimesPerWeek(4))
        roundTrip(Recurrence.TimesPerMonth(12))
    }

    @Test
    fun `it is readable by a person in a SQLite browser`() {
        assertEquals("Daily:1", RecurrenceCodec.encode(Recurrence.Daily()))
        assertEquals("TimesPerWeek:3", RecurrenceCodec.encode(Recurrence.TimesPerWeek(3)))
        assertEquals(
            "Weekly:MONDAY,WEDNESDAY:1",
            RecurrenceCodec.encode(Recurrence.Weekly(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY))),
        )
    }

    @Test
    fun `days keep calendar order however the set was built`() {
        val one = RecurrenceCodec.encode(
            Recurrence.Weekly(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY)),
        )
        val other = RecurrenceCodec.encode(
            Recurrence.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
        )
        assertEquals("the same rule must not write two different rows", one, other)
    }

    @Test
    fun `rubbish decodes to nothing rather than throwing`() {
        listOf(
            null, "", "   ", "Nonsense:1", "Daily", "Daily:zero",
            "Weekly:NOTADAY:1", "MonthlyByDay:99:1", "TimesPerWeek:0",
        ).forEach {
            assertNull("'$it' should decode to null", RecurrenceCodec.decode(it))
        }
    }
}
