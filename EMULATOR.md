# Dedicated NewAudio emulator

Use the Android Virtual Device named `newaudio` for NewAudio development and testing. It is dedicated to this project; do not use another project's AVD or a physical device unless that target is explicitly requested.

## Configuration

| Setting | Value |
|---|---|
| AVD name | `newaudio` |
| Android version | Android 15 / API 35 |
| System image | Google APIs, x86_64 |
| Device profile | Pixel 6, 1080 x 2400, 420 dpi |
| Internal storage | 12 GB |
| RAM | 4 GB |
| CPU cores | 4 |

The installed system-image package is `system-images;android-35;google_apis;x86_64`. NewAudio does not require a Google Play image for normal development and runtime checks.

## Verified local Linux toolchain

| Tool | Path |
|---|---|
| Android SDK | `/home/jemi/Android/Sdk` |
| JDK 17 | `/home/jemi/.local/opt/jdk-17.0.20+8` |
| ADB | `/home/jemi/Android/Sdk/platform-tools/adb` |
| Emulator | `/home/jemi/Android/Sdk/emulator/emulator` |
| AVD manager | `/home/jemi/Android/Sdk/cmdline-tools/latest/bin/avdmanager` |

`local.properties` is host-local and must contain:

```properties
sdk.dir=/home/jemi/Android/Sdk
```

The dedicated AVD has been created at `~/.android/avd/newaudio.avd`. Its checked local configuration uses 12 GB data storage, 4 GB RAM, and four CPU cores.

If it must be recreated, use Android Studio's Device Manager with the settings above. The currently installed command-line `avdmanager` reports a missing `devices.xml` after generating an AVD definition, so it is not a reliable recreation path until the local command-line tools are repaired or updated. Do not substitute `PocastCloni_API_35` as a workaround.

## Build, install, and launch

From the repository root, run:

```bash
./scripts/run-app-on-emulator.sh
```

The script:

1. selects JDK 17 and the configured Android SDK;
2. reuses the running `newaudio` AVD or starts it with a cold boot;
3. waits for Android to finish booting;
4. builds `:app:assembleDebug` from the current working tree;
5. installs `app/build/outputs/apk/debug/app-debug.apk`; and
6. launches `com.example.newaudio/.MainActivity`.

List configured AVDs without starting one:

```bash
./scripts/run-app-on-emulator.sh list
```

An alternate dedicated AVD name may be passed as the first argument, but shared project AVDs must not be used without explicit approval.

## Manual commands

```bash
export JAVA_HOME=/home/jemi/.local/opt/jdk-17.0.20+8
export ANDROID_SDK_ROOT=/home/jemi/Android/Sdk
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"

./gradlew :app:assembleDebug
adb devices
adb -s <newaudio-serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <newaudio-serial> shell am start -W -n com.example.newaudio/.MainActivity
```

Always use the explicit `newaudio` serial when more than one Android device is connected. Do not automatically uninstall the app or clear its data when installation fails, because those actions destroy emulator state.
