package app.tide.core.schedule

import java.time.LocalDate

/**
 * Deciding what happened, from the record rather than by asking.
 *
 * This is the idea the whole module exists for. A normal scheduler shows you a
 * checkbox and waits: you trained on Tuesday, the app has the session, and it
 * still asks you to confirm you trained. Then it marks you as having missed
 * Monday because it was told about Monday and never told about Tuesday.
 *
 * Here, evidence is whatever already happened, a logged session, a weigh-in, a
 * payment, and an occurrence resolves against it. Nothing is asked that the
 * database already knows, and nothing is marked missed while there is still
 * time for it to happen.
 *
 * Pure, and it takes dates rather than records, so it has no opinion about
 * what the evidence was. Training supplies session dates, Money supplies
 * payment dates, and neither has to teach this file anything.
 */

enum class OccurrenceState {
    /** The record says it happened. Nobody was asked. */
    Done,

    /** Still inside its window. Not late, because there is still time. */
    Due,

    /** The window closed with nothing to show for it. A fact, not a scolding. */
    Missed,

    /** Deliberately set aside. Not a miss, because it was a decision. */
    Skipped,
}

/** How a skip is stored, without carrying the derived window around. */
data class OccurrenceRef(val ruleId: String, val due: LocalDate, val index: Int = 0)

data class Resolved(
    val occurrence: Occurrence,
    val state: OccurrenceState,
    /** The day the record shows it happened, when it did. */
    val evidenceOn: LocalDate? = null,
) {
    val ref: OccurrenceRef get() = OccurrenceRef(occurrence.ruleId, occurrence.due, occurrence.index)
}

object Reconciler {

    /**
     * @param evidence the days this rule's thing actually happened, from the
     *   record. Order does not matter and duplicates are allowed: two sessions
     *   on one day can satisfy two of a weekly quota.
     */
    fun reconcile(
        occurrences: List<Occurrence>,
        evidence: List<LocalDate>,
        today: LocalDate,
        skipped: Set<OccurrenceRef> = emptySet(),
    ): List<Resolved> {
        val unused = evidence.sorted().toMutableList()
        val byDeadline = occurrences.sortedWith(compareBy({ it.window.endInclusive }, { it.index }))
        val resolved = mutableMapOf<Occurrence, Resolved>()

        for (occurrence in byDeadline) {
            val ref = OccurrenceRef(occurrence.ruleId, occurrence.due, occurrence.index)
            if (ref in skipped) {
                resolved[occurrence] = Resolved(occurrence, OccurrenceState.Skipped)
                continue
            }

            // Earliest deadline first, taking the earliest evidence that fits.
            // One record satisfies one occurrence: three sessions in a week
            // clear three of three, and one session clears exactly one.
            val hit = unused.firstOrNull { it in occurrence.window }
            if (hit != null) {
                unused.remove(hit)
                resolved[occurrence] = Resolved(occurrence, OccurrenceState.Done, hit)
                continue
            }

            val state = if (occurrence.window.endInclusive < today) {
                OccurrenceState.Missed
            } else {
                // Still open. Nothing is late while there is time left in the
                // window, which is what stops a Tuesday lifter being told off
                // on Monday evening.
                OccurrenceState.Due
            }
            resolved[occurrence] = Resolved(occurrence, state)
        }

        return occurrences.map { resolved.getValue(it) }
    }

    /**
     * What still needs doing, soonest first.
     *
     * Missed items come first within a day, because a closed window is the
     * thing you can no longer act on and burying it under today's open ones
     * hides it.
     */
    fun outstanding(resolved: List<Resolved>): List<Resolved> =
        resolved.filter { it.state == OccurrenceState.Due || it.state == OccurrenceState.Missed }
            .sortedWith(compareBy({ it.occurrence.due }, { it.state != OccurrenceState.Missed }))

    /**
     * How a quota is going, as a count rather than a verdict.
     *
     * Two of three, with the week still open, is a statement of fact. It is
     * deliberately not a percentage and deliberately not a streak: this app
     * does not keep score of your consistency and does not have an opinion
     * about a missed week.
     */
    fun quotaProgress(resolved: List<Resolved>, ruleId: String, window: ClosedRange<LocalDate>): QuotaProgress? {
        val inWindow = resolved.filter {
            it.occurrence.ruleId == ruleId && it.occurrence.window == window
        }
        if (inWindow.isEmpty()) return null
        return QuotaProgress(
            done = inWindow.count { it.state == OccurrenceState.Done },
            target = inWindow.size,
            windowEnd = window.endInclusive,
        )
    }
}

/** Done out of target. No percentage, and no judgement about the difference. */
data class QuotaProgress(
    val done: Int,
    val target: Int,
    val windowEnd: LocalDate,
) {
    val remaining: Int get() = (target - done).coerceAtLeast(0)
}
