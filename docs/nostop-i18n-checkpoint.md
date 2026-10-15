# NØSTOP — Internationalization checkpoint

Updated: 2026-10-15. Branch: `nonstop`.

## Delivered in this checkpoint

- **Audio & Quality**: settings headings, streaming/download quality, Qobuz login state, lossless fallback, endpoint diagnostics, and the help text around potentially high data usage.
- **Library & Storage**: download-location controls, library storage usage, backup dialogs, restore scope selections, irreversible-operation warning, migration states, and settings descriptions.
- **Sync**: header, download management, blocked/failed tracks, Last.fm recommendations, undo controls, mix mode, refresh warning, and confirmation explaining what undo can and cannot restore.
- The implementation uses normal `res/values/strings.xml` and `res/values-pt-rBR/strings.xml`, so the existing app language selection controls which resources appear.
- There were no intentional changes to playback, authentication, download pipelines, sync persistence, or the backup/restore business logic.

## Static verification

- Checked the new string references in `SettingsAudioQualityScreen.kt`, `SettingsLibraryStorageScreen.kt`, and `SyncScreen.kt` against their own module resources.
- Checked that the English and Brazilian Portuguese resource sets have matching keys and matching positional string format arguments.
- Verified no duplicate string names in these resources.

## Still pending

- Complete localization of the settings’ shared components, dynamic text from view models, downloaded music/playlist screens, and secondary sync/diagnostic screens. Some copy in the three screens above remains hard-coded in English.
- Product-wide visual redesign and podcast playback/library/downloading are independent future checkpoints.
- This checkpoint is not a user-facing final release. The next phone installation should happen only after all agreed features are consolidated.

## CI

The final commit for this checkpoint requests GitHub Actions' unit-test compilation, all-module tests and `:app:assembleDebug`. Build outcome must be checked before this checkpoint is called build-validated.
