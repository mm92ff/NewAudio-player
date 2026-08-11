#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
readonly DEFAULT_AVD_NAME="newaudio"
readonly APP_ID="com.example.newaudio"
readonly MAIN_ACTIVITY=".MainActivity"
readonly APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"

usage() {
    cat <<'EOF'
Usage:
  ./scripts/run-app-on-emulator.sh
  ./scripts/run-app-on-emulator.sh AVD_NAME
  ./scripts/run-app-on-emulator.sh list

The default AVD is "newaudio". The script builds the current debug APK,
installs it on that dedicated emulator, and launches MainActivity.
EOF
}

read_local_sdk_dir() {
    local properties_path="$PROJECT_ROOT/local.properties"
    local line
    local value

    [[ -r "$properties_path" ]] || return 1
    line="$(sed -n -E 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*(.*)$/\1/p' "$properties_path" | head -n 1)"
    [[ -n "$line" ]] || return 1

    value="${line//\\:/:}"
    value="${value//\\\\/\\}"
    printf '%s' "$value"
}

select_java_home() {
    local -a candidates=(
        "/home/jemi/.local/opt/jdk-17.0.20+8"
        "${JAVA_HOME:-}"
        "/home/jemi/.local/opt/android-studio/jbr"
    )
    local candidate

    for candidate in "${candidates[@]}"; do
        if [[ -n "$candidate" && -x "$candidate/bin/java" ]]; then
            printf '%s' "$candidate"
            return 0
        fi
    done

    printf '[ERROR] No usable JDK was found. Set JAVA_HOME to JDK 17.\n' >&2
    return 1
}

select_android_sdk() {
    local candidate="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

    if [[ -z "$candidate" ]]; then
        candidate="$(read_local_sdk_dir || true)"
    fi
    if [[ -z "$candidate" && -d /home/jemi/Android/Sdk ]]; then
        candidate="/home/jemi/Android/Sdk"
    fi

    if [[ -z "$candidate" || ! -d "$candidate" ]]; then
        printf '[ERROR] Android SDK not found. Set ANDROID_SDK_ROOT or update local.properties.\n' >&2
        return 1
    fi

    printf '%s' "$candidate"
}

avd_exists() {
    local avd_name=$1
    "$EMULATOR" -list-avds | grep -Fqx -- "$avd_name"
}

find_target_serial() {
    local avd_name=$1
    local serial
    local state
    local running_avd

    while read -r serial state _; do
        [[ "$serial" == emulator-* && "$state" == device ]] || continue
        running_avd="$("$ADB" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' | head -n 1 || true)"
        if [[ "$running_avd" == "$avd_name" ]]; then
            printf '%s' "$serial"
            return 0
        fi
    done < <("$ADB" devices | tail -n +2)

    return 1
}

wait_for_target_serial() {
    local avd_name=$1
    local deadline=$((SECONDS + 180))
    local serial

    while (( SECONDS < deadline )); do
        if serial="$(find_target_serial "$avd_name")"; then
            printf '%s' "$serial"
            return 0
        fi
        sleep 2
    done

    printf '[ERROR] Timed out waiting for AVD "%s" to appear in ADB.\n' "$avd_name" >&2
    return 1
}

wait_for_boot() {
    local serial=$1
    local deadline=$((SECONDS + 240))
    local state
    local boot_completed

    while (( SECONDS < deadline )); do
        state="$($ADB -s "$serial" get-state 2>/dev/null || true)"
        boot_completed="$($ADB -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
        if [[ "$state" == "device" && "$boot_completed" == "1" ]]; then
            return 0
        fi
        sleep 3
    done

    printf '[ERROR] Timed out waiting for Android to finish booting on %s.\n' "$serial" >&2
    return 1
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" || "${1:-}" == "help" ]]; then
    usage
    exit 0
fi

JAVA_HOME="$(select_java_home)"
ANDROID_SDK_ROOT="$(select_android_sdk)"
readonly JAVA_HOME ANDROID_SDK_ROOT
export JAVA_HOME ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"

readonly ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
readonly EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"

if [[ ! -x "$ADB" ]]; then
    printf '[ERROR] ADB is not executable: %s\n' "$ADB" >&2
    exit 1
fi
if [[ ! -x "$EMULATOR" ]]; then
    printf '[ERROR] Android emulator is not executable: %s\n' "$EMULATOR" >&2
    exit 1
fi

if [[ "${1:-}" == "list" ]]; then
    "$EMULATOR" -list-avds
    exit 0
fi

readonly AVD_NAME="${1:-$DEFAULT_AVD_NAME}"
if ! avd_exists "$AVD_NAME"; then
    printf '[ERROR] AVD "%s" does not exist. See EMULATOR.md for setup details.\n' "$AVD_NAME" >&2
    exit 1
fi

TARGET_SERIAL=""
if TARGET_SERIAL="$(find_target_serial "$AVD_NAME")"; then
    printf 'Using running AVD %s on %s.\n' "$AVD_NAME" "$TARGET_SERIAL"
else
    EMULATOR_LOG="$(mktemp "/tmp/${AVD_NAME}-emulator.XXXXXX.log")"
    printf 'Starting AVD %s with a cold boot. Log: %s\n' "$AVD_NAME" "$EMULATOR_LOG"
    nohup "$EMULATOR" -avd "$AVD_NAME" -no-snapshot </dev/null >"$EMULATOR_LOG" 2>&1 &
    TARGET_SERIAL="$(wait_for_target_serial "$AVD_NAME")"
fi

printf 'Waiting for Android on %s to finish booting...\n' "$TARGET_SERIAL"
wait_for_boot "$TARGET_SERIAL"

printf 'Building the current NewAudio debug APK...\n'
bash "$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" :app:assembleDebug --console=plain

if [[ ! -f "$APK_PATH" ]]; then
    printf '[ERROR] Debug APK was not created: %s\n' "$APK_PATH" >&2
    exit 1
fi

printf 'Installing %s on %s...\n' "$APK_PATH" "$TARGET_SERIAL"
"$ADB" -s "$TARGET_SERIAL" install -r "$APK_PATH"

printf 'Launching %s/%s...\n' "$APP_ID" "$MAIN_ACTIVITY"
"$ADB" -s "$TARGET_SERIAL" shell am start -W -n "$APP_ID/$MAIN_ACTIVITY"

printf '\nNewAudio is installed and running on %s (%s).\n' "$TARGET_SERIAL" "$AVD_NAME"
