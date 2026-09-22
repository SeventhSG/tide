package app.tide

import app.tide.core.data.importer.ImportPreview
import app.tide.core.data.importer.TrainingImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Joins [ImportScreen] to [TrainingImporter].
 *
 * Reading the file is injected rather than reached for, so this is testable
 * without a `ContentResolver`, a `Uri`, or a device. The screen never sees a
 * database type: [ImportPreview] is turned into display strings here.
 */
class ImportViewModel(
    private val importer: TrainingImporter,
    private val scope: CoroutineScope,
    /** Returns the file's text, or null if it cannot be read at all. */
    private val readText: suspend (String) -> String?,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var pendingText: String? = null

    fun onFileChosen(uri: String) {
        _state.value = ImportUiState.Reading
        scope.launch {
            val text = runCatching { readText(uri) }.getOrNull()
            if (text.isNullOrBlank()) {
                _state.value = ImportUiState.Failed("That file is empty, or it could not be opened.")
                return@launch
            }

            val preview = runCatching { importer.preview(text) }.getOrNull()
            if (preview == null) {
                _state.value = ImportUiState.Failed(
                    // Says which formats work, because "invalid file" tells
                    // someone nothing about what to do next.
                    "That does not look like a FitNotes or Strong export. " +
                        "Both are CSV files, exported from inside those apps.",
                )
                return@launch
            }
            if (preview.sets == 0) {
                _state.value = ImportUiState.Failed("There were no sets in that file.")
                return@launch
            }

            pendingText = text
            _state.value = preview.toReady()
        }
    }

    fun onConfirm() {
        val text = pendingText ?: return
        scope.launch {
            val summary = runCatching { importer.import(text) }.getOrNull()
            if (summary == null) {
                _state.value = ImportUiState.Failed("The file could not be read a second time.")
                return@launch
            }
            pendingText = null
            _state.value = ImportUiState.Done(
                sessions = summary.sessionsCreated,
                sets = summary.setsImported,
                created = summary.exercisesCreated,
                duplicates = summary.duplicateSessionsSkipped,
                skipped = summary.rowsSkipped.size,
            )
        }
    }

    fun reset() {
        pendingText = null
        _state.value = ImportUiState.Idle
    }

    private fun ImportPreview.toReady() = ImportUiState.Ready(
        format = when (format.name) {
            "FitNotes" -> "FitNotes"
            else -> "Strong"
        },
        span = span(earliest, latest),
        sessions = sessions,
        sets = sets,
        matched = matched.size,
        unmatched = unmatched,
        skipped = rowsSkipped.size,
    )

    /**
     * The months a history covers, to the month and no finer.
     *
     * A day and a time would be invented precision: the export's first row is
     * the first session it holds, not the day the person started training.
     */
    private fun span(from: Long?, to: Long?): String? {
        if (from == null || to == null) return null
        val format = DateTimeFormatter.ofPattern("MMMM yyyy")
        val first = Instant.ofEpochMilli(from).atZone(zone).format(format)
        val last = Instant.ofEpochMilli(to).atZone(zone).format(format)
        return if (first == last) first else "$first to $last"
    }
}
