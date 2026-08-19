[CmdletBinding()]
param(
    [string]$TestClass = 'com.example.newaudio.benchmark.StartupBenchmark',
    [string]$OutputDirectory,
    [string[]]$AdditionalGradleArguments = @(),
    [string]$CacheState,
    [string]$DeviceRoleId,
    [ValidateSet('lists', 'gallery-cold', 'gallery-warm', 'folders-playlists')]
    [string]$MetricShard,
    [ValidateRange(1, 10)][int]$Iterations,
    [ValidateRange(0, 1)][int]$RetryCount = 0,
    [switch]$AllowDirty,
    [switch]$DryRun,
    [switch]$CheckPrerequisites
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $PSCommandPath
. (Join-Path $scriptRoot 'performance-common.ps1')
$performanceRoot = [IO.Path]::GetFullPath((Join-Path $scriptRoot '..'))
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $performanceRoot '..'))
$resultsRoot = [IO.Path]::GetFullPath((Join-Path $performanceRoot 'results'))
$gradleWrapper = if ($env:OS -eq 'Windows_NT') {
    Join-Path $repositoryRoot 'gradlew.bat'
} else {
    Join-Path $repositoryRoot 'gradlew'
}
$benchmarkOutputRoot = Join-Path $repositoryRoot 'benchmark/build/outputs/connected_android_test_additional_output'

function Resolve-SafeOutputDirectory {
    param([string]$Candidate)

    $resolved = if ([string]::IsNullOrWhiteSpace($Candidate)) {
        $resultsRoot
    } elseif ([IO.Path]::IsPathRooted($Candidate)) {
        [IO.Path]::GetFullPath($Candidate)
    } else {
        [IO.Path]::GetFullPath((Join-Path $repositoryRoot $Candidate))
    }

    $prefix = $resultsRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if ($resolved -ne $resultsRoot -and
        -not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe output path '$resolved'. Outputs must stay below '$resultsRoot'."
    }
    return $resolved
}

function Get-AdbPath {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($null -ne $command) { return $command.Source }

    foreach ($root in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if (-not [string]::IsNullOrWhiteSpace($root)) {
            $adbName = if ($env:OS -eq 'Windows_NT') { 'adb.exe' } else { 'adb' }
            $candidate = Join-Path $root (Join-Path 'platform-tools' $adbName)
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    $localProperties = Join-Path $repositoryRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
            Select-Object -First 1
        if ($null -ne $sdkLine) {
            $sdkRoot = ($sdkLine -split '=', 2)[1].Trim()
            $sdkRoot = $sdkRoot -replace '\\:', ':' -replace '\\\\', '\'
            $adbName = if ($env:OS -eq 'Windows_NT') { 'adb.exe' } else { 'adb' }
            $candidate = Join-Path $sdkRoot (Join-Path 'platform-tools' $adbName)
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
        }
    }
    return $null
}

function Assert-ConnectedDevice {
    param([string]$AdbPath)

    $devices = @(& $AdbPath devices 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "adb devices failed: $($devices -join [Environment]::NewLine)" }
    $ready = @($devices | Where-Object { $_ -match '^\S+\s+device$' })
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL)) {
        $ready = @($ready | Where-Object { ($_ -split '\s+')[0] -eq $env:ANDROID_SERIAL })
        if ($ready.Count -ne 1) {
            throw "ANDROID_SERIAL '$($env:ANDROID_SERIAL)' does not identify exactly one ready device. Output: $($devices -join '; ')"
        }
        return
    }
    if ($ready.Count -ne 1) {
        throw "Exactly one ready Android device is required; found $($ready.Count). Output: $($devices -join '; ')"
    }
}

function Assert-Prerequisites {
    param(
        [switch]$RequireAdb,
        [switch]$RequireDevice
    )

    if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
        throw "Gradle wrapper not found at '$gradleWrapper'."
    }
    if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot 'benchmark') -PathType Container)) {
        throw "Benchmark module not found at '$(Join-Path $repositoryRoot 'benchmark')'."
    }
    if ($RequireAdb -or $RequireDevice) {
        $adb = Get-AdbPath
        if ([string]::IsNullOrWhiteSpace($adb)) {
            throw 'adb was not found in PATH, Android SDK environment variables, or local.properties.'
        }
        if ($RequireDevice) { Assert-ConnectedDevice -AdbPath $adb }
    }
}

$metricMethodsByClass = @{
    StartupBenchmark = @('st01ColdStartupToBrowserReady', 'st02WarmStartupToBrowserReady')
    NavigationBenchmark = @('nv01BrowserSettingsBrowser', 'nv02BrowserPlaylistBrowser', 'diagnosticFailureArtifactProbe')
    BrowserRenderingBenchmark = @(
        'br01AudioListScroll', 'br02AudioListIdleWithMiniPlayer', 'br03VideoListScroll',
        'br04VideoGalleryTwoColumns', 'br04VideoGalleryThreeColumns', 'br04VideoGalleryFourColumns',
        'br04VideoGalleryTwoColumnsWarm', 'br04VideoGalleryThreeColumnsWarm', 'br04VideoGalleryFourColumnsWarm',
        'br05NestedFolderColdCache', 'br06NestedFolderWarmCache', 'pl01AudioPlaylistScroll', 'pl02VideoPlaylistScroll'
    )
    AudioPlaybackBenchmark = @(
        'au01MiniPlayerIdle', 'au02FullPlayerIdle', 'au03SeekPauseResumeNext', 'au04SettingsScrollDuringPlayback',
        'au05PausedControlIdle', 'au06MiniPlayerRepeatOffIdle', 'au07MiniPlayerRepeatOneIdle',
        'au08LongTitleMarqueeOffIdle', 'au09LongTitleMarqueeOnIdle'
    )
    VideoPlaybackBenchmark = @(
        'vi01InlineVideoIdle', 'vi02FullscreenIdle', 'vi03ControlsSeekAndMarker',
        'vi04FullscreenInlineTransition', 'vi05SwipeNextPrevious',
        'vi06FullscreenIdleMarkersOff', 'vi06FullscreenIdleMarkersOn'
    )
}
if ($TestClass -notmatch '^com\.example\.newaudio\.benchmark\.([A-Za-z0-9_$]+)(?:#([A-Za-z0-9_$]+))?$' -or
    -not $metricMethodsByClass.ContainsKey($Matches[1])) {
    throw "TestClass '$TestClass' is outside the metric-runner allowlist. TraceCaptureTest and contract tests must use their dedicated runners."
}
$resolvedClassName = $Matches[1]
$resolvedMethodName = $Matches[2]
if (-not [string]::IsNullOrWhiteSpace($resolvedMethodName) -and
    $resolvedMethodName -notin $metricMethodsByClass[$resolvedClassName]) {
    throw "Metric method '$resolvedClassName#$resolvedMethodName' has no versioned journey contract."
}
if (-not [string]::IsNullOrWhiteSpace($MetricShard) -and
    ($resolvedClassName -ne 'BrowserRenderingBenchmark' -or
        -not [string]::IsNullOrWhiteSpace($resolvedMethodName))) {
    throw 'MetricShard is only valid for the complete BrowserRenderingBenchmark class.'
}
if ($PSBoundParameters.ContainsKey('Iterations')) {
    $maximumIterations = if ($resolvedClassName -eq 'StartupBenchmark') { 10 } else { 5 }
    if ($Iterations -gt $maximumIterations) {
        throw "Iterations=$Iterations exceeds the $resolvedClassName contract maximum of $maximumIterations."
    }
}
$iterationOverride = $PSBoundParameters.ContainsKey('Iterations')
Assert-NewAudioAdditionalGradleArguments -Arguments $AdditionalGradleArguments
$provenance = Get-NewAudioRepositoryProvenance -RepositoryRoot $repositoryRoot
Assert-NewAudioRepositoryPolicy -Provenance $provenance -AllowDirty:$AllowDirty
$resolvedCacheState = Resolve-NewAudioCacheState -TestClass $TestClass -RequestedState $CacheState

$safeOutput = Resolve-SafeOutputDirectory -Candidate $OutputDirectory
$arguments = @(
    ':benchmark:connectedBenchmarkAndroidTest',
    '-PfullTracing=false',
    "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass"
) + $AdditionalGradleArguments
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL)) {
    $arguments += "-Pandroid.injected.device.serial=$($env:ANDROID_SERIAL)"
}
if ($PSBoundParameters.ContainsKey('Iterations')) {
    $arguments += "-Pandroid.testInstrumentationRunnerArguments.newaudio.benchmark.iterations=$Iterations"
}
if (-not [string]::IsNullOrWhiteSpace($MetricShard)) {
    $arguments += "-Pandroid.testInstrumentationRunnerArguments.newaudio.benchmark.shard=$MetricShard"
}

if ($DryRun) {
    Assert-Prerequisites
    Write-Host 'Dry run only; no benchmark is executed.'
    Write-Host ("Working directory: {0}" -f $repositoryRoot)
    Write-Host ("Command: {0} {1}" -f $gradleWrapper, ($arguments -join ' '))
    Write-Host ("Results root: {0}" -f $safeOutput)
    Write-Host ("Repository dirty: {0}; baseline eligible: {1}" -f $provenance.dirty,
        (Test-NewAudioBaselineEligibility -Provenance $provenance -AllowDirty ([bool]$AllowDirty) `
            -IterationOverride $iterationOverride))
    return
}

Assert-Prerequisites -RequireAdb -RequireDevice
if ($CheckPrerequisites) {
    Write-Host 'Metric benchmark prerequisites are satisfied.'
    return
}

$adb = Get-AdbPath
$environment = Get-NewAudioRunEnvironment -AdbPath $adb -RepositoryRoot $repositoryRoot `
    -Mode 'metrics' -CacheState $resolvedCacheState -DeviceRoleId $DeviceRoleId
$attempt = 0
$priorFailureDirectories = [Collections.Generic.List[string]]::new()
do {
    $attempt++
    $startedAt = [DateTime]::UtcNow
    $runId = 'metrics-{0}' -f $startedAt.ToString('yyyyMMdd-HHmmssfff')
    $runDirectory = Join-Path $safeOutput $runId
    $rawDirectory = Join-Path $runDirectory 'raw'
    New-Item -ItemType Directory -Path $rawDirectory -Force | Out-Null

    $gradleLogPath = Join-Path $runDirectory 'gradle-output.log'
    $gradleExitCode = 0
    $testOutputSnapshot = Get-NewAudioFileSnapshot -SourceRoot $benchmarkOutputRoot

    Push-Location $repositoryRoot
    try {
        & $gradleWrapper @arguments 2>&1 | Tee-Object -FilePath $gradleLogPath
        $gradleExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if ($gradleExitCode -eq 0) { break }

    $recoveredOutputs = @(Copy-NewAudioCurrentTestOutputs -SourceRoot $benchmarkOutputRoot `
        -DestinationRoot (Join-Path $runDirectory 'additional-test-output') -StartedAtUtc $startedAt `
        -Snapshot $testOutputSnapshot)
    $failureMetadata = [ordered]@{
        schemaVersion = 3
        status = 'failed'
        runId = $runId
        mode = 'metrics'
        startedAtUtc = $startedAt.ToString('o')
        failedAtUtc = [DateTime]::UtcNow.ToString('o')
        exitCode = $gradleExitCode
        commit = [string]$provenance.commit
        fullComposeTracing = $false
        testClass = $TestClass
        resolvedTestClass = $resolvedClassName
        resolvedTestMethod = $resolvedMethodName
        metricShard = if ([string]::IsNullOrWhiteSpace($MetricShard)) { $null } else { $MetricShard }
        gradleTask = ':benchmark:connectedBenchmarkAndroidTest'
        iterationOverride = $iterationOverride
        requestedIterations = if ($iterationOverride) { $Iterations } else { $null }
        baselineEligible = $false
        repository = $provenance
        environment = $environment
        retry = [ordered]@{
            configuredRetryCount = $RetryCount
            attempt = $attempt
            maximumAttempts = 1 + $RetryCount
            willRetry = $attempt -le $RetryCount
        }
        gradleLog = [IO.Path]::GetFileName($gradleLogPath)
        resultDirectory = $runDirectory.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
        runner = [ordered]@{
            path = $PSCommandPath.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
            sha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
            gradleArguments = @($arguments)
        }
        recoveredTestOutputs = $recoveredOutputs
    }
    $failureJson = $failureMetadata | ConvertTo-Json -Depth 10
    $failureJson | Set-Content -LiteralPath (Join-Path $runDirectory 'run-failure.json') -Encoding utf8
    $failureJson | Set-Content -LiteralPath (Join-Path $runDirectory 'run-manifest.json') -Encoding utf8
    $relativeFailureDirectory = $runDirectory.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
    $priorFailureDirectories.Add($relativeFailureDirectory)

    if ($attempt -le $RetryCount) {
        Write-Warning "Metric benchmark attempt $attempt failed with exit code $gradleExitCode. Retrying once; failure artifacts: $runDirectory"
        continue
    }
    Write-Error "Metric benchmark failed after $attempt attempt(s) with exit code $gradleExitCode. Failure artifacts: $runDirectory" -ErrorAction Continue
    exit $gradleExitCode
} while ($attempt -le $RetryCount)

$artifacts = @()
if (Test-Path -LiteralPath $benchmarkOutputRoot -PathType Container) {
    $artifacts = @(Get-ChildItem -LiteralPath $benchmarkOutputRoot -Recurse -File |
        Where-Object {
            $_.LastWriteTimeUtc -ge $startedAt.AddSeconds(-2) -and
            $_.Extension -in @('.json', '.csv')
        } |
        Sort-Object FullName)
}
if ($artifacts.Count -eq 0) {
    throw "Gradle succeeded but no current metric JSON/CSV artifact was found below '$benchmarkOutputRoot'."
}

$artifactMetadata = [Collections.Generic.List[object]]::new()
foreach ($artifact in $artifacts) {
    $name = '{0}-{1}' -f ([IO.Path]::GetFileNameWithoutExtension($artifact.Name)),
        ([Guid]::NewGuid().ToString('N').Substring(0, 8))
    $destination = Join-Path $rawDirectory ($name + $artifact.Extension)
    Copy-Item -LiteralPath $artifact.FullName -Destination $destination
    $relativeSource = $artifact.FullName.Substring($repositoryRoot.Length).TrimStart('\', '/')
    $artifactMetadata.Add([ordered]@{
        sourcePath = $relativeSource
        copiedFileName = [IO.Path]::GetFileName($destination)
        lengthBytes = $artifact.Length
        sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    })
}

$metadata = [ordered]@{
    schemaVersion = 3
    status = 'succeeded'
    runId = $runId
    mode = 'metrics'
    fullComposeTracing = $false
    baselineEligible = Test-NewAudioBaselineEligibility -Provenance $provenance `
        -AllowDirty ([bool]$AllowDirty) -IterationOverride $iterationOverride
    startedAtUtc = $startedAt.ToString('o')
    completedAtUtc = [DateTime]::UtcNow.ToString('o')
    commit = $provenance.commit
    repository = $provenance
    testClass = $TestClass
    resolvedTestClass = $resolvedClassName
    resolvedTestMethod = $resolvedMethodName
    metricShard = if ([string]::IsNullOrWhiteSpace($MetricShard)) { $null } else { $MetricShard }
    gradleTask = ':benchmark:connectedBenchmarkAndroidTest'
    iterationOverride = $PSBoundParameters.ContainsKey('Iterations')
    requestedIterations = if ($PSBoundParameters.ContainsKey('Iterations')) { $Iterations } else { $null }
    allowDirty = [bool]$AllowDirty
    retry = [ordered]@{
        configuredRetryCount = $RetryCount
        successfulAttempt = $attempt
        priorFailedAttempts = $priorFailureDirectories.Count
        priorFailureDirectories = @($priorFailureDirectories)
    }
    artifactCount = $artifacts.Count
    artifacts = @($artifactMetadata)
    environment = $environment
    resultDirectory = $runDirectory.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
    runner = [ordered]@{
        path = $PSCommandPath.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
        gradleArguments = @($arguments)
    }
}
$runMetadataPath = Join-Path $runDirectory 'run-metadata.json'
$metadataJson = $metadata | ConvertTo-Json -Depth 10
$metadataJson | Set-Content -LiteralPath $runMetadataPath -Encoding utf8
$metadataJson | Set-Content -LiteralPath (Join-Path $runDirectory 'run-manifest.json') -Encoding utf8

$benchmarkJson = @(Get-ChildItem -LiteralPath $rawDirectory -Filter '*.json' -File |
    Sort-Object FullName |
    Select-Object -ExpandProperty FullName)
if ($benchmarkJson.Count -eq 0) {
    throw 'No Macrobenchmark JSON is available for candidate normalization.'
}
$normalizer = Join-Path $scriptRoot 'normalize-metrics.ps1'
& $normalizer -BenchmarkJsonPath $benchmarkJson -RunMetadataPath $runMetadataPath `
    -OutputPath (Join-Path $runDirectory 'candidate-series.json')

Write-Host "Metric benchmark completed. Results: $runDirectory"
