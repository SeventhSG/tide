package app.tide

import androidx.test.core.app.ApplicationProvider
import app.tide.ask.InstallState
import app.tide.ask.ModelInstaller
import app.tide.ask.ModelSpec
import app.tide.body.HealthReadings
import app.tide.body.HealthSource
import app.tide.sound.SurfGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Body, Ask and the sea.
 *
 * All three are the kind of thing normally only testable on a device: a health
 * provider, a 400 MB download and an audio output. All three are behind a seam
 * here, so the states that matter can be proved on the JVM.
 */
@RunWith(RobolectricTestRunner::class)
class BodyAskSoundTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = runBlocking {
        scope.coroutineContext.job.cancelAndJoin()
    }

    // --- Body -------------------------------------------------------------

    private class FakeHealth(
        private val availability: HealthSource.Availability = HealthSource.Availability.Available,
        private val granted: Boolean = true,
        private val readings: HealthReadings = HealthReadings(),
    ) : HealthSource {
        override suspend fun availability() = availability
        override suspend fun hasPermissions() = granted
        override suspend fun read(from: Instant, to: Instant) = readings
    }

    private val noon = Instant.parse("2026-09-23T12:00:00Z").toEpochMilli()

    private fun body(source: HealthSource) =
        BodyViewModel(source, scope, now = { noon }, zone = ZoneId.of("UTC"))

    @Test
    fun `no Health Connect on the device is said plainly`() {
        val state = body(FakeHealth(availability = HealthSource.Availability.NotInstalled)).state.value
        assertEquals(HealthSource.Availability.NotInstalled, state.availability)
        assertFalse(state.granted)
        assertFalse(state.hasAnyReading)
    }

    @Test
    fun `installed but not allowed is a different state from empty`() {
        val state = body(FakeHealth(granted = false)).state.value
        assertEquals(HealthSource.Availability.Available, state.availability)
        assertFalse(state.granted)
    }

    @Test
    fun `allowed with nothing recorded shows no numbers at all`() {
        val state = body(FakeHealth(readings = HealthReadings())).state.value
        assertTrue(state.granted)
        assertFalse("an empty day must not be filled in", state.hasAnyReading)
        assertNull(state.steps)
        assertNull(state.sleep)
    }

    @Test
    fun `readings are formatted, and a missing one stays missing`() {
        val state = body(
            FakeHealth(
                readings = HealthReadings(
                    steps = 8420,
                    sleep = Duration.ofHours(6).plusMinutes(41),
                    restingHeartRateBpm = 54,
                    weightKg = 81.4,
                    weightAt = Instant.parse("2026-09-22T07:00:00Z"),
                ),
            ),
        ).state.value

        assertEquals("8 420", state.steps)
        assertEquals("6h 41m", state.sleep)
        assertEquals("54 bpm", state.heartRate)
        assertEquals("81.4 kg", state.weight)
        assertEquals("YESTERDAY", state.weightAge)
    }

    @Test
    fun `a provider that throws leaves the screen standing`() {
        val exploding = object : HealthSource {
            override suspend fun availability() = throw IllegalStateException("provider is updating")
            override suspend fun hasPermissions() = throw IllegalStateException()
            override suspend fun read(from: Instant, to: Instant) = throw IllegalStateException()
        }
        val state = body(exploding).state.value
        assertEquals(HealthSource.Availability.NotSupported, state.availability)
    }

    // --- Ask, and the download --------------------------------------------

    private class FakeConnection(
        private val body: ByteArray,
        private val code: Int = HttpURLConnection.HTTP_OK,
    ) : HttpURLConnection(URL("https://example.invalid/model.gguf")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getContentLengthLong() = body.size.toLong()
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
    }

    private fun installerOver(bytes: ByteArray, spec: ModelSpec): ModelInstaller {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "models").deleteRecursively()
        return ModelInstaller(context, spec) { FakeConnection(bytes) }
    }

    private val tinySpec = ModelSpec(
        name = "Test model",
        fileName = "test.gguf",
        url = "https://example.invalid/model.gguf",
        licence = "Apache-2.0",
        approximateBytes = 1024,
        minimumBytes = 512,
    )

    @Test
    fun `a model that arrives whole is kept, and reported as installed`() = runBlocking {
        val installer = installerOver(ByteArray(2048) { 7 }, tinySpec)
        val states = installer.install().toList()

        assertTrue(states.last() is InstallState.Done)
        assertTrue(installer.installed())
        assertEquals(2048L, installer.file.length())

        // Installing again is a no-op rather than a second download.
        assertTrue(installer.install().toList().single() is InstallState.Done)
    }

    @Test
    fun `a file that arrives too small is not kept`() = runBlocking {
        val installer = installerOver(ByteArray(16), tinySpec)
        val states = installer.install().toList()

        assertTrue(states.last() is InstallState.Failed)
        assertFalse("half a model is not a model", installer.installed())
    }

    @Test
    fun `progress is reported while it downloads`() = runBlocking {
        val installer = installerOver(ByteArray(4096) { 1 }, tinySpec)
        val progress = installer.install().toList().filterIsInstance<InstallState.Progress>()

        assertTrue(progress.isNotEmpty())
        assertTrue("progress only ever moves forward", progress.zipWithNext().all { it.first.bytes <= it.second.bytes })
        assertEquals(1f, progress.last().fraction, 0.001f)
    }

    @Test
    fun `removing it frees the space and the screen goes back to offering it`() = runBlocking {
        val installer = installerOver(ByteArray(2048), tinySpec)
        installer.install().toList()

        val vm = AskViewModel(installer, scope, tinySpec)
        vm.refresh()
        assertTrue(vm.state.value.installed)

        vm.onRemove()
        assertFalse(vm.state.value.installed)
        assertFalse(installer.file.exists())
    }

    // --- The sea ----------------------------------------------------------

    @Test
    fun `silence is silent, and the volume is actually the volume`() {
        val generator = SurfGenerator(sampleRate = 8_000, seed = 3)
        val buffer = ShortArray(8_000)

        generator.fill(buffer, amplitude = 0f)
        assertEquals("zero volume makes no sound at all", 0f, generator.peakOf(buffer), 0.0001f)

        val quiet = SurfGenerator(sampleRate = 8_000, seed = 3)
        val loud = SurfGenerator(sampleRate = 8_000, seed = 3)
        val quietBuffer = ShortArray(8_000)
        val loudBuffer = ShortArray(8_000)
        quiet.fill(quietBuffer, amplitude = 0.2f)
        loud.fill(loudBuffer, amplitude = 0.9f)

        assertTrue(
            "the same sea at a higher volume is louder",
            loud.peakOf(loudBuffer) > quiet.peakOf(quietBuffer),
        )
    }

    @Test
    fun `the swell moves, so it does not read as a loop`() {
        // Two windows a swell apart are different, which is what stops the
        // generated sea sounding like a one second sample on repeat.
        val generator = SurfGenerator(sampleRate = 8_000, seed = 5)
        val first = ShortArray(8_000)
        val later = ShortArray(8_000)
        generator.fill(first, 0.8f)
        repeat(10) { generator.fill(later, 0.8f) }

        val identical = first.zip(later.toList()).count { it.first == it.second }
        assertTrue("a looping buffer would match everywhere", identical < first.size / 4)
    }
}
