# Changelog

All notable changes to NewAudio are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- Unit tests for `BackupExportImportTest` (10 tests) covering export/import correctness, error paths, and regression cases
- Unit tests for `SettingsUseCasesTest` (32 tests) covering all settings use cases
- Unit tests for `FileBrowserViewModel` (basic coverage)
- Unit tests for `ConsoleViewModel` (comprehensive coverage)

### Fixed
- **Backup export via `file://` URIs** — switched from `ContentResolver` to `FileOutputStream` for direct file paths, preventing silent failures
- **Backup import URI fallback** — use parsed `uri.path` instead of raw string in `File()` constructor, fixing broken fallback for `file://` paths
- **Race condition on import** — extracted import logic into `performImport()` suspend function called directly (awaited) before temp file cleanup, preventing file deletion before read completes
- **Premature export success message** — success/error notification now only fires after destination copy completes, gated by `notifyResult` parameter
- **`useMarquee` default inconsistency** — `UserPreferences.default()` now has `useMarquee = true` consistent with `isMarqueeEnabled = true`
- **Playlist import atomicity** — wrapped import in `@Transaction` to prevent partial database state on failure
- **Empty catch blocks** — replaced silent catch blocks with proper Timber error logging throughout the codebase
- **PlayerViewModel error events** — converted from `StateFlow` to `Channel` for correct single-delivery semantics
- **I/O on main thread** — moved file I/O operations from composables to ViewModels with correct dispatcher usage
- **UI responsiveness** — fixed main thread safety issues in multiple ViewModels
- **Bluetooth autoplay logging** — added error logging to `BluetoothAutoplayManager` catch block
- **DataStore read logging** — added `IOException` logging to DataStore read operations
- **Network error routing** — network errors now routed through existing `errorEvents` channel in `PlayerViewModel`

### Improved
- `FileBrowserList` `remember` blocks kept separate for better recomposition performance

---

## [1.0.0] — 2026-03-30

Initial release of NewAudio.

### Added

**Playback**
- Full-screen player with art, title, artist, seek bar, shuffle, repeat, skip, and volume controls
- Mini player displayed on all non-player screens
- Media3 / ExoPlayer background playback service with notification controls
- Persist and restore last-played song and position across app restarts

**File Browser**
- Recursive folder navigation with sort options
- Play folder, add to playlist, copy/move/delete/rename files
- Show/hide hidden files toggle
- Folder song count display

**Playlists**
- Create, rename, duplicate, delete playlists
- Drag-to-reorder songs and playlists
- Add individual songs or whole folders to playlists
- Export playlists (+ settings) to JSON file
- Import playlists (+ settings) from JSON file (forwards- and backwards-compatible)

**Equalizer**
- Per-band gain adjustment using system audio effects

**Settings**
- Theme: Light / Dark / System
- Custom primary colour picker
- Mini player and full-screen player progress bar height
- One-handed mode (controls shifted to bottom)
- Marquee text for long song titles
- Transparent list items
- Settings card border and transparency options
- Background gradient with tint fraction control
- Music folder selection and re-scan
- Reset database option

**Bluetooth**
- Auto-start playback when a Bluetooth audio device connects (configurable)

**Developer**
- In-app debug console overlay (log stream)
- Timber-based structured logging throughout

**Architecture**
- Clean Architecture: Domain / Data / Feature layers
- Hilt dependency injection
- Kotlin Coroutines + Flow throughout
- Room database with schema versioning
- DataStore Preferences for user settings
- Type-safe Compose Navigation with kotlinx-serialization routes
