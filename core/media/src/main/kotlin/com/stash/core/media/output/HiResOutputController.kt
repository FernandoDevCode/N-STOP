package com.stash.core.media.output

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.stash.core.data.prefs.HiResOutputMode
import com.stash.core.data.prefs.HiResOutputPreference
import com.stash.core.media.equalizer.EqController
import com.stash.core.media.equalizer.LoudnessController
import com.stash.core.media.equalizer.dsp.dspActivation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The decision for one output stream: what to write to the AudioTrack and
 * what (if anything) to ask of the USB mixer.
 *
 * [platformMixer] is the `AudioMixerAttributes` to apply (API 34+), kept as
 * `Any` so this class loads on every API level.
 */
class OutputPlan internal constructor(
    val decision: MixerDecision,
    val trackFormat: SampleFormat,
    val device: AudioDeviceInfo?,
    internal val platformMixer: Any?,
) {
    val mixerPath: MixerPath = when (decision) {
        is MixerDecision.Configure -> if (decision.bitPerfect) MixerPath.BIT_PERFECT else MixerPath.MATCHED
        is MixerDecision.Shared -> MixerPath.SHARED
    }
    val bitPerfect: Boolean get() = mixerPath == MixerPath.BIT_PERFECT

    /** Two plans with equal keys configure the hardware identically. */
    val key: String = when (decision) {
        is MixerDecision.Configure ->
            "cfg:${device?.id}:${decision.option.index}:${decision.option.sampleRate}:$trackFormat"
        is MixerDecision.Shared -> "shared:${decision.reason}"
    }
}

/** Per-player facts the output stage needs but Media3's output config doesn't carry. */
class OutputSession {
    /** What the player's decoder is being fed (from AnalyticsListener.onAudioInputFormatChanged). */
    @Volatile var source: SourceFormat? = null

    /** The player's current speed; bit-perfect is only possible at 1x. */
    @Volatile var speed: Float = 1f
}

/**
 * Owns Stash's side of Android's audio routing for hi-res output:
 *
 * - **Plans** each new output stream ([prepare]): for a USB DAC on Android 14+
 *   with Hi-Res output on, picks the USB mixer configuration that matches the
 *   song (see [MixerSelection]); otherwise the shared mixer.
 * - **Applies** the plan when that stream starts playing ([activate]) — not
 *   when it is created, because the crossfade's spare player creates its
 *   output seconds before the seam, and reopening the USB output then would
 *   resample the end of the song still playing.
 * - **Re-plans** live streams when the route or the mode changes (a DAC is
 *   plugged in mid-song, the user flips the setting), by asking the stream to
 *   be rebuilt ([StashAudioOutput.requestReplan]).
 * - **Publishes** what is actually happening for the signal-path UI.
 *
 * Mixer preferences are tied to Stash's uid by Android, so they only ever
 * affect Stash's own playback; they are cleared when the mode goes back to
 * Standard and dropped by Android when the DAC is unplugged.
 */
@Singleton
class HiResOutputController @Inject constructor(
    @ApplicationContext context: Context,
    preference: HiResOutputPreference,
    eqController: EqController,
    loudnessController: LoudnessController,
) {
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile var mode: HiResOutputMode = HiResOutputMode.STANDARD
        private set

    /** Hi-res USB output needs Android 14's preferred mixer attributes. */
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= 34

    private val _output = MutableStateFlow<OutputPathState?>(null)

    /** The audible output's path, or null before anything has played. */
    val output: StateFlow<OutputPathState?> = _output.asStateFlow()

    private val _source = MutableStateFlow<SourceFormat?>(null)
    val source: StateFlow<SourceFormat?> = _source.asStateFlow()

    /** Whether any EQ / normalization stage is currently shaping the sound. */
    val dspActive: StateFlow<Boolean> =
        combine(eqController.state, loudnessController.state) { e, l -> dspActivation(e, l).any }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** True while the audible output is bit-perfect: EQ, normalization and crossfade are bypassed. */
    val bitPerfectEngaged: StateFlow<Boolean> =
        output.map { it?.mixer == MixerPath.BIT_PERFECT }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** The listener-facing explanation of [output], ready for the signal-path sheet. */
    val signalPath: StateFlow<SignalVerdict?> =
        combine(source, output, dspActive) { s, o, d -> SignalPathExplainer.explain(s, o, d) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val live = CopyOnWriteArraySet<StashAudioOutput>()
    @Volatile private var active: StashAudioOutput? = null

    /** USB devices Stash has set a mixer preference on: id → what was applied. */
    private val applied = ConcurrentHashMap<Int, Applied>()

    private class Applied(val device: AudioDeviceInfo, val attributes: AudioAttributes, val mixer: Any)

    init {
        scope.launch { preference.mode.collect(::onModeChanged) }
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = recheckLiveOutputs()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                    // Android drops the preference itself when the device goes away.
                    removedDevices.forEach { applied.remove(it.id) }
                    recheckLiveOutputs()
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    /** Plans a new output stream. Side-effect free; called on the playback thread. */
    fun prepare(sampleRate: Int, channelCount: Int, speed: Float, attributes: AudioAttributes): OutputPlan {
        val m = mode
        val device = routedDevice(attributes)
        fun shared(reason: MixerDecision.Reason) =
            OutputPlan(MixerDecision.Shared(reason), SampleFormat.FLOAT, device, null)
        if (m == HiResOutputMode.STANDARD) return shared(MixerDecision.Reason.MODE_STANDARD)
        if (Build.VERSION.SDK_INT < 34) return shared(MixerDecision.Reason.UNSUPPORTED_ANDROID)
        if (attributes.usage != AudioAttributes.USAGE_MEDIA) return shared(MixerDecision.Reason.NOT_MEDIA_USAGE)
        if (device == null || !device.isUsb()) return shared(MixerDecision.Reason.NOT_USB)
        return MixerApi34.plan(audioManager, device, m, sampleRate, channelCount, speed)
    }

    /**
     * Applies [output]'s plan to the USB mixer and makes it the published,
     * audible output. Called from [StashAudioOutput.play] just before the
     * AudioTrack starts. A rejected preference degrades the stream to the
     * shared mixer rather than failing playback.
     */
    fun activate(output: StashAudioOutput) {
        val plan = output.plan
        val device = plan.device
        if (Build.VERSION.SDK_INT >= 34 && device != null) {
            val attributes = output.platformAttributes
            val ok = when (plan.decision) {
                is MixerDecision.Configure -> applyMixer(device, attributes, plan.platformMixer!!)
                is MixerDecision.Shared -> { clearMixer(device); true }
            }
            if (!ok) output.onMixerRejected()
        }
        active = output
        _output.value = output.pathState()
        output.session.source?.let { _source.value = it }
    }

    fun register(output: StashAudioOutput) { live += output }

    fun unregister(output: StashAudioOutput) {
        live -= output
        if (active === output) active = null
    }

    /** The player behind [session] started decoding a new source. */
    fun onSourceFormat(session: OutputSession, source: SourceFormat) {
        session.source = source
        if (active?.session === session) _source.value = source
    }

    /** [output]'s AudioTrack moved to another device (playback thread). */
    fun onRouted(output: StashAudioOutput) {
        if (active === output) _output.value = output.pathState()
        recheck(output)
    }

    /** [output]'s speed changed; bit-perfect can only hold at 1x. */
    fun onSpeedChanged(output: StashAudioOutput) = recheck(output)

    /** Clears every mixer preference Stash set (service shutdown). */
    fun releaseAll() {
        if (Build.VERSION.SDK_INT < 34) return
        applied.keys.toList().forEach { id -> applied[id]?.let { clearMixer(it.device) } }
    }

    private fun onModeChanged(newMode: HiResOutputMode) {
        if (newMode == mode) return
        mode = newMode
        if (newMode == HiResOutputMode.STANDARD) releaseAll()
        recheckLiveOutputs()
    }

    private fun recheckLiveOutputs() = live.forEach(::recheck)

    private fun recheck(output: StashAudioOutput) {
        val fresh = runCatching {
            prepare(output.sampleRate, output.channelCount, output.session.speed, output.platformAttributes)
        }.getOrNull() ?: return
        if (fresh.key != output.plan.key) output.requestReplan()
    }

    @RequiresApi(34)
    private fun applyMixer(device: AudioDeviceInfo, attributes: AudioAttributes, mixer: Any): Boolean {
        val current = applied[device.id]
        if (current != null && current.mixer == mixer) return true
        val ok = try {
            audioManager.setPreferredMixerAttributes(attributes, device, mixer as AudioMixerAttributes)
        } catch (e: RuntimeException) {
            Log.w(TAG, "setPreferredMixerAttributes failed", e)
            false
        }
        if (ok) applied[device.id] = Applied(device, attributes, mixer)
        else applied.remove(device.id)
        Log.i(TAG, "mixer preference ${if (ok) "set" else "REJECTED"}: $mixer on ${device.productName}")
        return ok
    }

    @RequiresApi(34)
    private fun clearMixer(device: AudioDeviceInfo) {
        val current = applied.remove(device.id) ?: return
        try {
            audioManager.clearPreferredMixerAttributes(current.attributes, device)
        } catch (e: RuntimeException) {
            Log.w(TAG, "clearPreferredMixerAttributes failed", e)
        }
    }

    private fun routedDevice(attributes: AudioAttributes): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { audioManager.getAudioDevicesForAttributes(attributes).firstOrNull() }.getOrNull()
        } else {
            null
        }

    /** The shared mixer's rate for music right now (what Android resamples to). */
    internal fun sharedMixerSampleRate(): Int? =
        runCatching { AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) }
            .getOrNull()?.takeIf { it > 0 }

    companion object {
        private const val TAG = "HiResOutput"

        fun AudioDeviceInfo.isUsb(): Boolean = type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_USB_ACCESSORY

        fun AudioDeviceInfo.toOutputDevice(): OutputDevice {
            val kind = when (type) {
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputDeviceKind.USB
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutputDeviceKind.BLUETOOTH
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE -> OutputDeviceKind.WIRED
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputDeviceKind.SPEAKER
                else -> OutputDeviceKind.OTHER
            }
            val name = when (kind) {
                OutputDeviceKind.SPEAKER -> "Phone speaker"
                OutputDeviceKind.WIRED -> "Wired headphones"
                else -> productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio device"
            }
            return OutputDevice(kind, name)
        }

        internal fun platformEncodingOf(format: SampleFormat): Int = when (format) {
            SampleFormat.INT16 -> AudioFormat.ENCODING_PCM_16BIT
            SampleFormat.INT24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            SampleFormat.INT32 -> AudioFormat.ENCODING_PCM_32BIT
            SampleFormat.FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
        }
    }
}

/** Everything that touches API 34 classes, isolated so older devices never load it. */
@RequiresApi(34)
private object MixerApi34 {
    fun plan(
        audioManager: AudioManager,
        device: AudioDeviceInfo,
        mode: HiResOutputMode,
        sampleRate: Int,
        channelCount: Int,
        speed: Float,
    ): OutputPlan {
        val platform: List<AudioMixerAttributes> =
            runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
        val options = platform.mapIndexed { i, a ->
            val f = a.format
            MixerOption(
                format = when (f.encoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> SampleFormat.INT16
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> SampleFormat.INT24
                    AudioFormat.ENCODING_PCM_32BIT -> SampleFormat.INT32
                    AudioFormat.ENCODING_PCM_FLOAT -> SampleFormat.FLOAT
                    else -> null // e.g. 8.24 fixed point: not something an AudioTrack takes from us
                },
                sampleRate = f.sampleRate,
                channelCount = f.channelCount,
                bitPerfect = a.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT,
                index = i,
            )
        }
        return when (val d = MixerSelection.choose(options, mode, sampleRate, channelCount, speed)) {
            is MixerDecision.Configure -> OutputPlan(d, d.trackFormat, device, platform[d.option.index])
            is MixerDecision.Shared -> OutputPlan(d, SampleFormat.FLOAT, device, null)
        }
    }
}
