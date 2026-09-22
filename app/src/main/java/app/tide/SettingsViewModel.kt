package app.tide

import app.tide.core.notify.Notifier
import app.tide.core.notify.Tier
import app.tide.core.notify.TideNotification
import app.tide.notify.NotifyPreferences
import app.tide.sound.OceanSoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Joins [SettingsScreen] to [NotifyPreferences] and the notifier.
 *
 * Every change is written through immediately and the digest is rescheduled
 * with it: a settings screen that needs a save button is a settings screen that
 * loses changes.
 */
class SettingsViewModel(
    private val prefs: NotifyPreferences,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
    /** Called whenever the digest time or its on/off state changes. */
    private val onScheduleChanged: (enabled: Boolean, at: LocalTime) -> Unit,
    /** Null in tests, which have no audio device and must never make a sound. */
    private val sound: OceanSoundPlayer? = null,
    private val now: () -> Instant = Instant::now,
) {
    private var soundVolume = 0.5f
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onToggleDigest() {
        prefs.digestEnabled = !prefs.digestEnabled
        applySchedule()
    }

    /** Whole hours. Nobody needs a digest at 08:05, and a minute picker is a fiddle. */
    fun onDigestEarlier() {
        prefs.digestAt = prefs.digestAt.minusHours(1)
        applySchedule()
    }

    fun onDigestLater() {
        prefs.digestAt = prefs.digestAt.plusHours(1)
        applySchedule()
    }

    fun onToggleQuietHours() {
        prefs.quietHoursEnabled = !prefs.quietHoursEnabled
        render()
    }

    fun onQuietStartEarlier() {
        prefs.quietStart = prefs.quietStart.minusHours(1)
        render()
    }

    fun onQuietStartLater() {
        prefs.quietStart = prefs.quietStart.plusHours(1)
        render()
    }

    fun onQuietEndEarlier() {
        prefs.quietEnd = prefs.quietEnd.minusHours(1)
        render()
    }

    fun onQuietEndLater() {
        prefs.quietEnd = prefs.quietEnd.plusHours(1)
        render()
    }

    /**
     * Starts or stops the sea.
     *
     * Only ever from this press. Nothing in Tide starts audio on its own: not
     * at launch, not when a session starts, and not when a notification
     * arrives.
     */
    fun onToggleSound() {
        val player = sound ?: return
        if (player.isPlaying) player.stop() else player.start()
        render()
    }

    fun onSoundQuieter() = setVolume(soundVolume - 0.1f)

    fun onSoundLouder() = setVolume(soundVolume + 0.1f)

    private fun setVolume(value: Float) {
        soundVolume = value.coerceIn(0f, 1f)
        sound?.volume = soundVolume
        render()
    }

    /** Called when the screen leaves, so the sea does not outlive it. */
    fun onStopSound() {
        sound?.stop()
        render()
    }

    /** Called when the screen returns, since the permission may have changed outside it. */
    fun refresh() = render()

    /**
     * Posts one, now, so it can be seen landing on a real phone.
     *
     * On the quiet channel, which never makes a sound, and through the same
     * [Notifier.post] as everything else so it is written to the ledger rather
     * than being a special path that proves nothing about the real one.
     */
    fun onSendTest() {
        scope.launch {
            val posted = notifier.post(
                TideNotification(
                    key = "test.${now().toEpochMilli()}",
                    title = "Tide",
                    body = "This is what a summary looks like. It made no sound.",
                    tier = Tier.Quiet,
                    createdAt = now(),
                ),
            )
            _state.value = _state.value.copy(
                permissionGranted = notifier.canPost(),
                lastTestResult = if (posted) {
                    "SENT"
                } else {
                    "NOT SENT, ANDROID REFUSED IT"
                },
            )
        }
    }

    private fun applySchedule() {
        onScheduleChanged(prefs.digestEnabled, prefs.digestAt)
        render()
    }

    private fun render() {
        _state.value = read().copy(lastTestResult = _state.value.lastTestResult)
    }

    private fun read() = SettingsUiState(
        digestEnabled = prefs.digestEnabled,
        digestAt = prefs.digestAt.format(HourMinute),
        quietHoursEnabled = prefs.quietHoursEnabled,
        quietStart = prefs.quietStart.format(HourMinute),
        quietEnd = prefs.quietEnd.format(HourMinute),
        permissionGranted = notifier.canPost(),
        soundPlaying = sound?.isPlaying == true,
        soundVolume = "${(soundVolume * 100).toInt()}%",
    )

    private companion object {
        val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** Pure view state, so the screen renders in a screenshot test with no preferences. */
data class SettingsUiState(
    val digestEnabled: Boolean = false,
    val digestAt: String = "08:00",
    val quietHoursEnabled: Boolean = true,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
    val permissionGranted: Boolean = false,
    /** What happened to the last test, or null if none was sent this visit. */
    val lastTestResult: String? = null,
    val soundPlaying: Boolean = false,
    val soundVolume: String = "50%",
)
