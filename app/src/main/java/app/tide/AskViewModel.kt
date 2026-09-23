package app.tide

import app.tide.ask.InferenceEngine
import app.tide.ask.InstallState
import app.tide.ask.ModelInstaller
import app.tide.ask.ModelSpec
import app.tide.ask.TrainingTools
import app.tide.core.data.training.TrainingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Ask, and the model behind it.
 *
 * What works today is the install: the weights are fetched to the phone, with
 * progress, resumable, and removable again. **What does not work yet is asking
 * a question**, because the inference engine is not wired up, and the screen
 * says so in those words rather than showing a chat box that answers nothing.
 *
 * Shipping the download first is deliberate. It is the part that has to be
 * right about storage, interruption and consent, and it is the part that can be
 * proved without a model running.
 */
class AskViewModel(
    private val installer: ModelInstaller,
    private val scope: CoroutineScope,
    private val spec: ModelSpec = ModelSpec.DEFAULT,
    /** Null only in tests that are about the download and not the chat. */
    private val repository: TrainingRepository? = null,
    /** Unset until a real engine exists. See [InferenceEngine]. */
    private val engine: InferenceEngine? = null,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AskUiState> = _state.asStateFlow()

    private var installJob: Job? = null

    /**
     * Answers straight from the database, no model involved.
     *
     * Sending a message never blocks on the download above: these tools read
     * SQL, not the model file, so Ask can say something true about your
     * training whether or not anything has been installed. What it cannot do
     * is hold a conversation, and it says that once rather than pretending.
     */
    fun onSend(text: String) {
        val message = text.trim()
        if (message.isBlank()) return
        _state.value = _state.value.copy(
            messages = _state.value.messages + AskUiState.ChatMessage(fromUser = true, text = message),
        )
        scope.launch {
            val reply = respond(message)
            _state.value = _state.value.copy(
                messages = _state.value.messages + AskUiState.ChatMessage(fromUser = false, text = reply),
            )
        }
    }

    private suspend fun respond(message: String): String {
        val repo = repository
            ?: return "There is no training data to read from here."

        engine?.takeIf { it.isReady }?.let {
            return it.reply(message, _state.value.messages.map { m -> m.text })
        }

        val lower = message.lowercase()
        return when {
            "this week" in lower -> TrainingTools.sessionsThisWeek(repo, now())
            "volume" in lower -> TrainingTools.volumeLast7Days(repo, now())
            "balance" in lower || "muscle" in lower -> TrainingTools.muscleBalance(repo)
            "last time" in lower -> {
                val exercise = lower
                    .substringAfter("last time i", lower)
                    .replace(Regex("did|trained|lifted|\\?"), "")
                    .trim()
                if (exercise.isBlank()) {
                    "Which exercise?"
                } else {
                    TrainingTools.lastPerformance(repo, exercise)
                }
            }

            else -> "There is no real conversation yet, only direct lookups. Try asking about " +
                "sessions this week, volume, muscle balance, or \"last time I did <exercise>\"."
        }
    }

    fun onInstall() {
        if (installJob?.isActive == true) return
        installJob = scope.launch {
            _state.value = _state.value.copy(installing = true, error = null, progress = 0f)
            installer.install()
                // Belt and braces: [ModelInstaller] already converts every
                // failure it can name into [InstallState.Failed], but a screen
                // must never crash on a download, so anything that still slips
                // through becomes one more Failed state rather than an
                // uncaught exception reaching this coroutine.
                .catch { emit(InstallState.Failed("Something went wrong. Nothing was kept.")) }
                .collect { update ->
                    _state.value = when (update) {
                        is InstallState.Progress -> _state.value.copy(
                            installing = true,
                            progress = update.fraction,
                            downloadedLabel = "${update.bytes / MB} MB of ${update.totalBytes / MB} MB",
                        )

                        is InstallState.Done -> _state.value.copy(
                            installing = false,
                            installed = true,
                            progress = 1f,
                            downloadedLabel = "${update.bytes / MB} MB on this phone",
                        )

                        is InstallState.Failed -> _state.value.copy(
                            installing = false,
                            error = update.reason,
                        )
                    }
                }
        }
    }

    fun onCancel() {
        installJob?.cancel()
        installJob = null
        // The partial file is kept on purpose: pressing install again continues
        // rather than starting the download over.
        _state.value = _state.value.copy(installing = false, error = "Paused. Install again to continue.")
    }

    fun onRemove() {
        installJob?.cancel()
        installer.remove()
        // Removing the weights does not clear the conversation: chat reads
        // the database, not the model file, and nothing here was answered by
        // the model that is being removed.
        _state.value = read().copy(messages = _state.value.messages)
    }

    fun refresh() {
        if (installJob?.isActive == true) return
        _state.value = read().copy(messages = _state.value.messages)
    }

    private fun read() = AskUiState(
        modelName = spec.name,
        modelLicence = spec.licence,
        approximateSizeLabel = "${spec.approximateBytes / MB} MB",
        installed = installer.installed(),
    )

    private companion object {
        const val MB = 1024L * 1024L
    }
}

/** Pure view state, so the screen renders in a screenshot test with no file. */
data class AskUiState(
    val modelName: String = "",
    val modelLicence: String = "",
    val approximateSizeLabel: String = "",
    val installed: Boolean = false,
    val installing: Boolean = false,
    val progress: Float = 0f,
    val downloadedLabel: String? = null,
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),
) {
    data class ChatMessage(val fromUser: Boolean, val text: String)
}
