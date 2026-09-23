package app.tide

import app.tide.core.data.money.MoneyRepository
import app.tide.core.schedule.Recurrence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The three shapes a subscription's recurrence can take, the ones `Recurrence` already supports. */
enum class Cadence { Monthly, Yearly, Weekly }

/**
 * Joins [MoneyScreen] to [MoneyRepository].
 *
 * Renewals are read, not scored: there is no Done or Missed here, a
 * subscription simply renews on its date whether the screen was ever opened.
 * What the screen adds up is what actually falls inside the month being
 * looked at, not a forced monthly-equivalent conversion of every cadence.
 */
class MoneyViewModel(
    private val repository: MoneyRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val _state = MutableStateFlow(MoneyUiState())
    val state: StateFlow<MoneyUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch { render() }
    }

    fun onAdd(name: String, amount: Double, cadence: Cadence, day: Int) {
        if (name.isBlank() || amount <= 0.0) return
        val recurrence = when (cadence) {
            Cadence.Monthly -> Recurrence.MonthlyByDay(day.coerceIn(1, 31))
            Cadence.Yearly -> Recurrence.MonthlyByDay(day.coerceIn(1, 31), everyMonths = 12)
            Cadence.Weekly -> Recurrence.Weekly(setOf(DayOfWeek.of(day.coerceIn(1, 7))))
        }
        scope.launch {
            repository.add(name.trim(), amount, recurrence, today())
            render()
        }
    }

    fun onRemove(id: String) {
        scope.launch {
            repository.archive(id)
            render()
        }
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()

    private suspend fun render() {
        val today = today()
        val subs = repository.active()
        // A year out is enough to give every subscription, however it is
        // paced, a real next date rather than none.
        val nextDate = repository.upcoming(today, today.plusYears(1))
            .groupBy { it.subscriptionId }
            .mapValues { (_, renewals) -> renewals.minOf { it.date } }

        _state.value = MoneyUiState(
            subscriptions = subs
                .map { s ->
                    MoneyUiState.Subscription(
                        id = s.id,
                        name = s.name,
                        amountLabel = formatAmount(s.amount),
                        nextRenewal = nextDate[s.id],
                        nextRenewalLabel = nextDate[s.id]?.let(::formatDate) ?: "NO DATE YET",
                    )
                }
                .sortedBy { it.nextRenewal ?: LocalDate.MAX },
            monthlyTotalLabel = if (subs.isEmpty()) null else formatAmount(repository.monthlyTotal(today)),
        )
    }

    private fun formatAmount(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)

    private fun formatDate(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)).uppercase()
}

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class MoneyUiState(
    val subscriptions: List<Subscription> = emptyList(),
    /** Null when there is nothing to add up. Zero is not the same as nothing. */
    val monthlyTotalLabel: String? = null,
) {
    data class Subscription(
        val id: String,
        val name: String,
        val amountLabel: String,
        val nextRenewal: LocalDate?,
        val nextRenewalLabel: String,
    )
}
