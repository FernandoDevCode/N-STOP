# NØNStop — interface implementation checkpoint (2026-10-15)

Branch: `nonstop`.

## Approved design implemented in native Compose
- Bottom navigation, in the approved order: Início · Explorar · Rádio · Sua Biblioteca · Buscar.
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
