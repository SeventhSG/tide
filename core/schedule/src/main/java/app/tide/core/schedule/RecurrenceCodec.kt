package app.tide.core.schedule

import java.time.DayOfWeek

/**
 * Recurrence rules, as a string the database can hold.
 *
 * The same bargain [app.tide.core.data.training.RuleCodec] makes for
 * progression: one line you can read in a SQLite browser and repair by hand.
 *
 *     Daily:1
 *     Weekly:MONDAY,WEDNESDAY:1
 *     MonthlyByDay:31:1
 *     TimesPerWeek:3
 *
 * This database is the only copy of the data and there is no server to restore
 * it from, so being able to fix a row without the app is worth more than the
 * convenience of a serialisation library.
 *
 * Unknown or corrupt values decode to null rather than throwing. A bad rule
 * string should cost you one reminder, not the app.
 */
object RecurrenceCodec {

    fun encode(recurrence: Recurrence): String = when (recurrence) {
        is Recurrence.Daily -> "Daily:${recurrence.every}"

        is Recurrence.Weekly ->
            "Weekly:${recurrence.days.sortedBy { it.value }.joinToString(",") { it.name }}:" +
                "${recurrence.everyWeeks}"

        is Recurrence.MonthlyByDay ->
            "MonthlyByDay:${recurrence.dayOfMonth}:${recurrence.everyMonths}"

        is Recurrence.MonthlyByWeekday ->
            "MonthlyByWeekday:${recurrence.week}:${recurrence.day.name}"

        is Recurrence.TimesPerWeek -> "TimesPerWeek:${recurrence.times}"
        is Recurrence.TimesPerMonth -> "TimesPerMonth:${recurrence.times}"
    }

    fun decode(raw: String?): Recurrence? {
        if (raw.isNullOrBlank()) return null
        val p = raw.split(':')
        return runCatching {
            when (p[0]) {
                "Daily" -> Recurrence.Daily(p[1].toInt())

                "Weekly" -> Recurrence.Weekly(
                    p[1].split(',')
                        .filter { it.isNotBlank() }
                        .map { DayOfWeek.valueOf(it) }
                        .toSet(),
                    p[2].toInt(),
                )

                "MonthlyByDay" -> Recurrence.MonthlyByDay(p[1].toInt(), p[2].toInt())
                "MonthlyByWeekday" -> Recurrence.MonthlyByWeekday(p[1].toInt(), DayOfWeek.valueOf(p[2]))
                "TimesPerWeek" -> Recurrence.TimesPerWeek(p[1].toInt())
                "TimesPerMonth" -> Recurrence.TimesPerMonth(p[1].toInt())
                else -> null
            }
        }.getOrNull()
    }
}
