package app.tide

import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The training hub: what the last month looks like, and a way into everything
 * else in the module.
 *
 * Four figures, all of them counted rather than modelled: sessions, working
 * sets, volume and the heaviest estimated 1RM. No score, no readiness, no
 * grade. Each one is a number you could arrive at yourself from the log, which
 * is the test for whether it belongs on this screen.
 */
class TrainViewModel(
    private val repository: TrainingRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val windowDays: Int = 28,
) {
    private val _state = MutableStateFlow(TrainUiState())
    val state: StateFlow<TrainUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            val to = now()
            val from = to - windowDays * DAY_MS
            val sessions = repository.sessionsBetween(from, to).filter { it.endedAt != null }
            val sets = repository.workingSetCountBetween(from, to)
            val volume = repository.volumeBetween(from, to)
            val active = repository.activeSession()

            val lastTrained = repository.lastTrainedByExercise().values.maxOrNull()
            val today = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()

            _state.value = TrainUiState(
                windowLabel = "LAST $windowDays DAYS",
                sessions = sessions.size,
                workingSets = sets,
                // Absent rather than zero. A month with no training has no
                // volume to report, and "0 kg" reads as a measurement.
                volume = if (volume > 0) "${volume.toInt().grouped()} kg" else null,
                lastTrainedLabel = lastTrained?.let { label(it, today) },
                sessionOpen = active != null,
                hasHistory = sessions.isNotEmpty() || active != null,
            )
        }
    }

    private fun label(lastAt: Long, today: LocalDate): String {
        val date = Instant.ofEpochMilli(lastAt).atZone(zone).toLocalDate()
        val days = today.toEpochDay() - date.toEpochDay()
        return when {
            days <= 0L -> "TODAY"
            days == 1L -> "YESTERDAY"
            days < 14L -> "$days DAYS AGO"
            else -> "${days / 7} WEEKS AGO"
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class TrainUiState(
    val windowLabel: String = "",
    val sessions: Int = 0,
    val workingSets: Int = 0,
    /** Null when nothing was lifted in the window. */
    val volume: String? = null,
    /** Null when nothing has ever been trained. */
    val lastTrainedLabel: String? = null,
    val sessionOpen: Boolean = false,
    val hasHistory: Boolean = false,
)

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(" ").reversed()
