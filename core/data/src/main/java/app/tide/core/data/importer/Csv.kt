package app.tide.core.data.importer

/**
 * A CSV reader, because the exports need one.
 *
 * `split(",")` loses the first row with a comma inside a quoted field, and both
 * exports have them: Strong quotes exercise names, and a comment field takes
 * whatever the user typed. Getting this wrong shifts every column right and
 * silently imports a year of training as garbage, so it is parsed properly.
 *
 * Handles quoted fields, doubled quotes as an escaped quote, embedded newlines
 * and both line endings. It does not handle alternative delimiters, because
 * neither app emits them.
 */
object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        // A leading byte order mark would otherwise become part of the first
        // header name, and every lookup of that column would miss.
        val input = text.removePrefix("﻿")

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            // A trailing newline should not produce a final empty row.
            if (row.size > 1 || row[0].isNotEmpty()) rows.add(row)
            row = mutableListOf()
        }

        while (i < input.length) {
            val c = input[i]
            when {
                inQuotes && c == '"' && i + 1 < input.length && input[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> endField()
                !inQuotes && (c == '\n' || c == '\r') -> {
                    endRow()
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /**
     * Rows keyed by header name, lowercased and trimmed.
     *
     * Both apps have changed their column order and their capitalisation between
     * versions, so nothing here may read a column by index.
     */
    fun parseWithHeader(text: String): List<Map<String, String>> {
        val rows = parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        return rows.drop(1).mapNotNull { row ->
            if (row.all { it.isBlank() }) return@mapNotNull null
            header.indices.associate { i ->
                header[i] to (row.getOrNull(i)?.trim() ?: "")
            }
        }
    }
}
