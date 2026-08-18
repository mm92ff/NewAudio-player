#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "Usage: $0 <avd-name> <api-level> <log-path> [font-scale] [image-tag]" >&2
}

if [ "$#" -lt 3 ] || [ "$#" -gt 5 ]; then
    usage
    exit 64
fi

if [ "${GITHUB_ACTIONS:-}" != "true" ]; then
    echo "This helper is restricted to GitHub Actions runners." >&2
    exit 64
fi

avd_name=$1
api_level=$2
emulator_log=$3
font_scale=${4:-1.00}
image_tag=${5:-google_apis}

if [[ ! "$avd_name" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "Invalid AVD name: $avd_name" >&2
    exit 64
fi
if [[ ! "$api_level" =~ ^[0-9]+$ ]]; then
    echo "Invalid Android API level: $api_level" >&2
    exit 64
fi
if [[ "$emulator_log" != /* ]]; then
    echo "The emulator log path must be absolute: $emulator_log" >&2
    exit 64
fi
if [[ ! "$font_scale" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid font scale: $font_scale" >&2
    exit 64
fi
if [[ ! "$image_tag" =~ ^[A-Za-z0-9._-]+$ ]]; then
    echo "Invalid Android system image tag: $image_tag" >&2
    exit 64
fi

: "${ANDROID_HOME:?ANDROID_HOME must be set}"
: "${HOME:?HOME must be set}"
: "${RUNNER_TEMP:?RUNNER_TEMP must be set}"
: "${GITHUB_PATH:?GITHUB_PATH must be set}"
: "${GITHUB_ENV:?GITHUB_ENV must be set}"

readonly cmdline_tools_build="15859902"
readonly cmdline_tools_sha1="040d3996a65543d22ec4bf73e4c37aa37a8d4af4"
readonly emulator_port="5554"
readonly emulator_serial="emulator-${emulator_port}"
readonly emulator_cores="2"
readonly emulator_memory_mb="2048"
readonly system_image="system-images;android-${api_level};${image_tag};x86_64"

sudo chmod 666 /dev/kvm

cmdline_archive="$RUNNER_TEMP/commandlinetools-linux-${cmdline_tools_build}.zip"
cmdline_extract=$(mktemp -d "$RUNNER_TEMP/newaudio-cmdline-tools.XXXXXX")
cmdline_root="$ANDROID_HOME/cmdline-tools/newaudio-22"

curl --fail --location --retry 3 --retry-all-errors \
    --output "$cmdline_archive" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${cmdline_tools_build}_latest.zip"
echo "${cmdline_tools_sha1}  ${cmdline_archive}" | sha1sum --check --strict
unzip -q "$cmdline_archive" -d "$cmdline_extract"
mkdir -p "$cmdline_root"
cp -R "$cmdline_extract/cmdline-tools/." "$cmdline_root/"

sdkmanager="$cmdline_root/bin/sdkmanager"
avdmanager="$cmdline_root/bin/avdmanager"

set +e
yes | "$sdkmanager" --sdk_root="$ANDROID_HOME" \
    "platform-tools" \
    "emulator" \
    "$system_image"
sdkmanager_status=${PIPESTATUS[1]}
set -e
if [ "$sdkmanager_status" -ne 0 ]; then
    echo "Android SDK installation failed with exit code $sdkmanager_status." >&2
    exit "$sdkmanager_status"
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
echo "$ANDROID_HOME/platform-tools" >> "$GITHUB_PATH"
echo "$ANDROID_HOME/emulator" >> "$GITHUB_PATH"

mkdir -p "$HOME/.android/avd"
export ANDROID_AVD_HOME="$HOME/.android/avd"
echo "ANDROID_AVD_HOME=$ANDROID_AVD_HOME" >> "$GITHUB_ENV"
echo "ANDROID_SERIAL=$emulator_serial" >> "$GITHUB_ENV"

printf 'no\n' | "$avdmanager" create avd \
    --force \
    --name "$avd_name" \
    --package "$system_image" \
    --device pixel_6
"$ANDROID_HOME/emulator/emulator" -list-avds | grep -Fqx "$avd_name"

adb start-server
nohup "$ANDROID_HOME/emulator/emulator" \
    -avd "$avd_name" \
    -port "$emulator_port" \
    -no-window \
    -no-audio \
    -no-boot-anim \
    -no-metrics \
    -no-snapshot \
    -cores "$emulator_cores" \
    -memory "$emulator_memory_mb" \
    -gpu swiftshader > "$emulator_log" 2>&1 &
emulator_pid=$!

for boot_attempt in $(seq 1 150); do
    if ! kill -0 "$emulator_pid" 2>/dev/null; then
        echo "Emulator stopped before Android finished booting." >&2
        cat "$emulator_log"
        exit 1
    fi
    if [ "$(adb -s "$emulator_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
        break
    fi
    if [ "$boot_attempt" -eq 150 ]; then
        echo "Timed out waiting five minutes for Android to boot." >&2
        cat "$emulator_log"
        exit 1
    fi
    sleep 2
done

actual_api=$(adb -s "$emulator_serial" shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$actual_api" != "$api_level" ]; then
    echo "Expected Android API $api_level, but the emulator reports API $actual_api." >&2
    exit 1
fi

adb -s "$emulator_serial" shell input keyevent 82
adb -s "$emulator_serial" shell settings put global window_animation_scale 0
adb -s "$emulator_serial" shell settings put global transition_animation_scale 0
adb -s "$emulator_serial" shell settings put global animator_duration_scale 0
adb -s "$emulator_serial" shell settings put system font_scale "$font_scale"
# Keep the launcher out of the foreground during the Gradle build. On headless
# software rendering it can otherwise raise an unrelated ANR dialog that blocks
# the subsequent instrumentation activity from becoming visible.
adb -s "$emulator_serial" shell am start -W -a android.settings.SETTINGS >/dev/null
if adb -s "$emulator_serial" shell pm path com.google.android.apps.nexuslauncher 2>/dev/null |
    grep -q '^package:'; then
    adb -s "$emulator_serial" shell am force-stop com.google.android.apps.nexuslauncher
fi
adb -s "$emulator_serial" get-state | grep -Fqx device

echo "Android API $api_level is ready on $emulator_serial with font scale $font_scale."
