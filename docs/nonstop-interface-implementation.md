# NØNStop — interface implementation checkpoint (2026-10-15)

Branch: `nonstop`.

## Approved design implemented in native Compose
- Bottom navigation, in the approved order: Início · Explorar · Rádio · Buscar · Biblioteca.
- Five tabs are real routes. Explore lists available catalog releases, albums and rediscovery mixes; Radio lists the app's actual generated radio/mix playlists, without mock data.
- Old Sync/Settings routes are retained and exposed in the Library overflow.
- Full-width frosted mini-player and tab navigation. Timeline at the mini-player's top edge; real local-liked heart and play/pause, no skip/queue buttons and no outlined play button.
- Home NØNStop toolbar typography with neutral N letters, multicolor Ø, purple→cyan→mint Stop lettering, and a small centered equalizer. This is a native UI approximation; production logo vector and final launcher raster assets are separate asset work.
- The Home media chips use a brief bright interaction pulse and settle to a frosted lilac selected state.
- Music Home keeps its catalog-backed playlists, discovery hero, personalized mixes, new releases and previously implemented business logic.

## Deliberately not represented as shipped functionality
- Podcasts RSS subscription, episode player, and audiobook backend are NOT yet implemented. Their chips disclose that they are coming in a later checkpoint rather than showing fictional content.
- Artist cinematic hero, album details, standalone full-screen player visual overhaul, professional wordmark vectors and bitmap icons still require implementation / art asset delivery.
- Existing playback, downloads, library persistence and authentication data layers have not been replaced.

## Validation
- Push commit containing `[do test]` to run the repository's full CI matrix on this checkpoint.
- Do not deliver or request per-checkpoint phone installs; wait until full implementation and final validation.

## Second UI pass
- Catalog artist hero upgraded to real full-bleed imagery with the existing playable action and social navigation.
- Main now-playing canvas no longer displays quality/bitrate text; audio diagnostics logic is still retained.
- Global theme now follows brand violet/cyan/mint, with separate light-theme contrast colors.
- Brand wordmark and resting filter selections adapt to light mode.

## Final home/identity pass (post-second CI)
- The music Home now prioritizes user library shortcuts ("Volte para isso"), rediscovery ("Redescoberta"), personalized mixes, and latest releases; saved custom ordering is retained.
- Artwork-first mix cards use a cinematic image scrim and readable overlaid labels.
- The donor ticker has been moved below the discovery feed to avoid competing for attention.
- The local-library artist page now uses the same full-bleed real-image hero as online artist profiles.
- Android 26+ adaptive launcher icon uses a native vector approximation of the N-shaped waveform brand mark, violet to cyan to mint; the default app name is NØNStop.
- Legacy pre-Android-26 PNG launcher icons remain in the source tree, but the app currently sets `minSdk = 26`. They are not used on supported devices and do not block a release; supported devices use the `mipmap-anydpi-v26` adaptive icon backed by the NØNStop vector.
- This checkpoint does not yet include RSS podcasts/audiobook media support or a fully exported glow-accurate wordmark artwork.

## Navigation and locale finishing work
- Active tab illumination persists while a detail screen is pushed over a parent tab.
- All newly introduced Explore/Radio copy and mix-card build states are localized in pt-BR and English.
- Artist full-bleed heroes use dark scrims at the text edge in BOTH light and dark themes.
- This iteration includes the CI-detected missing Compose Color import fix for the local-library artist hero.
- Existing user-saved Home section arrangements continue to take precedence over the updated default order.

## Home above-the-fold finishing adjustment
- The genuine Daily Discover pager now follows the user's first four main music sections, rather than separating Back to Listening from Rediscovery. Its play/open/mix-building actions are preserved.
- All of the above runs against existing real catalog and playlist data, not hardcoded showcase music.

## Navigation refinement — approved layout
- Bottom bar uses the user's explicitly confirmed order: Início · Explorar · Rádio · Buscar · Biblioteca.
- The online artist page provides sticky, localized jump chips for songs, albums, singles and about; only shelves with real data receive a live chip.
- Artist album deep-links account for the extra sticky bar to land on the correct list.
- Pure unit tests cover full, sparse and empty artist catalog section indexes.

## Now Playing composition pass
- The expanded music player now uses a left-aligned editorial song title and artist/album caption under the prominent artwork, in place of the older centered, constrained title.
- The real like/unlike action and sleep timer remain independently clickable beside the track information; tapping the song text still opens the artist profile.
- The art keeps album-palette lighting with a tighter 14dp corner radius and controlled glow.
- Existing playback transport, queue, seek gestures, casting, lyrics, shared transitions and audio engine are unchanged.

## Albums and playlists UI checkpoint
- Extracted a reusable native NØNStop collection hero for local albums and playlists: editorial artwork, violet/navy atmosphere, restrained colored shadow, clear title and metadata.
- Reworked their primary actions to fit narrow phone screens without squeezed labels.
- Playlist search, share, and editable cover remain functional in an overflow menu, respecting read-only playlists.
- Online album catalog hero now follows the same proportions, while retaining play, queue, save, shuffle (only when applicable), download and streaming switch behaviors.
- New offline action labels respect the existing pt-BR/English resource system. No media/data services were replaced.

## Cross-screen finish of collection checkpoint
- Online album call-to-action labels use localized Portuguese/English resources, while the playlist and offline album labels follow the same language selection.
- Search and Library headers share the product's restrained lilac lighting and search input tint instead of the older plum static color.
- User requests no intermediate APK installs; builds are CI validation artifacts, not installation requests.

## Shared visual finishing pass — 2026-10-15
- NØNStop wordmark: both neutral N letters now adapt to light/dark, and the equalizer hues contrast in light mode; the approved logo proportions/typography remain unchanged.
- Selected bottom navigation icon uses steady brand violet rather than cover-dependent tones, with a soft radial glow and a 180 ms selection transition; tab order remains **Início · Explorar · Rádio · Buscar · Biblioteca**.
- Home media chips now paint their pulse above the resting tint instead of hiding it underneath, use accessible tab semantics and honor Android's reduce-motion option.
- Shared press-scale feedback honors reduce motion; artwork-first album and artist cards have reliable violet/cyan placeholders and readable two-line titles.
- Section headings and Home rail spacing now follow one editorial rhythm.
- This pass changes presentation only; playback, library permissions, downloads and data models are untouched.

## Explore and Radio cold-loading refinement — 2026-10-15
- Explore's catalog album rails now reuse the same shared 152dp album cards as Search and artist profiles, including consistent artwork fallbacks and touch feedback.
- Explore and Radio now show static, accessibility-hidden placeholder rails only during an initial empty loading state. Once loading finishes, the existing honest empty-state copy is retained.
- No mock catalogs, media downloads, playback or routing changes were introduced.
- Validate this change using the project's complete CI gate (`[do test]`).

## Explore and Radio empty-state/accessibility finish — 2026-10-15
- Discover page titles and section titles expose actual heading semantics for screen readers; visual hierarchy follows the shared editorial medium-weight section heading.
- Cold-loading rails remain static decorative placeholders, but now expose one localized loading description for TalkBack (pt-BR/en) instead of disappearing silently from accessibility navigation.
- Explore and Radio empty states use theme-aware rounded surfaces and decorative category icons, preserving existing conditional rendering and honest empty-state text.
- No catalog request, routing, media player, downloaded-content or permissions logic was touched.
- Full CI is requested on this checkpoint via `[do test]`; keep intermediate APKs out of the manual installation path.

## Podcast RSS MVP — 2026-10-15
- Home > Podcasts now opens a working search surface, not the previous coming-soon notice.
- The initial discovery directory is Apple Podcasts Search API (no secret embedded in the APK), returning publishers' public HTTPS RSS feeds. A user can also paste a direct public HTTPS RSS feed URL. This does **not** claim Spotify catalogue parity.
- Shows can be followed locally; RSS episode listings are fetched from the original publisher feed. There are no fabricated episodes.
- Playback uses a separate Android Media3 MediaSessionService, reusing the already-present playback technology without putting episodes in music TrackDao, music queues, scrobbles or recommendations.
- A 300 MiB LRU cache in `cacheDir/podcast_stream` stores streaming data; finished explicit downloads are independent files in `filesDir/podcasts`, with a WorkManager transfer, atomic publication, and a size ceiling.
- Episode resume points use private SharedPreferences. Direct RSS media URLs only; no Spotify/YouTube audio extraction. Streaming errors and compatibility still require physical-device validation.
- Scope limitation: this MVP does not yet include OPML, episode bookmarks, playback speed presets, a unified music/podcast mini-player, download migration to external SAF storage, catalogue browse charts, or RSS auto-refresh notifications.
- The legacy audiobook tab remains informational. No intermediate phone APK should be installed.
- CI trigger below validates Kotlin compilation, unit tests and the debug assemble: `[do test]`.

### Podcast MVP post-CI fixes
- Initial implementation passed the complete Android debug/compile/unit/worker CI matrix (run 38811308589).
- Follow-up correctness fixes: saved podcast files route through Media3's local-file-capable DefaultDataSource; explicit downloads wait for connectivity and reject HTML/JSON/XML/video error responses; pasted RSS feed URLs are no longer truncated to the directory-search limit.
- The audiobook coming-soon copy no longer claims the Podcast tab is unimplemented.
- Re-run the full matrix on this exact head using `[do test]` before declaring this checkpoint green.

## Podcast reliability and shared chrome checkpoint — 2026-10-15
- RSS directory requests, RSS feeds, full-episode streaming, and permanent downloads share the same HTTPS-only OkHttp client. Redirects are bounded and validated hop-by-hop; private DNS answers are refused.
- Permanent transfers remain WorkManager-owned across process death. The Home episode list reattaches to unique work for each episode, restores progress, and cancels the outstanding task before removing a saved file. Publication is serialized against file removal.
- The activity-scoped PodcastViewModel shares one MediaController with the Home podcast tab and with global chrome. The root mini-player shows podcast-specific controls when the user chooses a podcast, not a simultaneous music transport. Music playback takes priority when it resumes; podcast playback pauses music when selected.
- Podcast service is connected lazily until the user has first played a podcast; there is no duplicate media service startup on a fresh music-only install.
- All changes preserve existing song DB, album art, download workers and Now Playing route; no intermediate APK installs.
- CI required on latest commit: `[do test]`.

### Kotlin compilation fixes after initial CI
- First shared-chrome CI caught two narrowly scoped Kotlin errors: a removed UUID import still used by download temp naming, and OkHttp 4's Dns interface requiring an explicit object implementation. Both corrected without changing product scope.
- Re-run all checks on current head: `[do test]`.

## Full-screen podcast Now Playing — 2026-10-15
- A global podcast mini-player tap now opens a dedicated PodcastNowPlayingRoute, rather than switching to Home and selecting Podcasts.
- The route shares the same activity-scoped PodcastViewModel / MediaController; it does not create a second MediaSession or mutate music history.
- Full-screen transport includes the current episode title, show name, artwork fallback, scrubber with commit-on-release, elapsed/total time supporting multi-hour episodes, 15-second rewind, play/pause, 30-second skip, and an adjustable playback-speed cycle (1×/1.25×/1.5×/2×).
- The same slide-up/down transitions and chrome-hidden behavior as the music Now Playing route apply; Android Back and the downward arrow dismiss the podcast player without pausing audio.
- Removed the duplicated in-list seek/player controls; episode play buttons and global mini-player remain available in the Podcasts Home tab.
- Android compilation and all unit suites must validate this change: `[do test]`.

## Visual convergence checkpoint — 2026-10-15 (post-device screenshots)
- The user's Android screenshots demonstrated a mismatch between the approved editorial mockups and the shipped presentation. Passing CI is not proof of visual parity.
- Home now shows an actual, artwork-led "Continue listening" card whenever the music player exposes a real current track, including when a sparse or not-yet-synced library has no playlists. The card opens the existing full-screen music player; it does not fabricate playback history, media or recommendations. Previous pinned playlist sections and user-customized arrangements are unchanged.
- Search's Top Result no longer crushes the title between a large album cover and three independent track actions: artwork/name occupy the complete first row, while transport/download/more controls occupy a second row. The play/download/queue callbacks are unchanged.
- Podcast detail now has a full-width, artwork-led show hero, themed episode cards, localized RSS dates where parsable, and an indeterminate progress state while a permanent download is only preparing. Explicit downloads still use the original WorkManager path.
- The music Now Playing title now wraps to two readable lines, with slightly stronger album-colour lighting and a restrained artwork rim. It still retains existing music transport, lyrics, casting, shared element and sleep timer actions.
- The actual design target remains an editorial, not Spotify-heavy Home: "Volte para isso", "Redescoberta", "Feito para você", "Novos lançamentos" when backed by real items; purple/cyan/mint highlights; full-width frosted mini-player and five-tab order Início · Explorar · Rádio · Buscar · Biblioteca.
- This is a **visual convergence checkpoint, not a final release**: production wordmark/launcher assets, remaining search/library/collection visual QA, hardware screenshots, and functional smoke verification still remain. The Audiobooks tab is informational, not a playable backend; it must not be represented as completed.
- Do not request another device install just because the CI passed. The owner wants one consolidated installation after the final visual/functional verification.
- CI gate: `[do test]`.

### Visual convergence supplementary finishing
- Search result category and top-result type labels are localized (Portuguese / English): the screenshot's "Songs" and "Artists" English headings no longer appear in pt-BR.
- Podcast search now follows NØNStop's compact rounded-input language, with a keyboard Search action and an inline accessible search affordance rather than a second oversized purple button.
- This is still a code-level checkpoint. It needs physical-device comparison against the agreed visual prototype before anyone calls the look final.
- Verify updated head on CI with `[do test]`.

### Podcast player atmosphere
- The full-screen podcast player now has a soft brand-colour glow behind the real episode artwork, plus a restrained outline and a fading radial backdrop. Controls, real Media3 session, speed and background playback are unchanged.
- This complements the music Now Playing artwork approach; it is not a new, redundant player engine.
- Run complete CI against this commit: `[do test]`.
