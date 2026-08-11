#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly PROJECT_NAME="$(basename -- "$SCRIPT_DIR")"

SELF_TEST=0
PAUSE_ON_EXIT=0
TEMP_ARCHIVE=""
LIST_FILE=""
KEYSTORE_RELATIVE=""

usage() {
    printf 'Usage: %s [--self-test] [--pause]\n' "$(basename -- "$0")"
}

for argument in "$@"; do
    case "$argument" in
        --self-test)
            SELF_TEST=1
            ;;
        --pause)
            PAUSE_ON_EXIT=1
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            printf '[ERROR] Unknown argument: %s\n' "$argument" >&2
            usage >&2
            exit 2
            ;;
    esac
done

pause_if_requested() {
    if (( PAUSE_ON_EXIT == 1 )); then
        printf '\nPress Enter to close this window...'
        read -r _ || true
    fi
}

cleanup() {
    local exit_code=$?

    if [[ -n "$LIST_FILE" && -f "$LIST_FILE" ]]; then
        rm -f -- "$LIST_FILE"
    fi
    if [[ -n "$TEMP_ARCHIVE" && -f "$TEMP_ARCHIVE" ]]; then
        rm -f -- "$TEMP_ARCHIVE"
    fi

    if (( exit_code != 0 )); then
        printf '\nBackup failed. No finished archive was created.\n' >&2
        pause_if_requested
    fi
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

if ! command -v 7z >/dev/null 2>&1 && ! command -v 7zz >/dev/null 2>&1; then
    printf '[ERROR] 7-Zip was not found. Install 7z or 7zz and try again.\n' >&2
    exit 1
fi
if ! command -v flock >/dev/null 2>&1; then
    printf '[ERROR] flock was not found. Install util-linux and try again.\n' >&2
    exit 1
fi
if ! command -v realpath >/dev/null 2>&1; then
    printf '[ERROR] realpath was not found. Install coreutils and try again.\n' >&2
    exit 1
fi

if command -v 7z >/dev/null 2>&1; then
    readonly SEVEN_ZIP="$(command -v 7z)"
else
    readonly SEVEN_ZIP="$(command -v 7zz)"
fi

trim_whitespace() {
    local value=$1
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

find_project_keystore() {
    local properties_path="$SCRIPT_DIR/signing.local.properties"
    local line
    local key
    local value=""
    local candidate
    local resolved

    while IFS= read -r line || [[ -n "$line" ]]; do
        line="${line%$'\r'}"
        [[ "$line" == *"="* ]] || continue
        key="$(trim_whitespace "${line%%=*}")"
        if [[ "$key" == "storeFile" ]]; then
            value="$(trim_whitespace "${line#*=}")"
            break
        fi
    done <"$properties_path"

    if [[ -z "$value" ]]; then
        printf '[ERROR] signing.local.properties has no usable storeFile entry.\n' >&2
        return 1
    fi

    if [[ "$value" == /* ]]; then
        candidate="$value"
    else
        candidate="$SCRIPT_DIR/$value"
    fi

    if ! resolved="$(realpath -e -- "$candidate" 2>/dev/null)" || [[ ! -f "$resolved" ]]; then
        printf '[ERROR] Configured release keystore is missing: %s\n' "$candidate" >&2
        return 1
    fi

    case "$resolved" in
        "$SCRIPT_DIR"/*)
            KEYSTORE_RELATIVE="${resolved#"$SCRIPT_DIR"/}"
            ;;
        *)
            printf '[ERROR] The configured release keystore must be inside the NewAudio project: %s\n' \
                "$resolved" >&2
            return 1
            ;;
    esac
}

if [[ -e "$SCRIPT_DIR/signing.local.properties" ]]; then
    if [[ ! -r "$SCRIPT_DIR/signing.local.properties" ]]; then
        printf '[ERROR] Signing configuration is unreadable: %s\n' \
            "$SCRIPT_DIR/signing.local.properties" >&2
        exit 1
    fi
    find_project_keystore
fi

exec {LOCK_FD}<"$SCRIPT_DIR"
if ! flock -n "$LOCK_FD"; then
    printf '[ERROR] Another project backup is already running.\n' >&2
    exit 1
fi

archive_contains() {
    local required_path=$1
    grep -Fqx "Path = $required_path" "$LIST_FILE"
}

validate_archive_contents() {
    local archive_path=$1
    local archive_entry
    local -a required_entries=(
        ".git/HEAD"
        "app/src/main"
        "settings.gradle.kts"
        "build.gradle.kts"
        "gradlew"
    )

    if [[ -d "$SCRIPT_DIR/apks" ]]; then
        required_entries+=("apks")
    fi
    if [[ -f "$SCRIPT_DIR/local.properties" ]]; then
        required_entries+=("local.properties")
    fi
    if [[ -f "$SCRIPT_DIR/signing.local.properties" ]]; then
        required_entries+=("signing.local.properties")
    fi
    if [[ -n "$KEYSTORE_RELATIVE" ]]; then
        required_entries+=("$KEYSTORE_RELATIVE")
    fi
    if [[ -f "$SCRIPT_DIR/benchmark/build.gradle.kts" ]]; then
        required_entries+=("benchmark/build.gradle.kts")
    fi
    if [[ -f "$SCRIPT_DIR/performance/README.md" ]]; then
        required_entries+=("performance/README.md")
    fi
    if [[ -f "$SCRIPT_DIR/sign-release-local.bat" ]]; then
        required_entries+=("sign-release-local.bat")
    fi

    printf '\nVerifying archive integrity...\n'
    "$SEVEN_ZIP" t "$archive_path" -bsp0

    LIST_FILE="$(mktemp)"
    "$SEVEN_ZIP" l -slt "$archive_path" >"$LIST_FILE"

    for archive_entry in "${required_entries[@]}"; do
        if ! archive_contains "$archive_entry"; then
            printf '[ERROR] Required entry is missing from the backup: %s\n' \
                "$archive_entry" >&2
            return 1
        fi
    done

    while IFS= read -r archive_entry; do
        if is_excluded_path "$archive_entry"; then
            printf '[ERROR] Excluded entry is present in the backup: %s\n' \
                "$archive_entry" >&2
            return 1
        fi
    done < <(
        awk '
            found_separator && /^Path = / { sub(/^Path = /, ""); print }
            /^----------$/ { found_separator = 1 }
        ' "$LIST_FILE"
    )

    rm -f -- "$LIST_FILE"
    LIST_FILE=""
}

is_excluded_path() {
    local path=$1

    case "$path" in
        build|build/*|*/build|*/build/*|\
        .gradle|.gradle/*|*/.gradle|*/.gradle/*|\
        .kotlin|.kotlin/*|*/.kotlin|*/.kotlin/*|\
        .idea|.idea/*|*/.idea|*/.idea/*|\
        .externalNativeBuild|.externalNativeBuild/*|*/.externalNativeBuild|*/.externalNativeBuild/*|\
        .cxx|.cxx/*|*/.cxx|*/.cxx/*|\
        captures|captures/*|*/captures|*/captures/*|\
        out|out/*|*/out|*/out/*|\
        benchmark-reports|benchmark-reports/*|*/benchmark-reports|*/benchmark-reports/*|\
        benchmark-results|benchmark-results/*|*/benchmark-results|*/benchmark-results/*|\
        artifacts|artifacts/*|*/artifacts|*/artifacts/*|\
        logs|logs/*|*/logs|*/logs/*|\
        performance/results|performance/results/*|\
        performance/reports|performance/reports/*|\
        performance/traces|performance/traces/*|\
        workflow/runtime|workflow/runtime/*|\
        workflow/reports|workflow/reports/*|\
        workflow/research|workflow/research/*|\
        xxx|xxx/*|*/xxx|*/xxx/*|\
        githubfolder|githubfolder/*|*/githubfolder|*/githubfolder/*|\
        *.log|*.perfetto-trace|*.idsig|*benchmarkData.json|\
        emulator-screen.png|*/emulator-screen.png|ui*.xml|*/ui*.xml|\
        "$PROJECT_NAME"_backup_*.7z|*/"$PROJECT_NAME"_backup_*.7z|*.partial.7z)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

cd -- "$SCRIPT_DIR"
umask 077

readonly TIMESTAMP="$(date '+%Y-%m-%d_%H-%M-%S')"

if (( SELF_TEST == 1 )); then
    ARCHIVE_NAME="${PROJECT_NAME}_backup_self-test_$$.7z"
else
    ARCHIVE_NAME="${PROJECT_NAME}_backup_${TIMESTAMP}.7z"
    suffix=1
    while [[ -e "$SCRIPT_DIR/$ARCHIVE_NAME" ]]; do
        ARCHIVE_NAME="${PROJECT_NAME}_backup_${TIMESTAMP}_$suffix.7z"
        ((suffix += 1))
    done
fi

readonly ARCHIVE_PATH="$SCRIPT_DIR/$ARCHIVE_NAME"
TEMP_ARCHIVE="$SCRIPT_DIR/.${ARCHIVE_NAME%.7z}.$$.partial.7z"

if (( SELF_TEST == 1 )); then
    printf 'Running project backup self-test...\n'
else
    printf '\nProject backup\n'
    printf '==============\n'
    printf 'Project: %s\n' "$PROJECT_NAME"
    printf 'Output:  %s\n\n' "$ARCHIVE_PATH"
    printf 'WARNING: This archive is NOT encrypted.\n'
    printf 'It contains local signing files and may contain keystores.\n'
    printf 'Anyone with access to it may be able to sign application releases.\n'
    printf 'Store the archive in a protected location.\n\n'
fi

archive_excludes=(
    '-xr!build'
    '-xr!.gradle'
    '-xr!.kotlin'
    '-xr!.idea'
    '-xr!.externalNativeBuild'
    '-xr!.cxx'
    '-xr!captures'
    '-xr!out'
    '-xr!benchmark-reports'
    '-xr!benchmark-results'
    '-xr!artifacts'
    '-xr!logs'
    '-xr!performance/results'
    '-xr!performance/reports'
    '-xr!performance/traces'
    '-xr!workflow/runtime'
    '-xr!workflow/reports'
    '-xr!workflow/research'
    '-xr!xxx'
    '-xr!githubfolder'
    '-xr!*.log'
    '-xr!*.perfetto-trace'
    '-xr!*.idsig'
    '-xr!*benchmarkData.json'
    '-xr!emulator-screen.png'
    '-xr!ui*.xml'
    "-xr!${PROJECT_NAME}_backup_*.7z"
    '-xr!*.partial.7z'
)

"$SEVEN_ZIP" a -t7z "$TEMP_ARCHIVE" . \
    -mx=7 -m0=lzma2 \
    "${archive_excludes[@]}"

validate_archive_contents "$TEMP_ARCHIVE"

if (( SELF_TEST == 1 )); then
    rm -f -- "$TEMP_ARCHIVE"
    TEMP_ARCHIVE=""
    printf '\nSelf-test passed: integrity, required content, and exclusions are correct.\n'
    exit 0
fi

mv -- "$TEMP_ARCHIVE" "$ARCHIVE_PATH"
TEMP_ARCHIVE=""

readonly ARCHIVE_SIZE="$(stat -c '%s' "$ARCHIVE_PATH")"
readonly ARCHIVE_SHA256="$(sha256sum "$ARCHIVE_PATH" | awk '{print $1}')"

printf '\nBackup created and verified successfully.\n'
printf 'File:   %s\n' "$ARCHIVE_PATH"
printf 'Bytes:  %s\n' "$ARCHIVE_SIZE"
printf 'SHA256: %s\n' "$ARCHIVE_SHA256"

pause_if_requested
