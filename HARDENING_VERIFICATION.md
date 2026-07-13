# NewAudio hardening verification

Stand: 2026-07-13

## Scope

This record covers the implementation of Sprints 0–10 from
`plans/newaudio_hardening_plan.md`. Compose Tracing, Macrobenchmark, Perfetto
and Baseline Profiles remain intentionally deferred to the later performance
phase.

## Implemented release gates

- The GitHub Actions workflow in the working tree builds and tests the debug and release variants on `master`, `main`, and pull requests.
- The instrumentation job runs as a matrix on Android API 23, 29, 32, 33, 34
  and 35.
- Gradle dependency locking is enabled and the lock files are ready to be included in the hardening commit.
- The official Gradle 8.13 wrapper is ready to be included and the distribution is pinned
  with `distributionSha256Sum`.
- Release builds use R8 and resource shrinking.
- Signing secrets and local signing configuration remain excluded from Git.

## Local automated evidence

The following results were produced from the final local source state:

| Check | Result |
| --- | --- |
| JVM tests | 43 suites, 287 tests, 0 failures, 0 errors, 0 skipped |
| Android instrumentation, API 35 | 9 tests, 0 failures, 0 errors, 0 skipped |
| Android Lint | 0 errors, 157 warnings, 1 hint, 0 security/export findings |
| Debug APK build | successful, 27,213,823 bytes |
| Unsigned release APK build | successful, 4,735,633 bytes |
| Android test APK build | successful |
| Git whitespace validation | `git diff --check` successful |

The release metadata is `versionCode=241`,
`versionName=2.41-beta`, `minSdk=23` and `targetSdk=35`. This avoids reusing the
existing `v2.40-beta` tag. The release APK does
not contain the debuggable flag.

Instrumentation covers app startup, Room upgrades from the supported schema
history to v7, shared media across multiple playlists, foreign-key path cascades
for audio/video/markers, literal handling of `%` and `_` in path queries, a
real debug-only DocumentsProvider, and Compose semantics/touch-target checks
for draggable video markers. JVM coverage includes the storage
failure paths, database rollback, partial move outcomes, backup validation,
playback restore ordering and MediaSession authorization policy.

## Supply-chain evidence

- Gradle wrapper JAR SHA-256:
  `81A82AAEA5ABCC8FF68B3DFCB58B3C3C429378EFD98E7433460610FECD7AE45F`
- Gradle 8.13 distribution SHA-256:
  `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`
- The resolved release Maven graph contained 178 packages. An OSV batch query
  returned no findings on 2026-07-13.
- A tracked-file secret and sensitive-filename scan returned no findings.

## Release-candidate checks outside local automation

Before publishing a signed release, include all currently untracked hardening
files in the commit, let the CI matrix complete, create the new `v2.41-beta` tag, and
perform the device-dependent smoke checks for notification controls, headset,
Bluetooth, audio focus, Android 14 partial visual-media selection and the
chosen external document providers. These checks require real platform/provider
behaviour and are not represented by the single local API-35 emulator result.

The release APK recorded above is intentionally unsigned. Production signing
must use the external CI or local signing secret configured for the release.
