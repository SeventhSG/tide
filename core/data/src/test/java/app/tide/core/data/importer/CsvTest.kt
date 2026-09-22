package app.tide.core.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The CSV reader.
 *
 * Every case here is one that a `split(",")` reader gets wrong, and each one
 * shifts columns rather than failing loudly, which is why this is parsed
 * properly rather than quickly.
 */
class CsvTest {

    @Test
    fun `a quoted comma stays inside its field`() {
        val rows = Csv.parse("a,\"b,c\",d")
        assertEquals(listOf(listOf("a", "b,c", "d")), rows)
    }

    @Test
    fun `a doubled quote is one quote`() {
        // The file holds: a,"say ""hi""",c
        val rows = Csv.parse("a,\"say \"\"hi\"\"\",c")
        assertEquals(listOf("a", "say \"hi\"", "c"), rows.single())
    }

    @Test
    fun `both line endings work`() {
        assertEquals(2, Csv.parse("a,b\nc,d").size)
        assertEquals(2, Csv.parse("a,b\r\nc,d").size)
    }

    @Test
    fun `a trailing newline does not make an empty row`() {
        assertEquals(2, Csv.parse("a,b\nc,d\n").size)
    }

    @Test
    fun `a newline inside quotes stays inside the field`() {
        val rows = Csv.parse("a,\"line one\nline two\",c")
        assertEquals("one row, because the newline was quoted", 1, rows.size)
        assertEquals("line one\nline two", rows.single()[1])
    }

    @Test
    fun `headers key the rows, lowercased`() {
        val rows = Csv.parseWithHeader("Date,Exercise\n2024-01-01,Squat")
        assertEquals("2024-01-01", rows.single()["date"])
        assertEquals("Squat", rows.single()["exercise"])
    }

    @Test
    fun `a blank line between rows is ignored`() {
        val rows = Csv.parseWithHeader("Date,Exercise\n2024-01-01,Squat\n\n2024-01-02,Bench")
        assertEquals(2, rows.size)
    }

    @Test
    fun `an empty file is empty, not a crash`() {
        assertEquals(emptyList<List<String>>(), Csv.parse(""))
        assertEquals(emptyList<Map<String, String>>(), Csv.parseWithHeader(""))
    }
}
