package app.tide

import app.tide.body.HealthReadings
import app.tide.body.HealthSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Body, over Health Connect.
 *
 * Tide measures none of this itself. A phone in a pocket counts steps, a watch
 * records sleep and heart rate, a scale writes weight, and Health Connect is
 * where Android keeps all of it. Body reads that and shows it; it writes
 * nothing back and stores nothing of its own.
 *
 * So the screen has four states and all four are real: Health Connect is not on
 * this device, it is there but has not been allowed, it is allowed and has
 * nothing for today, or it has readings. **None of them is filled with a
 * placeholder number.**
 */
class BodyViewModel(
    private val source: HealthSource,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val _state = MutableStateFlow(BodyUiState(loading = true))
    val state: StateFlow<BodyUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Called when actually launching the permission screen throws, which is
     * different from the screen opening and permission being denied: that
     * case already shows correctly as `granted = false`. This is for when
     * nothing opened at all, so pressing the button stops looking like it
     * did nothing.
     */
    fun onConnectFailed() {
        _state.value = _state.value.copy(
            error = "Health Connect did not open. It may need updating from the Play Store.",
        )
    }

    fun refresh() {
        scope.launch {
            // Every call here can throw: the provider can be mid-update, or the
            // permission can be revoked between two lines. A body screen that
            // crashes the app is worse than one that says it cannot read.
            val availability = runCatching { source.availability() }
                .getOrDefault(HealthSource.Availability.NotSupported)

            if (availability != HealthSource.Availability.Available) {
                _state.value = BodyUiState(availability = availability)
                return@launch
            }

            val granted = runCatching { source.hasPermissions() }.getOrDefault(false)
            if (!granted) {
                _state.value = BodyUiState(availability = availability, granted = false)
                return@launch
            }

            val to = Instant.ofEpochMilli(now())
            val from = to.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val readings = runCatching { source.read(from, to) }.getOrDefault(HealthReadings())

            _state.value = BodyUiState(
                availability = availability,
                granted = true,
                steps = readings.steps?.let { it.toInt().grouped() },
                sleep = readings.sleep?.let(::formatSleep),
                heartRate = readings.restingHeartRateBpm?.let { "$it bpm" },
                weight = readings.weightKg?.let { "${trim(it)} kg" },
                weightAge = readings.weightAt?.let { age(it, to) },
            )
        }
    }

    private fun age(at: Instant, now: Instant): String {
        val days = Duration.between(at, now).toDays()
        return when {
            days <= 0 -> "TODAY"
            days == 1L -> "YESTERDAY"
            days < 14 -> "$days DAYS AGO"
            else -> "${days / 7} WEEKS AGO"
        }
    }
}

/** Pure view state, so the screen renders in a screenshot test with no provider. */
data class BodyUiState(
    val availability: HealthSource.Availability = HealthSource.Availability.Available,
    val granted: Boolean = false,
    val loading: Boolean = false,
    /** Each null when Health Connect holds no reading for today. */
    val steps: String? = null,
    val sleep: String? = null,
    val heartRate: String? = null,
    val weight: String? = null,
    val weightAge: String? = null,
    /** Set only when launching the permission screen itself failed to open. */
    val error: String? = null,
) {
    val hasAnyReading: Boolean
        get() = steps != null || sleep != null || heartRate != null || weight != null
}

private fun formatSleep(duration: Duration): String {
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun trim(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.1f", v)

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(" ").reversed()
