[CmdletBinding()]
param(
    [string]$ReleaseApk,
    [string]$DebugApk,
    [string]$Aapt2Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $PSCommandPath
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $scriptRoot '../..'))

if ([string]::IsNullOrWhiteSpace($ReleaseApk)) {
    $candidates = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'app/build/outputs/apk/release') `
        -Filter '*.apk' -File -ErrorAction SilentlyContinue | Sort-Object FullName)
    if ($candidates.Count -ne 1) {
        throw "Expected exactly one release APK; found $($candidates.Count)."
    }
    $ReleaseApk = $candidates[0].FullName
}
$ReleaseApk = [IO.Path]::GetFullPath($ReleaseApk)
if (-not (Test-Path -LiteralPath $ReleaseApk -PathType Leaf)) {
    throw "Release APK not found: $ReleaseApk"
}
if ([string]::IsNullOrWhiteSpace($DebugApk)) {
    $candidates = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'app/build/outputs/apk/debug') `
        -Filter '*.apk' -File -ErrorAction SilentlyContinue | Sort-Object FullName)
    if ($candidates.Count -ne 1) {
        throw "Expected exactly one debug APK; found $($candidates.Count)."
    }
    $DebugApk = $candidates[0].FullName
}
$DebugApk = [IO.Path]::GetFullPath($DebugApk)
if (-not (Test-Path -LiteralPath $DebugApk -PathType Leaf)) {
    throw "Debug APK not found: $DebugApk"
}

function Resolve-Aapt2 {
    if (-not [string]::IsNullOrWhiteSpace($Aapt2Path)) {
        $resolved = [IO.Path]::GetFullPath($Aapt2Path)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "aapt2 not found: $resolved"
        }
        return $resolved
    }
    $command = Get-Command aapt2 -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }
    $sdkRoots = [Collections.Generic.List[string]]::new()
    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($root)) { $sdkRoots.Add($root) }
    }
    $localProperties = Join-Path $repositoryRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($null -ne $sdkLine) {
            $sdkRoot = ($sdkLine -split '=', 2)[1].Trim()
            $sdkRoot = $sdkRoot -replace '\\:', ':' -replace '\\\\', '\'
            $sdkRoots.Add($sdkRoot)
        }
    }
    foreach ($root in $sdkRoots) {
        if ([string]::IsNullOrWhiteSpace($root)) { continue }
        $buildTools = Join-Path $root 'build-tools'
        if (-not (Test-Path -LiteralPath $buildTools -PathType Container)) { continue }
        $name = if ($env:OS -eq 'Windows_NT') { 'aapt2.exe' } else { 'aapt2' }
        $candidate = Get-ChildItem -LiteralPath $buildTools -Recurse -Filter $name -File |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($null -ne $candidate) { return $candidate.FullName }
    }
    throw 'aapt2 was not found in PATH, ANDROID_HOME or ANDROID_SDK_ROOT.'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
function Assert-IsolatedApk {
    param([string]$ApkPath, [string]$Variant)

    $manifestDump = @(& $aapt2 dump xmltree --file AndroidManifest.xml $ApkPath 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "aapt2 could not inspect the $Variant manifest: $($manifestDump -join [Environment]::NewLine)"
    }
    $manifestText = $manifestDump -join [Environment]::NewLine
    foreach ($pattern in @('BenchmarkSetupReceiver', 'BENCHMARK_SETUP', 'com\.example\.newaudio\.benchmark\.SETUP')) {
        if ($manifestText -match $pattern) {
            throw "$Variant manifest contains forbidden benchmark marker '$pattern'."
        }
    }
    if ($Variant -eq 'release' -and $manifestText -match 'E: profileable') {
        throw 'Release manifest contains forbidden profileable marker.'
    }

    $archive = [IO.Compression.ZipFile]::OpenRead($ApkPath)
    try {
        $forbiddenEntries = @($archive.Entries | Where-Object {
            $_.FullName -match '(?i)(^|/)benchmark-fixtures(/|$)' -or
            $_.FullName -match '(?i)(^|/)assets/fixtures(/|$)' -or
            $_.FullName -match '(?i)perfetto' -or
            $_.FullName -match '(?i)tracing[_-]perfetto'
        })
        if ($forbiddenEntries.Count -gt 0) {
            throw "$Variant APK contains benchmark/Perfetto entries: $($forbiddenEntries.FullName -join ', ')"
        }
    } finally {
        $archive.Dispose()
    }
    Write-Host "$Variant APK isolation verified: $ApkPath"
}

$aapt2 = Resolve-Aapt2
Assert-IsolatedApk -ApkPath $DebugApk -Variant 'debug'
Assert-IsolatedApk -ApkPath $ReleaseApk -Variant 'release'

$gradleWrapper = if ($env:OS -eq 'Windows_NT') {
    Join-Path $repositoryRoot 'gradlew.bat'
} else {
    Join-Path $repositoryRoot 'gradlew'
}
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper not found: $gradleWrapper"
}
$forbiddenDependencies = @(
    'androidx.compose.runtime:runtime-tracing',
    'androidx.tracing:tracing-perfetto',
    'androidx.tracing:tracing-perfetto-binary'
)
foreach ($configuration in @('debugRuntimeClasspath', 'releaseRuntimeClasspath')) {
    $dependencyOutput = @(& $gradleWrapper --no-daemon -q :app:dependencies `
        "--configuration=$configuration" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not resolve $configuration for isolation verification: $($dependencyOutput -join [Environment]::NewLine)"
    }
    $dependencyText = $dependencyOutput -join [Environment]::NewLine
    foreach ($coordinate in $forbiddenDependencies) {
        if ($dependencyText -match [regex]::Escape($coordinate)) {
            throw "$configuration contains forbidden benchmark dependency '$coordinate'."
        }
    }
}

Write-Host 'Debug/release dependency isolation verified.'
