package app.tide

import app.tide.core.data.db.ExerciseEntity
import app.tide.core.data.db.Muscle
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Joins [ExercisePickerScreen] to the library in [TrainingRepository].
 *
 * Search runs in SQL rather than over a list held here. Thirty-eight exercises
 * would filter fine in memory, but the library this screen is built for is
 * about 1,300, and a screen that only works at the small size is a screen that
 * gets rewritten.
 *
 * Nothing here is ranked by anything invented. The recent section is the last
 * time each lift was actually trained, and the rest is the library in its own
 * order.
 */
class ExercisePickerViewModel(
    private val repository: TrainingRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(ExercisePickerUiState())
    val state: StateFlow<ExercisePickerUiState> = _state.asStateFlow()

    private var query = ""
    private var libraryCount = 0
    private var lastTrained: Map<String, Long> = emptyMap()
    private var inSession = false

    init {
        scope.launch {
            libraryCount = repository.library().size
            lastTrained = repository.lastTrainedByExercise()
            load()
        }
        scope.launch {
            repository.observeActiveSession().collect {
                inSession = it != null
                render(_state.value.sections)
            }
        }
    }

    fun onQueryChange(text: String) {
        query = text
        scope.launch { load() }
    }

    private suspend fun load() {
        val typed = query
        val matches = repository.library(typed)
        // The query can have moved on while the database was answering. The
        // stale answer is dropped rather than flashing over what was typed.
        if (typed != query) return
        render(if (typed.isBlank()) browse(matches) else results(matches))
    }

    /** The library with a recent section on top, then grouped by primary muscle. */
    private fun browse(all: List<ExerciseEntity>): List<ExercisePickerUiState.Section> {
        val sections = mutableListOf<ExercisePickerUiState.Section>()
        val recent = all
            .filter { lastTrained.containsKey(it.id) }
            .sortedByDescending { lastTrained.getValue(it.id) }
            .take(RECENT_COUNT)
        if (recent.isNotEmpty()) {
            sections += ExercisePickerUiState.Section("RECENT", recent.map { row(it, withMuscle = true) })
        }
        all.groupBy { it.primaryMuscle }
            .toSortedMap(compareBy { Muscle.entries.indexOf(it) })
            .forEach { (muscle, exercises) ->
                sections += ExercisePickerUiState.Section(
                    title = muscleLabel(muscle).uppercase(),
                    rows = exercises.sortedBy { it.name }.map { row(it, withMuscle = false) },
                )
            }
        return sections
    }

    private fun results(matches: List<ExerciseEntity>): List<ExercisePickerUiState.Section> {
        if (matches.isEmpty()) return emptyList()
        val label = if (matches.size == 1) "1 MATCH" else "${matches.size} MATCHES"
        return listOf(
            ExercisePickerUiState.Section(label, matches.map { row(it, withMuscle = true) }),
        )
    }

    private fun row(e: ExerciseEntity, withMuscle: Boolean): ExercisePickerUiState.Row {
        val equipment = e.equipment.name
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .uppercase()
        return ExercisePickerUiState.Row(
            id = e.id,
            name = e.name,
            detail = if (withMuscle) "$equipment, ${muscleLabel(e.primaryMuscle).uppercase()}" else equipment,
            lastTrained = lastTrained[e.id]?.let { sinceLabel(now() - it) },
        )
    }

    private fun render(sections: List<ExercisePickerUiState.Section>) {
        _state.value = ExercisePickerUiState(
            query = query,
            sections = sections,
            libraryCount = libraryCount,
            searching = query.isNotBlank(),
            inSession = inSession,
        )
    }

    private companion object {
        const val RECENT_COUNT = 5
    }
}

/** Pure view state, so the screen renders in a screenshot test with no database. */
data class ExercisePickerUiState(
    val query: String = "",
    val sections: List<Section> = emptyList(),
    val libraryCount: Int = 0,
    val searching: Boolean = false,
    /** True when a session is already open, so a pick joins it rather than starting one. */
    val inSession: Boolean = false,
) {
    data class Section(val title: String, val rows: List<Row>)

    data class Row(
        val id: String,
        val name: String,
        /** Equipment, and the muscle where the section does not already say it. */
        val detail: String,
        /** Null for a lift with no history. It has never been trained, so nothing is shown. */
        val lastTrained: String?,
    )
}

private fun muscleLabel(muscle: Muscle): String =
    muscle.name
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .let { it.first() + it.drop(1).lowercase() }

/**
 * How long ago, in the coarsest unit that is still true.
 *
 * Days up to a fortnight, then weeks. "TRAINED 34 DAYS AGO" is a precision
 * nobody counts in, and it is the kind of number that reads as a reprimand.
 */
private fun sinceLabel(ms: Long): String {
    val days = ms / 86_400_000L
    return when {
        days <= 0 -> "TODAY"
        days == 1L -> "YESTERDAY"
        days < 14 -> "$days DAYS AGO"
        else -> "${days / 7} WEEKS AGO"
    }
}
