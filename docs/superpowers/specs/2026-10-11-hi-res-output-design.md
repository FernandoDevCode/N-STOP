# Hi-Res output: float pipeline, USB sample-rate matching and bit-perfect

Date: 2026-10-11 · Status: implemented (phase 1–3), phase 4 proposed

## The problem, stage by stage

What happened to a 24-bit / 96 kHz Qobuz FLAC before this change:

| Stage | What it did | Loss |
|---|---|---|
| Decoder (MediaCodec FLAC) | Asked for 16-bit, because the sink had float output off | **24 → 16 bit, truncated, no dither** |
| Stash DSP (5 Media3 AudioProcessors) | Each stage: int16 → float → int16 with `toInt()` | Up to **5 more truncations**, float32 biquads |
| Limiter | 2 ms lookahead ring, never drained | **Last 2 ms of every track dropped**, 2 ms silence after every seek |
| AudioTrack | 16-bit at 96 kHz | — |
| AudioFlinger mixer | Runs at its own rate (usually 48 kHz) | **96 → 48 kHz resample** |
| HAL → DAC | — | — |

Why float output was off: Media3's `DefaultAudioSink` only runs user-supplied
`AudioProcessor`s on its 16-bit branch. With `setEnableFloatOutput(true)` it sends
high-resolution PCM down a branch that skips them (`DefaultAudioSink.configure`,
1.9.2 lines 737–743), so the EQ would have silently stopped working.

"The DAC downsamples" is a common reading of this, but the phone's DAC isn't the
culprit. Android's **mixer** resamples. Even the internal DAC usually runs at 48 kHz,
and USB DACs are opened at whatever rate the audio policy picks.

## Options considered

1. **Turn float output on, keep Media3 processors.** One line, but the EQ is
   silently bypassed for every float stream. Rejected.
2. **ForwardingAudioSink with our own pipeline in front of `DefaultAudioSink`.**
   Works, but means re-implementing `handleBuffer` / drain / position semantics
   around Media3's contract. More code on the riskiest seam.
3. **Fork `DefaultAudioSink`.** 2,100 lines of package-private dependencies to
   maintain across Media3 upgrades. Rejected.
4. **Own the `AudioOutput` (Media3 1.9's `AudioOutputProvider` seam).** ✅ Chosen.
   `DefaultAudioSink` stays stock (float on); our `AudioOutput` sits between it
   and the `AudioTrack` and does DSP, format conversion and mixer configuration.
   Contract to honour: `write()` advances the input buffer only by bytes actually
   consumed, so we advance by whole frames the AudioTrack accepted
   (`ConvertingWriter`). All stages are zero-latency, so frames stay 1:1.
5. **Userspace USB Audio Class driver** (libusb + isochronous transfers over
   `UsbDeviceConnection`'s fd, as USB Audio Player Pro does). The only route
   past AudioFlinger on Android 8–13. Deferred to phase 4, see below.

And for the mixer itself:

- **Android 14 preferred mixer attributes** (`AudioManager.setPreferredMixerAttributes`,
  USB only, `USAGE_MEDIA`, tied to our uid). Rate-matched (default behaviour)
  support is mandatory for USB on Android 14. Bit-perfect is optional; it's
  confirmed on Pixel 8/9 after their updates (androidx/media#415). ✅ Chosen.
- **Direct PCM outputs on the internal DAC.** A plain app can't force these;
  AudioPolicy only opens a direct PCM output when the stream asks for one, and
  vendors decide. Not pursued; the signal path reports the shared mixer rate.

## Design (as built)

```
Decoder ──float──▶ DefaultAudioSink (float on, speed via AudioTrack params)
                      │ trimming, channel mapping
                      ▼
               StashAudioOutput  ◀── HiResOutputController (plan / apply / replan / publish)
                 1. FloatDspChain (preamp → 5-band EQ → bass shelf → loudness → limiter),
                    only while a stage is active; idle = zero-copy passthrough
                 2. ConvertingWriter: float → track format (float, or the DAC's int format
                    for bit-perfect), TPDF dither only for samples off the grid
                 3. bit-perfect: player volume applied here, track volume held at 1.0
                      ▼
               AudioTrack (song's own rate) ──▶ AudioFlinger ──▶ USB DAC
```

- **Modes** (`HiResOutputMode`, Settings › Audio & Quality › Hi-Res Output):
  Standard; Match USB DAC to each song; Bit-perfect USB (confirmation dialog
  with a volume warning). Has no effect below Android 14 or off USB.
- **Plan vs apply.** `prepare()` is side-effect free (called when an output is
  created); `activate()` applies the mixer preference when the output *starts
  playing*. The crossfade spare creates its output seconds early, and reopening
  the USB output then would resample the end of the song still playing.
- **Format choice** (`MixerSelection`): exact sample rate only (a "nearby"
  rate is just resampling under another name); bit-perfect needs exact channel
  count and 1x speed, else falls back to rate matching; widest format wins
  (32 > 24 > float > 16).
- **Re-planning.** Route change (DAC plugged in mid-song), mode change, or a
  speed change that breaks bit-perfect → the output throws a *recoverable*
  `WriteException` once, after at least one frame was written (earlier is
  fatal in `DefaultAudioSink`). ExoPlayer then rebuilds the output in place,
  the same path it uses for a dead AudioTrack. Only a hardware change
  rebuilds: shared plans with the same track format are equal whatever their
  reason, and a configuration Android or the DAC refused is remembered
  (`RefusedConfigurations`, per device) until a replug or a mode change, so
  a recheck never rebuilds a stream into the same refusal again.
- **Bit-perfect side effects.** EQ, normalization and crossfade step aside;
  the Equalizer screen says so, and the service holds crossfade off while
  `bitPerfectEngaged`.
- **Listen Together holds bit-perfect off** (`HiResOutputController.setBitPerfectHeld`,
  set in `enterSession`, cleared in `exitSession` and `onDestroy`). Drift
  correction nudges the speed between 0.97x and 1.03x; with bit-perfect only
  possible at exactly 1x, each nudge flipped the plan between bit-perfect and
  matched and rebuilt the stream (a re-seek), which made more drift. Held, the
  planner picks rate matching at every speed, so a nudge leaves the plan key
  alone. The signal path says "bit-perfect is off during Listen Together".
- **Main playback only.** Every player built on `StashRenderersFactory` gets
  Stash's EQ, but only the service's two (`OutputSession.mainPlayback`) are
  planned, applied and published. The search preview plays over the shared
  mixer: it used to become the "audible" output, retune a bit-perfect DAC to
  its own rate under the paused song, and leave its path (and crossfade's
  bit-perfect hold) published after it ended.
- **A released stream's path is cleared** after a 1 s grace
  (`RELEASE_GRACE_MS`) unless another stream starts first. Rebuilds and
  rate changes between songs release one stream just before the next one
  starts, so they don't blink the sheet or flick the crossfade hold; a stop
  clears "Bit-perfect" from the Equalizer screen and lets crossfade back.
- **Buffer sizing** (`TrackBufferSizing`). Speed now goes through
  `AudioTrack.setPlaybackParams` (Sonic lives in the processor chain the
  float branch skips), and AudioFlinger's time-stretch reads `speed` frames
  per frame played. Media3 1.9.2 (`DefaultAudioTrackBufferSizeProvider`)
  sizes PCM at `max(minBuffer, clamp(4 × minBuffer, 250 ms, 750 ms) × maxSpeed)`
  and asks for `maxSpeed = 8` once the AudioTrack time-stretches: 2–6 s. The
  first fix capped it at 2x, which still doubled 1x to 500–1500 ms: EQ moves
  took that long to be heard, and after a gapless change the next song's
  first ~0.5 s played at the previous song's loudness gain. Now:
  - `DefaultAudioSink` is always given the **1x size** (250–750 ms). It
    compares configs, buffer size included, to decide whether the next song
    can keep the track, so this size must not move with the speed.
  - The real AudioTrack gets the 1x size **up to 2x** and **double above 2x**
    (Stash's top speed is 4x). At the top of each step the buffer still lasts
    125–375 ms of real time, the same as the device-verified 4x on the
    doubled buffer, and holds at least twice what AudioFlinger demands before
    it accepts a speed (≈ speed × the AudioTrack minimum; below that
    `setPlaybackParams` is refused and Media3 only logs it).
  - A speed change across 2x rebuilds the track at the other size. Listen
    Together's nudges and everyday 0.5–2x never do.
- **Per-player loudness.** Each player's output session carries its own
  song's gain, so a crossfade no longer fades in at the wrong level and steps
  at the swap.
- **Signal path.** `SignalPathExplainer` turns source + output + DSP state into
  stages and a verdict (Bit-perfect / Hi-Res, not resampled / Lossless + your EQ /
  Resampled to 48 kHz / Lossy source), shown on Now Playing (tap the quality
  line) and in Settings. When Stash set the USB mixer's format, the Android
  line names it, since it is what the DAC finally gets ("Matched to 48 kHz /
  16-bit, not resampled"). A mixer narrower than the song is flagged
  ("Matched to 48 kHz / 16-bit (your DAC's maximum)"), and "Hi-Res" is only
  claimed for what reaches the DAC.

## Focus and explicit pauses

A call's transient focus loss pauses the master (`playWhenReady = false`) and
its end resumes it. A pause that lands during the call (the sleep timer, a
headset or car button) is a no-op on an already-paused ExoPlayer and raises no
event, so the resume used to undo it. `onPlayerInteractionFinished` in the
session callback sees every controller play/pause and calls
`CrossfadeEngine.onPlayPauseRequested()`, which drops the pending resume (and
hands a fade back to the outgoing song). A transient loss only marks a resume
when something was actually playing, and a paused Stash gives focus back when
the call ends.

## Verification

- JVM: `PcmCodecTest` (bit-exact int16/int24 round trips across the full
  range, dither statistics, relock), `ConvertingWriterTest` (partial-write
  accounting), `FloatDspChainTest` (gain at band centre at 96/192 kHz,
  above-Nyquist bands, ramps, limiter ceiling, zero latency),
  `MixerSelectionTest` (incl. the Listen Together hold: equal plan keys at
  0.97/1.0/1.03x), `SignalPathExplainerTest` (incl. the mixer's own format),
  `StashAudioOutputTest` (zero-copy passthrough, bit-perfect exact image,
  software volume, replan, speed tiers), `TrackBufferSizingTest`,
  `HiResOutputControllerTest` (preview ignored, release grace),
  `CrossfadeEngineFocusTest` (a pause during a call sticks).
- Device checks still needed (no hardware in CI):
  1. Pixel 8+/Android 15 with a USB DAC whose UI shows the incoming rate:
     44.1 / 96 / 192 kHz songs → DAC display follows in both hi-res modes.
  2. Bit-perfect: `adb shell dumpsys media.audio_flinger` shows the bit-perfect
     output thread with our track. Volume keys move the DAC's hardware volume.
  3. Plug the DAC in mid-song → brief re-seek, then the matched rate.
  4. EQ slider latency stays well under a second; crossfade still works in Match mode.
  5. Bit-perfect + Listen Together: one re-seek on joining, then none while
     drift correction runs (`HiResOutput: bit-perfect held off` in logcat).
  6. Bit-perfect, song paused, play a search preview: the DAC's rate doesn't
     change and the signal path still shows the song. Check the preview plays
     at all while the bit-perfect preference stays applied.
  7. Speed 1x → 3x → 1x: one re-seek at each crossing of 2x, and 3x/4x
     actually play at that speed (no "Failed to set playback params" in logcat).

## Phase 4 (proposed): userspace USB driver for Android 8–13

What it takes, why it isn't in this change, and the safety bar:

- NDK module: libusb (`libusb_wrap_sys_device` on `UsbDeviceConnection.fileDescriptor`),
  UAC1/UAC2 descriptor parsing, clock-source and alt-setting selection,
  isochronous OUT with asynchronous feedback-endpoint rate adaptation,
  hot-unplug, and the USB permission flow.
- **Hearing safety:** it bypasses Android's volume entirely. It must drive the
  DAC's Feature Unit volume (and refuse to start without one, or start at a
  low software gain), and ramp in on start.
- Needs a hardware test matrix (UAC1 and UAC2, adaptive and async, 16/24/32-bit,
  44.1–384 kHz) before shipping. It can't be built or tested here; the NDK
  and the devices are both missing.
- Android 14+ users get the same outcome from phases 1–3 with none of these risks.
