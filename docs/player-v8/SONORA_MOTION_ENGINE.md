# Sonora Motion Engine — production integration (2026-11-12)

This documents the complete code path, **not** an assertion of measured artistic quality.

## Scope and architecture

`StashAudioOutput` → `PlaybackVisualEnergy.sample()` (read-only, bounded PCM copy) →
single background `MusicFeatureAnalyzer` (latest-only queue) → immutable
`MusicMotionFeatures` → `MotionProfileCache` (versioned priors only) →
`MusicalMotionSource` → `OrganicMotionField` in `V8Renderer`.

- Analyze only when the V8 playback surface is active and a subscriber exists.
- On the audio callback, read at most 384 mono frames per ~32ms. No FFT, DSP,
  persistence, Compose work, or buffer mutation on the audio thread.
- A single daemon executor processes the latest packet; no unbounded backlog.
- Extract RMS dynamics, six coarse spectral probes, positive spectral changes,
  attack novelty, recurrence and slower dynamics. This is a **causal estimate**,
  not genre recognition, perfect beat tracking, structural segmentation or
  recognition of musical emotion.
- Motion is a continuously interpolated mixture of ethereal, elastic and tense
  behavior. No artist/title or album-specific branches.
- The cover recipe still determines *color*. The audio controls *behavior*.
- The V2 optical progress and Android seek target are unchanged.
- The old reactive sparkle layer is not active when the motion source is present.
- Only save mature tendencies (120+ analyzed packets), not PCM or timed events.
  A cache is a prior and does not fabricate a live beat. Current PCM is required
  to activate music-responsive behavior.
- If PCM is unavailable, suspend music reactivity and show slow neutral movement;
  stop animation when paused, offscreen or reduced motion is requested.

## Explicit invariants

1. Never modify samples sent to the player, DSP configuration or playback state.
2. Never analyze an entire copyrighted stream via a new network service.
3. Keep analysis work off playback and UI threads; bound memory and queue size.
4. Do not derive movement from metadata genre alone or special-case artists.
5. An unrecognized/unsupported output must remain playable and visually stable.
6. Do not call this "musical understanding" without validation on unfamiliar songs.

## Acceptance and remaining device validation

- CI must compile `:core:media`, `:labs:art-lab` and `:feature:nowplaying`.
- Unit tests: silent input, temporal onset, normalization, reset, non-destructive
  buffer sampling, motion interpolation and cache validity.
- Redmi Note 14: check three reference tracks and at least six previously unseen
  tracks; review quiet, busy and transitional passages of each.
- Evaluate headphones, Bluetooth, USB DAC and inactive player; compare with
  reduced-motion Android setting.
- Record average/worst FPS, dropped audio buffers and CPU impact.
- Visual quality and music-to-motion coherence require user approval.
- This PR remains draft; do not merge or publish final release solely on CI success.

This production motion extension was explicitly requested after the original
V8-only Art Lab specification. It changes the opt-in PCM *observation* boundary
only; Media3 write/playback contracts remain untouched.
