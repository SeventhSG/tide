package app.tide.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * The sound of the sea, generated rather than recorded.
 *
 * No audio file ships with Tide. A recording of surf is someone's recording,
 * with a licence to read and a few megabytes to carry, and the sea is one of
 * the few sounds a computer can make convincingly from noise: filtered noise is
 * water, and a slow swell over the top of it is waves.
 *
 * So [SurfGenerator] builds the sound a buffer at a time, forever, without
 * repeating: brown noise for the body, a slow sine swell for the sets rolling
 * in, and a little high noise on the crests for foam. It is pure arithmetic and
 * is tested as arithmetic.
 *
 * **Nothing here ever starts on its own.** There is no autoplay on launch, none
 * when a session starts, and none when a notification arrives. It plays when
 * someone presses play and stops when they leave.
 */
class SurfGenerator(
    private val sampleRate: Int = 44_100,
    seed: Int = 11,
) {
    private val random = Random(seed)

    /** Brown noise state. Integrating white noise is what gives water its weight. */
    private var brown = 0f

    /** Where we are in the swell, in seconds. Never reset, so it never loops. */
    private var t = 0.0

    /**
     * Fills [out] with the next stretch of sea.
     *
     * Values are 16 bit signed, the format [AudioTrack] is handed below.
     * [amplitude] is the volume, 0 to 1, applied here rather than by the system
     * so a quiet setting costs nothing at the mixer.
     */
    fun fill(out: ShortArray, amplitude: Float = 0.5f) {
        val step = 1.0 / sampleRate
        for (i in out.indices) {
            // Two swells at incommensurable periods, so the pattern does not
            // repeat on any human timescale.
            val swellA = sin(2 * PI * t / SWELL_SECONDS)
            val swellB = sin(2 * PI * t / (SWELL_SECONDS * 1.61803))
            val swell = ((swellA + swellB * 0.6) / 1.6 + 1.0) / 2.0

            val white = random.nextFloat() * 2f - 1f
            brown = (brown + white * 0.02f).coerceIn(-1f, 1f)

            // Foam: a touch of the unfiltered noise, only near a crest.
            val foam = if (swell > 0.72) white * ((swell - 0.72) / 0.28).toFloat() * 0.25f else 0f

            val body = brown * 3.2f
            val sample = (body + foam) * (0.35f + 0.65f * swell.toFloat()) * amplitude

            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            t += step
        }
    }

    /** How loud the last buffer was, for a test to assert that quiet is quiet. */
    fun peakOf(buffer: ShortArray): Float =
        (buffer.maxOfOrNull { abs(it.toInt()) } ?: 0) / Short.MAX_VALUE.toFloat()

    companion object {
        /** A set every eleven seconds or so, which is a calm ocean swell. */
        const val SWELL_SECONDS = 11.0
    }
}

/**
 * Plays the generated sea, on a thread of its own.
 *
 * Kept deliberately small: start, stop, set the volume. Everything interesting
 * is in [SurfGenerator], which needs no device to test.
 */
class OceanSoundPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    @Volatile var volume: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    val isPlaying: Boolean get() = running

    /**
     * Starts it. Only ever called from a press.
     *
     * Uses the media stream rather than the notification one, so the system
     * volume key the person reaches for is the one that changes it.
     */
    fun start() {
        if (running) return
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(SAMPLE_RATE)

        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audio
        running = true
        audio.play()

        thread = Thread {
            val generator = SurfGenerator(SAMPLE_RATE)
            val buffer = ShortArray(minBuffer / 2)
            while (running) {
                generator.fill(buffer, volume)
                audio.write(buffer, 0, buffer.size)
            }
        }.also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
    }

    /** True when something else holds audio focus, so the button can say so. */
    fun somethingElseIsPlaying(): Boolean {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return manager?.isMusicActive == true && !running
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}
