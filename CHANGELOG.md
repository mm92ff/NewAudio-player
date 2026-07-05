# Changelog

All notable changes to NewAudio are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [Unreleased]

### Added
- Dedicated Video mode with its own folder setup, media scan, browser state, playback session, and mini-player integration
- Inline video player that replaces the video list area while keeping the mini player controls available
- Fullscreen video mode with double-tap enter/exit, orientation-aware layout, swipe-to-next/previous with wrap-around, timeline reveal, and play/pause control
- Fullscreen video gestures for brightness, volume, timeline seeking, and pinch-based video fit/fill behavior
- Video marker system for important moments, including add, move, delete, previous/next marker navigation, optional marker controls, and marker backup/restore
- Hash and metadata based video marker restore so markers can be matched after moving media to another device
- Separate video playlists with create, rename, duplicate, delete, reorder, export/import, and playback support
- Video gallery view with thumbnail previews, configurable 2/3/4 column layout, square/portrait-friendly formats, filled/cropped display, folder tiles, media counts, and optional filename overlay
- Coil video thumbnail support with cached previews and fallback video icons
- First-run video folder selection using the Android Movies location as the default where available
- Settings for video display mode, gallery layout, gallery fill behavior, thumbnail filename overlays, marker controls, and audio/video session switching
- Audio/video session resume behavior when switching between Music and Video modes
- Folder creation from empty browser space and file-operation flows for audio and video folders
- Unit coverage for video scanning, video playlists, video markers, audio/video file copy/move/delete flows, folder deletion cascades, session switching, and backup/restore marker handling

### Changed
- Music/Video mode switch now restores the folder that contains the currently active media item for that session
- Video mini-player title opens the inline player instead of jumping directly to fullscreen
- Inline video navigation uses the app-bar back button; the separate mini-player back control was removed
- Folder media counts now use context-aware labels for audio and video
- Marquee behavior now applies consistently across audio and video text where supported
- README now documents the current audio/video player feature set and includes new video screenshots
- Project license changed from MIT to Mozilla Public License 2.0

### Fixed
- Returning from fullscreen video no longer leaves the normal player view blank while audio continues in the background
- Normal video mode remains portrait after exiting fullscreen, including for landscape videos
- Consecutive fullscreen video changes avoid a brief incorrect portrait/landscape flash when possible
- Video swipe navigation wraps from the last item to the first and from the first item to the last
- Deleted videos and folders now refresh browser state and remove stale database references
- Moving, copying, renaming, and deleting video files updates database paths, playlists, markers, and currently visible folder state
- Copied video and audio files now keep correct display metadata in the mini player
- Relaunching the app while video playback continues no longer restores the wrong audio session
- Video files moved into subfolders remain playable after folder sync repairs stale content URIs
- Folder delete cascades now clean up audio/video database entries, playlist references, and video markers
- Video folder back navigation is available after auto-opening the folder of the current session media
- Debug/error console setting was cleaned up to match the current diagnostics behavior

### Documentation
- Added fresh screenshots for the video gallery and inline video player
- Updated README requirements, tech stack, permissions, project structure, and feature list for the current local app state

---

## [2.3.3-beta] — 2026-03-31

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
