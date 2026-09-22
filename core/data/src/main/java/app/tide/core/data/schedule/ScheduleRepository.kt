package app.tide.core.data.schedule

import app.tide.core.data.db.ScheduleDao
import app.tide.core.data.db.ScheduleKind
import app.tide.core.data.db.ScheduleRuleEntity
import app.tide.core.data.db.SkippedOccurrenceEntity
import app.tide.core.schedule.Occurrence
import app.tide.core.schedule.OccurrenceRef
import app.tide.core.schedule.Recurrence
import app.tide.core.schedule.RecurrenceCodec
import app.tide.core.schedule.Reconciler
import app.tide.core.schedule.Resolved
import app.tide.core.schedule.Schedule
import app.tide.core.schedule.ScheduleRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Where the schedule meets the record.
 *
 * `:core:schedule` is pure and knows nothing about training. This is the piece
 * that hands it the evidence, and the reason the reconciler can say "you did
 * that" without ever asking: a finished session is already in the database, so
 * a rule about training resolves itself.
 *
 * Occurrences are never stored. They are derived from the rule every time,
 * because a materialised calendar and a rule that has since changed will
 * disagree, and the stale table always wins by accident. Only skips are
 * persisted, because a skip is a decision and cannot be derived from anything.
 */
class ScheduleRepository(
    private val schedule: ScheduleDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val today: () -> LocalDate = { LocalDate.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun rules(): List<ScheduleRuleEntity> = schedule.active()

    fun observeRules() = schedule.observeActive()

    suspend fun addRule(
        title: String,
        kind: ScheduleKind,
        recurrence: Recurrence,
        anchor: LocalDate = today(),
        until: LocalDate? = null,
    ): String {
        val id = newId()
        schedule.upsert(
            ScheduleRuleEntity(
                id = id,
                title = title,
                kind = kind,
                recurrence = RecurrenceCodec.encode(recurrence),
                anchorEpochDay = anchor.toEpochDay(),
                untilEpochDay = until?.toEpochDay(),
                createdAt = now(),
            ),
        )
        return id
    }

    suspend fun archiveRule(id: String) = schedule.archive(id, now())

    suspend fun skip(ref: OccurrenceRef, note: String? = null) {
        schedule.skip(
            SkippedOccurrenceEntity(
                ruleId = ref.ruleId,
                dueEpochDay = ref.due.toEpochDay(),
                occurrenceIndex = ref.index,
                skippedAt = now(),
                note = note,
            ),
        )
    }

    suspend fun unskip(ref: OccurrenceRef) =
        schedule.unskip(ref.ruleId, ref.due.toEpochDay(), ref.index)

    /**
     * Everything scheduled across a span, resolved against what actually
     * happened.
     *
     * Each rule is reconciled against its own kind's evidence separately, so a
     * single session can count toward a weekly quota **and** toward a "train at
     * least once this month" rule. Those are two real commitments and one
     * session genuinely satisfies both; it is only within a single rule that
     * one record must not clear two obligations.
     */
    suspend fun outlook(from: LocalDate, to: LocalDate): List<Resolved> {
        val rules = schedule.active()
        if (rules.isEmpty()) return emptyList()

        val skips = schedule.allSkips().map {
            OccurrenceRef(it.ruleId, LocalDate.ofEpochDay(it.dueEpochDay), it.occurrenceIndex)
        }.toSet()

        // Fetched once per kind across the whole span rather than per rule,
        // so ten training rules do not mean ten passes over the sessions.
        val evidenceByKind = mutableMapOf<ScheduleKind, List<LocalDate>>()
        val day = today()

        return rules.flatMap { entity ->
            val recurrence = RecurrenceCodec.decode(entity.recurrence)
            // A corrupt rule string costs this one reminder, not the screen.
                ?: return@flatMap emptyList()

            val occurrences = Schedule.expand(
                ScheduleRule(
                    id = entity.id,
                    recurrence = recurrence,
                    anchor = LocalDate.ofEpochDay(entity.anchorEpochDay),
                    until = entity.untilEpochDay?.let { LocalDate.ofEpochDay(it) },
                ),
                from,
                to,
            )
            if (occurrences.isEmpty()) return@flatMap emptyList()

            val evidence = evidenceByKind.getOrPut(entity.kind) {
                evidenceFor(entity.kind, from, to)
            }
            Reconciler.reconcile(occurrences, evidence, day, skips)
        }.sortedWith(compareBy({ it.occurrence.due }, { it.occurrence.ruleId }, { it.occurrence.index }))
    }

    /** What still needs doing between two dates, soonest first. */
    suspend fun outstanding(from: LocalDate, to: LocalDate): List<Resolved> =
        Reconciler.outstanding(outlook(from, to))

    /**
     * The week containing [day], resolved.
     *
     * The shape Today wants: what this week asked for and what has happened so
     * far, with the week still open.
     */
    suspend fun thisWeek(day: LocalDate = today()): List<Resolved> {
        val start = Schedule.weekStart(day)
        return outlook(start, start.plusDays(6))
    }

    private suspend fun evidenceFor(
        kind: ScheduleKind,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMillis = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        val instants = when (kind) {
            ScheduleKind.Training -> schedule.trainingEvidenceAt(fromMillis, toMillis)
            ScheduleKind.BodyWeight -> schedule.bodyWeightEvidenceAt(fromMillis, toMillis)
            // Money has no ledger yet, and Manual is ticked by hand. Neither
            // has evidence to read, so neither gets a made up one.
            ScheduleKind.Money, ScheduleKind.Manual -> emptyList()
        }
        // Converted here, with a real zone, rather than bucketed in SQL with a
        // fixed offset that is wrong twice a year.
        return instants.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    }
}

/** The occurrence a [Resolved] came from, for storing a skip. */
val Resolved.reference: OccurrenceRef
    get() = OccurrenceRef(occurrence.ruleId, occurrence.due, occurrence.index)

/** Just the occurrences, for callers that do not care how they resolved. */
fun List<Resolved>.occurrences(): List<Occurrence> = map { it.occurrence }
