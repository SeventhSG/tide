package app.tide.core.data.money

import app.tide.core.data.db.SubscriptionDao
import app.tide.core.data.db.SubscriptionEntity
import app.tide.core.schedule.Recurrence
import app.tide.core.schedule.RecurrenceCodec
import app.tide.core.schedule.Schedule
import app.tide.core.schedule.ScheduleRule
import java.time.LocalDate
import java.util.UUID

/**
 * Subscriptions, and when they renew.
 *
 * Deliberately not built on [app.tide.core.data.schedule.ScheduleRepository].
 * That module resolves a rule against evidence, Done or Missed, and a
 * renewal has neither: it is not a commitment kept or failed, it is a fact
 * that happens on a date whether anyone opens the app or not. So this reads
 * straight through `core:schedule`'s pure [Schedule.expand], which is
 * already tested, rather than forcing every renewal through a reconciler
 * that would mislabel each one `Missed` the moment its date passes unwatched.
 */
class MoneyRepository(
    private val subscriptions: SubscriptionDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    fun observeActive() = subscriptions.observeActive()

    suspend fun active(): List<SubscriptionEntity> = subscriptions.active()

    suspend fun add(name: String, amount: Double, recurrence: Recurrence, anchor: LocalDate): String {
        val id = newId()
        subscriptions.upsert(
            SubscriptionEntity(
                id = id,
                name = name,
                amount = amount,
                recurrence = RecurrenceCodec.encode(recurrence),
                anchorEpochDay = anchor.toEpochDay(),
                createdAt = now(),
            ),
        )
        return id
    }

    suspend fun archive(id: String) = subscriptions.archive(id, now())

    /** Every renewal falling in `[from, to]`, across every active subscription. */
    suspend fun upcoming(from: LocalDate, to: LocalDate): List<UpcomingRenewal> {
        if (from > to) return emptyList()
        return subscriptions.active().flatMap { sub ->
            val recurrence = RecurrenceCodec.decode(sub.recurrence)
                // A corrupt row costs this one subscription's renewals, not the screen.
                ?: return@flatMap emptyList()
            val rule = ScheduleRule(
                id = sub.id,
                recurrence = recurrence,
                anchor = LocalDate.ofEpochDay(sub.anchorEpochDay),
            )
            Schedule.expand(rule, from, to).map { occurrence ->
                UpcomingRenewal(sub.id, sub.name, sub.amount, occurrence.due)
            }
        }.sortedBy { it.date }
    }

    /** What actually renews inside this calendar month. No forced conversion between cadences. */
    suspend fun monthlyTotal(month: LocalDate): Double {
        val start = month.withDayOfMonth(1)
        val end = start.withDayOfMonth(start.lengthOfMonth())
        return upcoming(start, end).sumOf { it.amount }
    }
}

/** One subscription's renewal, on one date. */
data class UpcomingRenewal(
    val subscriptionId: String,
    val name: String,
    val amount: Double,
    val date: LocalDate,
)
