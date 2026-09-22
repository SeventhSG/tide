package app.tide.ask

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Putting the model on the phone.
 *
 * The assistant is meant to run on the device, which means the weights have to
 * get there. They are not bundled: a model is hundreds of megabytes and most
 * people will never open Ask, so it is fetched once, on purpose, by someone who
 * pressed a button.
 *
 * **This is the one thing in Tide that uses the network**, and it is worth
 * being exact about what that means: the request goes to the host below, it
 * carries no identifier of you, and nothing is sent. After it finishes the app
 * is offline again and the file never leaves the phone.
 *
 * The download resumes: a partial file is kept and continued with a range
 * request rather than restarted, because a 400 MB download over a phone
 * connection will be interrupted.
 */
class ModelInstaller(
    private val context: Context,
    private val model: ModelSpec = ModelSpec.DEFAULT,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {

    val file: File get() = File(context.filesDir, "models/${model.fileName}")

    private val partial: File get() = File(file.parentFile, "${model.fileName}.part")

    fun installed(): Boolean = file.exists() && file.length() >= model.minimumBytes

    /** Frees the space again. The model can always be fetched a second time. */
    fun remove(): Boolean {
        partial.delete()
        return file.delete()
    }

    /**
     * Downloads it, reporting progress as it goes.
     *
     * Emits [Progress] until the file is on disk, then [Done]. Every failure
     * arrives as [Failed] with something a person can act on rather than as a
     * thrown exception: out of space is a different problem from no signal, and
     * the screen says which.
     */
    fun install(): Flow<InstallState> = flow {
        if (installed()) {
            emit(InstallState.Done(file.length()))
            return@flow
        }

        file.parentFile?.mkdirs()
        val already = if (partial.exists()) partial.length() else 0L

        val free = context.filesDir.usableSpace
        if (free < model.approximateBytes - already + HEADROOM_BYTES) {
            emit(InstallState.Failed("Not enough space. About ${model.approximateBytes / MB} MB is needed."))
            return@flow
        }

        val connection = runCatching {
            openConnection(URL(model.url)).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                if (already > 0) setRequestProperty("Range", "bytes=$already-")
            }
        }.getOrElse {
            emit(InstallState.Failed("Could not reach the host. Check the connection."))
            return@flow
        }

        val resuming = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
        if (connection.responseCode != HttpURLConnection.HTTP_OK && !resuming) {
            emit(InstallState.Failed("The host answered ${connection.responseCode}."))
            connection.disconnect()
            return@flow
        }

        val total = connection.contentLengthLong.let { if (it > 0) it + (if (resuming) already else 0) else model.approximateBytes }
        var written = if (resuming) already else 0L
        if (!resuming) partial.delete()

        runCatching {
            connection.inputStream.use { input ->
                java.io.FileOutputStream(partial, resuming).use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        emit(InstallState.Progress(written, total))
                    }
                }
            }
        }.onFailure {
            // The partial file stays. The next attempt continues from here.
            emit(InstallState.Failed("The download stopped. It will continue where it left off."))
            connection.disconnect()
            return@flow
        }

        connection.disconnect()

        if (written < model.minimumBytes) {
            emit(InstallState.Failed("The file arrived incomplete and was not kept."))
            partial.delete()
            return@flow
        }

        partial.renameTo(file)
        emit(InstallState.Done(file.length()))
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val MB = 1024L * 1024L
        /** Never fill the last of someone's storage for a model. */
        const val HEADROOM_BYTES = 256L * 1024L * 1024L
    }
}

/**
 * Which model, and where from.
 *
 * Qwen2.5 0.5B Instruct, quantised, because it is **Apache-2.0**, which is the
 * same licence as this app: the same check the exercise images have to pass.
 * It is small enough to run on a phone and small enough to download over a
 * phone connection, and it is a starting point rather than a final answer.
 */
data class ModelSpec(
    val name: String,
    val fileName: String,
    val url: String,
    val licence: String,
    val approximateBytes: Long,
    /** Below this the file is not a model, whatever the server said. */
    val minimumBytes: Long,
) {
    companion object {
        val DEFAULT = ModelSpec(
            name = "Qwen2.5 0.5B Instruct, Q4_K_M",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/" +
                "qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true",
            licence = "Apache-2.0",
            approximateBytes = 400L * 1024 * 1024,
            minimumBytes = 200L * 1024 * 1024,
        )
    }
}

sealed interface InstallState {
    data class Progress(val bytes: Long, val totalBytes: Long) : InstallState {
        val fraction: Float get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes) else 0f
    }

    data class Done(val bytes: Long) : InstallState

    data class Failed(val reason: String) : InstallState
}
