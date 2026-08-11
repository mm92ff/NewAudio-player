# AGENTS.md

## Project

- This is a Kotlin/Android project built with Gradle.
- `app/` is the primary application module.
- Preserve unrelated user changes and keep requested work narrowly scoped.
- Analysis-only, evaluation, diagnosis, and review requests are read-only unless the user explicitly authorizes changes.

## Emulator workflow

- Read `EMULATOR.md` before emulator, instrumentation, or device work.
- Use the dedicated `newaudio` AVD. Do not use `PocastCloni_API_35`, another project's AVD, or a physical device unless the user explicitly requests it.
- Prefer `./scripts/run-app-on-emulator.sh` for building, installing, and launching the current debug APK.
- When more than one device is visible to ADB, always pass the serial belonging to the `newaudio` AVD explicitly.
- Do not uninstall the app or clear emulator data unless the user explicitly authorizes the data loss.
- Treat historical emulator results as context only. Claim emulator validation only for checks run against the current working tree.

## Validation

- Start with focused JVM tests and compilation for the changed area.
- Use emulator or instrumentation checks for behavior that depends on Android runtime, Compose UI, Room, DataStore, media playback, storage permissions, or device APIs.
- Report checks that were skipped, unavailable, or compiled without runtime execution.
- Run `git diff --check` for implementation diffs.

## Git safety

- Inspect Git status before and after work.
- Do not discard unrelated tracked or untracked changes.
- Do not push, rewrite history, delete branches, or use destructive Git commands without an explicit request.
