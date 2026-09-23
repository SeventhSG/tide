package app.tide.ask

/**
 * The seam a real model plugs into.
 *
 * Nothing implements this yet. Real on-device generation needs llama.cpp,
 * which means vendoring its C++ source and cross-compiling it with the NDK
 * for every ABI Tide ships, and there is no llama.cpp Android library
 * published anywhere that would avoid that: every project doing this hand
 * rolls the same JNI bridge. Building that blind, on a machine with no
 * emulator and no device to load the result on, is exactly how a native
 * library ends up crashing on the one phone that finally runs it. [Ask]
 * says this plainly rather than pretending a stub means it works.
 *
 * What is real without it: [TrainingTools] answers genuine questions from
 * the database today, and [AskViewModel] routes chat messages to them. This
 * interface is where an engine's replies would join that same conversation,
 * without anything above it needing to change.
 */
interface InferenceEngine {
    val isReady: Boolean

    suspend fun reply(message: String, history: List<String>): String
}
