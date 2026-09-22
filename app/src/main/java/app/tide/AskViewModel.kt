package app.tide

import app.tide.ask.InstallState
import app.tide.ask.ModelInstaller
import app.tide.ask.ModelSpec
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
) {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AskUiState> = _state.asStateFlow()

    private var installJob: Job? = null

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
        _state.value = read()
    }

    fun refresh() {
        if (installJob?.isActive == true) return
        _state.value = read()
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
)
