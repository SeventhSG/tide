package app.tide.notify

import app.tide.core.data.db.ScheduleKind
import app.tide.core.data.schedule.ScheduleRepository
import app.tide.core.data.training.TrainingRepository
import app.tide.core.schedule.Recurrence
import java.time.DayOfWeek

/**
 * The bridge from the week you planned to the week the app watches.
 *
 * Until now the two halves never met: `:core:schedule` reconciled rules against
 * logged sessions, and nothing created a rule, so the daily summary correctly
 * had nothing to say. A plan is exactly the missing thing. Planning Monday and
 * Thursday means the app knows those are training days, the reconciler resolves
 * each one from the session you logged, and the summary can say what is planned
 * for today without anyone confirming anything by hand.
 *
 * **One rule, rewritten.** The plan is the truth; the rule is derived from it.
 * Editing the plan archives the old rule and writes a new one rather than
 * accumulating half-true rules nobody can see or delete.
 *
 * **The rule starts today.** It is anchored on the day the plan was edited, so
 * planning Monday on a Wednesday claims the Monday coming rather than reaching
 * back to announce that this week's Monday was missed.
 *
 * **Nothing here is a nudge.** The rule generates one line in one quiet daily
 * summary, and a day you did not train produces no message at all: the digest
 * lists what is due, never what was missed.
 */
class PlanSchedule(
    private val training: TrainingRepository,
    private val schedule: ScheduleRepository,
) {

    suspend fun sync() {
        val days = training.planDays().filterValues { it.isNotEmpty() }.keys

        // Archive whatever the plan wrote last time. Rules made anywhere else
        // are left alone: this owns its own rule and nothing more.
        schedule.rules()
            .filter { it.title == TITLE }
            .forEach { schedule.archiveRule(it.id) }

        if (days.isEmpty()) return

        schedule.addRule(
            title = TITLE,
            kind = ScheduleKind.Training,
            recurrence = Recurrence.Weekly(
                days = days.map { DayOfWeek.of(it + 1) }.toSet(),
            ),
        )
    }

    private companion object {
        /** How the plan's own rule is recognised again next time. */
        const val TITLE = "Planned training"
    }
}
