# NØSTOP — Brand and shared UI checkpoint

Branch: `nonstop` · Date: 2026-10-15

## Completed

- Android 26+ adaptive launcher artwork: original geometric slashed-ring mark, NØSTOP midnight graphite backdrop. Normal and round adaptive launcher resources now reference the new vector.
- Home app wordmark replaced with a responsive `NØSTOP` text brand, using the existing Space Grotesk font and album-aware text color.
- Replaced former purple/cyan main UI theme colors with NØSTOP graphite, botanical lime, muted mint and mineral-white light mode. Light mode uses separate darker accent ink for readable small labels. Legacy `Stash*` Kotlin constant names remain as internal compatibility identifiers.
- Localized reusable UI in `core:ui`: like destinations, stream/download picker, search filter, track option menus, track sharing, and streaming mode description.
- The sharing sheet now chooses the informational-share icon through a boolean parameter rather than inspecting English words, so Portuguese never changes this behavior.
- Music streaming, downloaded file handling, Spotify/YouTube URLs, share-worker and OAuth callbacks were left unchanged intentionally.

## Follow-up and known limitations

- Older pre-Android-26 raster launcher PNGs still contain upstream artwork, but the app currently requires `minSdk = 26`. Thus these legacy resources are not used on supported devices. Android 26+ uses the new adaptive vector, and these legacy PNGs are not a release blocker.
- A full screen-by-screen UX redesign has not been completed yet. This commit establishes the identity, global colors and shared string resources.
- Existing short share links still use the upstream service domain; do not substitute a made-up NØSTOP domain without a working migration plan.
- Podcasts (RSS, following, progress and allowed downloads) are still a separate future checkpoint.
- Secondary screens may still contain hardcoded English strings.
- Do not install an intermediate APK on the user's phone. Wait for the full agreed product scope.

## Verification

Before merging any release or sharing a final APK, run Android debug assemble, module unit tests and shared worker tests, and review CI output. This documentation commit triggers that GitHub Actions verification using `[do test]`.
