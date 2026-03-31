# NewAudio

A modern Android music player built with Jetpack Compose, Media3/ExoPlayer, and Clean Architecture.

## Features

- **Music Playback** — Full-screen and mini player with playback controls (play/pause, skip, seek, shuffle, repeat)
- **File Browser** — Navigate your device's storage to find and play music files
- **Playlist Management** — Create, edit, reorder playlists; add/remove songs; duplicate and export
- **Backup & Restore** — Export and import playlists (including settings) as JSON files
- **Equalizer** — Built-in audio equalizer with configurable bands
- **Bluetooth Autoplay** — Automatically start playback when a Bluetooth device connects
- **Theming** — Light, Dark, and System-follow themes with a customizable primary colour
- **UI Customisation** — One-handed mode, marquee text, progress bar height, transparent list items, settings card style
- **Background Playback** — Media3 foreground service with full notification controls
- **Debug Console** — In-app overlay for live log output during development

## Screenshots

> Add screenshots here

## Requirements

| Item | Version |
|---|---|
| Android | 6.0 (API 23) and higher |
| Target SDK | 35 (Android 15) |
| Compile SDK | 35 |

## Tech Stack

| Layer | Libraries |
|---|---|
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | Clean Architecture (Domain / Data / Feature), ViewModel, StateFlow |
| Media | Media3 (ExoPlayer + MediaSession) |
| Database | Room 2.6 |
| Preferences | DataStore Preferences |
| DI | Hilt 2.51 |
| Serialisation | kotlinx.serialization (JSON) |
| Async | Kotlin Coroutines + Flow |
| Logging | Timber |
| Testing | JUnit 4, kotlinx-coroutines-test, MockK |

## Build

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 35

### Clone & Run

```bash
git clone https://github.com/mm92ff/NewAudio-player.git
cd NewAudio-player
```

Open the project in Android Studio and run on a device or emulator (API 23+).

### Build APK

```bash
./gradlew assembleRelease
```

### Run Unit Tests

```bash
./gradlew :app:testDebugUnitTest
```

## Project Structure

```
app/src/main/java/com/example/newaudio/
├── data/           # Repository implementations, Room DB, DataStore, audio
├── domain/         # Models, repository interfaces, use cases
├── feature/        # UI screens & ViewModels (player, fileBrowser, playlist, settings, console)
├── di/             # Hilt modules
├── service/        # MediaPlaybackService, BluetoothAutoplayManager
└── ui/             # Theme, navigation, main screen
```

## Permissions

| Permission | Purpose |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | Read music files |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Background playback |
| `BLUETOOTH_CONNECT` | Detect Bluetooth device connections |
| `RECEIVE_BOOT_COMPLETED` | Restore state after reboot |
| `POST_NOTIFICATIONS` | Show playback notification |

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'feat: add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

## License

This project is licensed under the [MIT License](LICENSE).
