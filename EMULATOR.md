# Dedicated NewAudio emulator

Use the Android Virtual Device named `newaudio` for NewAudio development and testing. It is dedicated to this project and should be preferred over shared or unrelated emulator profiles.

## Configuration

| Setting | Value |
|---|---|
| AVD name | `newaudio` |
| Android version | Android 15 / API 35 |
| System image | Google Play, x86_64 |
| Device profile | Medium Phone, 1080 x 2400, 420 dpi |
| Internal storage | 12 GB |
| RAM | 4 GB |
| CPU cores | 4 |

## Usage

Start the emulator from Android Studio's Device Manager, or from PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd newaudio
```

When more than one Android device is connected, determine the serial assigned to `newaudio` with `adb devices` and pass it explicitly to Gradle or `adb` commands.
