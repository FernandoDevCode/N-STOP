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
- The pre-Android-26 PNG launcher icons still need production raster export (not included in this checkpoint).
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
