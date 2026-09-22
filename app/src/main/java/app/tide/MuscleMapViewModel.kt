package app.tide

import app.tide.core.data.analysis.MuscleReading
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Joins [MuscleMapScreen] to the analysis in [TrainingRepository].
 *
 * The one rule worth restating here, because it would be so easy to break
 * while formatting: **fatigue gets a bar and never a number.** It is an index
 * out of a model, so there is no honest unit to print after it.
 */
class MuscleMapViewModel(
    private val repository: TrainingRepository,
    private val scope: CoroutineScope,
    private val windowDays: Int = 14,
) {
    private val _state = MutableStateFlow(
        MuscleMapUiState(windowLabel = windowLabel(windowDays), view = MuscleView.Balance, rows = emptyList()),
    )
    val state: StateFlow<MuscleMapUiState> = _state.asStateFlow()

    private var readings: List<MuscleReading> = emptyList()

    init {
        scope.launch {
            readings = repository.muscleMap(windowDays)
            render(_state.value.view)
        }
    }

    fun onSelectView(view: MuscleView) = render(view)

    private fun render(view: MuscleView) {
        val rows = when (view) {
            MuscleView.Balance -> balance()
            MuscleView.Fatigue -> fatigue()
            MuscleView.Strength -> strength()
        }
        _state.value = MuscleMapUiState(windowLabel(windowDays), view, rows)
    }

    private fun balance(): List<MuscleMapUiState.Row> {
        val worked = readings.filter { it.volumeKg > 0 }
        val top = worked.maxOfOrNull { it.volumeKg } ?: return emptyList()
        return worked.map {
            MuscleMapUiState.Row(
                muscle = label(it),
                fraction = (it.volumeKg / top).toFloat(),
                value = "${it.volumeDisplayKg.grouped()} kg",
                stale = it.isStale,
            )
        }
    }

    private fun fatigue(): List<MuscleMapUiState.Row> {
        val loaded = readings.filter { it.fatigueIndex > 0 }
        val top = loaded.maxOfOrNull { it.fatigueIndex } ?: return emptyList()
        return loaded.sortedByDescending { it.fatigueIndex }.map {
            MuscleMapUiState.Row(
                muscle = label(it),
                fraction = (it.fatigueIndex / top).toFloat(),
                // Days, which is a fact, rather than a number for the index,
                // which would be a measurement this app cannot make.
                value = it.daysSinceTrained?.let { d -> if (d == 0) "today" else "${d}d" },
                stale = false,
            )
        }
    }

    private fun strength(): List<MuscleMapUiState.Row> {
        val known = readings.filter { it.bestEstimated1rmKg != null }
        val top = known.maxOfOrNull { it.bestEstimated1rmKg!! }
        return readings
            .sortedWith(compareByDescending<MuscleReading> { it.bestEstimated1rmKg ?: -1.0 })
            .map {
                val e1rm = it.bestEstimated1rmKg
                MuscleMapUiState.Row(
                    muscle = label(it),
                    fraction = if (e1rm != null && top != null && top > 0) {
                        (e1rm / top).toFloat()
                    } else {
                        0f
                    },
                    value = buildString {
                        // No estimate for a muscle only ever trained as a
                        // secondary, and nothing invented to fill the gap.
                        e1rm?.let { kg -> append("${kg.roundToInt()} kg") }
                        it.daysSinceTrained?.let { d ->
                            if (isNotEmpty()) append(", ")
                            append(if (d == 0) "today" else "${d}d")
                        }
                    }.ifBlank { null },
                    stale = it.isStale,
                )
            }
    }

    /** `FrontDelts` reads as `Front delts`. */
    private fun label(reading: MuscleReading): String =
        reading.muscle.name
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }
            .let { it.first() + it.drop(1).lowercase() }
}

/**
 * Untouched for a fortnight.
 *
 * A fact, flagged so it can be seen, and the copy around it states it as one.
 * Nothing in this app scolds you for it.
 */
private val MuscleReading.isStale: Boolean
    get() = (daysSinceTrained ?: Int.MAX_VALUE) > 14

private fun windowLabel(days: Int) = "LAST $days DAYS"

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(" ").reversed()
