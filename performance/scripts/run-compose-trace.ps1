[CmdletBinding()]
param(
    [string]$TestClass = 'com.example.newaudio.benchmark.TraceCaptureTest',
    [string]$OutputDirectory,
    [string]$TraceProcessorPath,
    [string[]]$AdditionalGradleArguments = @(),
    [string]$CacheState,
    [string]$DeviceRoleId,
    [switch]$AllowDirty,
    [switch]$SkipSummary,
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
$summaryScript = Join-Path $scriptRoot 'summarize-trace.ps1'
$traceJourneyByMethod = [ordered]@{
    traceColdStartup = 'ST-01'; traceNavigationSettings = 'NV-01'; traceNavigationPlaylist = 'NV-02'
    traceAudioBrowserScroll = 'BR-01'; traceAudioBrowserIdle = 'BR-02'; traceVideoBrowserScroll = 'BR-03'
    traceVideoGalleryTwoColumns = 'BR-04-GRID-2-COLD'; traceVideoGalleryThreeColumns = 'BR-04-GRID-3-COLD'
    traceVideoGalleryFourColumns = 'BR-04-GRID-4-COLD'; traceNestedFolderColdCache = 'BR-05'
    traceNestedFolderWarmCache = 'BR-06'; traceAudioPlaylistScroll = 'PL-01'; traceVideoPlaylistScroll = 'PL-02'
    traceAudioMiniPlayerIdle = 'AU-01'; traceAudioFullPlayerIdle = 'AU-02'; traceAudioControls = 'AU-03'
    traceSettingsDuringAudio = 'AU-04'; traceAudioPausedControlIdle = 'AU-05'; traceAudioRepeatOffIdle = 'AU-06'
    traceAudioRepeatOneIdle = 'AU-07'; traceAudioMarqueeOffIdle = 'AU-08'; traceAudioMarqueeOnIdle = 'AU-09'
    traceVideoInlineIdle = 'VI-01'; traceVideoFullscreenIdle = 'VI-02'; traceVideoControlsSeekAndMarker = 'VI-03'
    traceVideoFullscreenInlineTransition = 'VI-04'; traceVideoSwipeNextPrevious = 'VI-05'
    traceVideoFullscreenMarkersOff = 'VI-06-MARKERS-OFF'; traceVideoFullscreenMarkersOnStable = 'VI-06-MARKERS-ON'
}

function Resolve-RequestedTraceJourney {
    param([string]$FileName, [string]$Selector)

    foreach ($entry in $traceJourneyByMethod.GetEnumerator()) {
        if ($FileName -like "*$($entry.Key)*" -or $Selector -match "#$([regex]::Escape($entry.Key))$") {
            $journeyId = [string]$entry.Value
            $decoderPath = if ($journeyId -eq 'BR-03' -or $journeyId -like 'BR-04-*' -or $journeyId -eq 'PL-02') {
                'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER'
            } else {
                'NOT_APPLICABLE'
            }
            return [ordered]@{
                method = [string]$entry.Key
                journeyId = $journeyId
                cacheState = if ($journeyId -eq 'BR-06') {
                    'WARM_PRELOADED_IMAGE_CACHE'
                } else {
                    'COLD_EMPTY_IMAGE_CACHE'
                }
                decoderPath = $decoderPath
            }
        }
    }
    return [ordered]@{
        method = $null
        journeyId = 'unspecified'
        cacheState = 'COLD_EMPTY_IMAGE_CACHE'
        decoderPath = 'NOT_APPLICABLE'
    }
}

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

function Resolve-TraceProcessor {
    if (-not [string]::IsNullOrWhiteSpace($TraceProcessorPath)) {
        $resolved = [IO.Path]::GetFullPath($TraceProcessorPath)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "Trace Processor not found at '$resolved'."
        }
        return $resolved
    }
    foreach ($name in @('trace_processor_shell.exe', 'trace_processor_shell', 'trace_processor.exe', 'trace_processor')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($null -ne $command) { return $command.Source }
    }
    return $null
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
    if (-not (Test-Path -LiteralPath $summaryScript -PathType Leaf)) {
        throw "Summary script not found at '$summaryScript'."
    }
    $adb = $null
    if ($RequireAdb -or $RequireDevice) {
        $adb = Get-AdbPath
        if ([string]::IsNullOrWhiteSpace($adb)) {
            throw 'adb was not found in PATH, Android SDK environment variables, or local.properties.'
        }
    }
    if ($RequireDevice) {
        $devices = @(& $adb devices 2>&1)
        if ($LASTEXITCODE -ne 0) { throw "adb devices failed: $($devices -join [Environment]::NewLine)" }
        $ready = @($devices | Where-Object { $_ -match '^\S+\s+device$' })
        if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL)) {
            $ready = @($ready | Where-Object { ($_ -split '\s+')[0] -eq $env:ANDROID_SERIAL })
            if ($ready.Count -ne 1) {
                throw "ANDROID_SERIAL '$($env:ANDROID_SERIAL)' does not identify exactly one ready device. Output: $($devices -join '; ')"
            }
            $ready = @($ready[0])
        }
        if ($ready.Count -ne 1) {
            throw "Exactly one ready Android device is required; found $($ready.Count). Output: $($devices -join '; ')"
        }
    }
    if (-not $SkipSummary -and [string]::IsNullOrWhiteSpace((Resolve-TraceProcessor))) {
        throw 'Trace Processor was not found. Set -TraceProcessorPath or use -SkipSummary.'
    }
}

if ($TestClass -notmatch '^com\.example\.newaudio\.benchmark\.TraceCaptureTest(?:#[A-Za-z0-9_$]+)?$') {
    throw "TestClass '$TestClass' is outside the trace-runner boundary. Only TraceCaptureTest may enable Full Compose Tracing."
}
if ($TestClass -match '#([A-Za-z0-9_$]+)$' -and -not $traceJourneyByMethod.Contains($Matches[1])) {
    throw "Trace method '$($Matches[1])' has no versioned journey contract."
}
Assert-NewAudioAdditionalGradleArguments -Arguments $AdditionalGradleArguments
$provenance = Get-NewAudioRepositoryProvenance -RepositoryRoot $repositoryRoot
Assert-NewAudioRepositoryPolicy -Provenance $provenance -AllowDirty:$AllowDirty
$resolvedCacheState = Resolve-NewAudioCacheState -TestClass $TestClass -RequestedState $CacheState

$safeOutput = Resolve-SafeOutputDirectory -Candidate $OutputDirectory
$arguments = @(
    ':benchmark:connectedBenchmarkAndroidTest',
    '-PfullTracing=true',
    "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass",
    '-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.fullTracing.enable=true'
) + $AdditionalGradleArguments
if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SERIAL)) {
    $arguments += "-Pandroid.injected.device.serial=$($env:ANDROID_SERIAL)"
}

$traceSnapshot = @{}
if (Test-Path -LiteralPath $benchmarkOutputRoot -PathType Container) {
    foreach ($existingTrace in @(Get-ChildItem -LiteralPath $benchmarkOutputRoot -Recurse -File |
        Where-Object {
            $_.Name.EndsWith('.perfetto-trace', [StringComparison]::OrdinalIgnoreCase) -or
            $_.Extension -eq '.trace'
        })) {
        $traceSnapshot[$existingTrace.FullName] = '{0}:{1}' -f
            $existingTrace.LastWriteTimeUtc.Ticks, $existingTrace.Length
    }
}

if ($DryRun) {
    Assert-Prerequisites
    Write-Host 'Dry run only; no trace is captured.'
    Write-Host ("Working directory: {0}" -f $repositoryRoot)
    Write-Host ("Command: {0} {1}" -f $gradleWrapper, ($arguments -join ' '))
    Write-Host ("Results root: {0}" -f $safeOutput)
    Write-Host ("Repository dirty: {0}; baseline eligible: false" -f $provenance.dirty)
    return
}

Assert-Prerequisites -RequireAdb -RequireDevice
if ($CheckPrerequisites) {
    Write-Host 'Full Compose Tracing prerequisites are satisfied.'
    return
}

$startedAt = [DateTime]::UtcNow
$runId = 'compose-trace-{0}' -f $startedAt.ToString('yyyyMMdd-HHmmssfff')
$runDirectory = Join-Path $safeOutput $runId
$traceDirectory = Join-Path $runDirectory 'traces'
if (Test-Path -LiteralPath $runDirectory) {
    throw "Run directory already exists; refusing to mix scenarios: '$runDirectory'."
}
New-Item -ItemType Directory -Path $traceDirectory | Out-Null

$adb = Get-AdbPath
$environment = Get-NewAudioRunEnvironment -AdbPath $adb -RepositoryRoot $repositoryRoot `
    -Mode 'diagnostic-full-compose-tracing' -CacheState $resolvedCacheState -DeviceRoleId $DeviceRoleId
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
if ($gradleExitCode -ne 0) {
    $recoveredOutputs = @(Copy-NewAudioCurrentTestOutputs -SourceRoot $benchmarkOutputRoot `
        -DestinationRoot (Join-Path $runDirectory 'additional-test-output') -StartedAtUtc $startedAt `
        -Snapshot $testOutputSnapshot)
    $failureTraces = [Collections.Generic.List[object]]::new()
    if (Test-Path -LiteralPath $benchmarkOutputRoot -PathType Container) {
        foreach ($trace in @(Get-ChildItem -LiteralPath $benchmarkOutputRoot -Recurse -File |
            Where-Object {
                $isTrace = $_.Name.EndsWith('.perfetto-trace', [StringComparison]::OrdinalIgnoreCase) -or $_.Extension -eq '.trace'
                $signature = '{0}:{1}' -f $_.LastWriteTimeUtc.Ticks, $_.Length
                $isTrace -and (-not $traceSnapshot.ContainsKey($_.FullName) -or $traceSnapshot[$_.FullName] -ne $signature) -and
                    $_.LastWriteTimeUtc -ge $startedAt.AddSeconds(-2)
            } | Sort-Object FullName)) {
            $destination = Join-Path $traceDirectory (('failure-{0}-{1}' -f ([Guid]::NewGuid().ToString('N').Substring(0, 8)), $trace.Name) -replace '[^A-Za-z0-9._-]', '_')
            Copy-Item -LiteralPath $trace.FullName -Destination $destination
            $failureTraces.Add([ordered]@{
                fileName = [IO.Path]::GetFileName($destination)
                sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
            })
        }
    }
    $failureMetadata = [ordered]@{
        schemaVersion = 3
        status = 'failed'
        runId = $runId
        mode = 'diagnostic-full-compose-tracing'
        startedAtUtc = $startedAt.ToString('o')
        failedAtUtc = [DateTime]::UtcNow.ToString('o')
        exitCode = $gradleExitCode
        commit = [string]$provenance.commit
        fullComposeTracing = $true
        testClass = $TestClass
        resolvedTestClass = 'TraceCaptureTest'
        resolvedTestMethod = if ($TestClass -match '#([A-Za-z0-9_$]+)$') { $Matches[1] } else { $null }
        gradleTask = ':benchmark:connectedBenchmarkAndroidTest'
        baselineEligible = $false
        repository = $provenance
        environment = $environment
        gradleLog = [IO.Path]::GetFileName($gradleLogPath)
        resultDirectory = $runDirectory.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
        runner = [ordered]@{
            path = $PSCommandPath.Substring($repositoryRoot.Length).TrimStart('\', '/').Replace('\', '/')
            sha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
            gradleArguments = @($arguments)
        }
        recoveredTraces = @($failureTraces)
        recoveredTestOutputs = $recoveredOutputs
    }
    $failureJson = $failureMetadata | ConvertTo-Json -Depth 10
    $failureJson | Set-Content -LiteralPath (Join-Path $runDirectory 'run-failure.json') -Encoding utf8
    $failureJson | Set-Content -LiteralPath (Join-Path $runDirectory 'run-manifest.json') -Encoding utf8
    Write-Error "Compose trace benchmark failed with exit code $gradleExitCode. Failure artifacts: $runDirectory" -ErrorAction Continue
    exit $gradleExitCode
}

$newTraces = @()
if (Test-Path -LiteralPath $benchmarkOutputRoot -PathType Container) {
    $newTraces = @(Get-ChildItem -LiteralPath $benchmarkOutputRoot -Recurse -File |
        Where-Object {
            $isTrace = $_.Name.EndsWith('.perfetto-trace', [StringComparison]::OrdinalIgnoreCase) -or
                $_.Extension -eq '.trace'
            $signature = '{0}:{1}' -f $_.LastWriteTimeUtc.Ticks, $_.Length
            $isTrace -and
                (-not $traceSnapshot.ContainsKey($_.FullName) -or
                 $traceSnapshot[$_.FullName] -ne $signature) -and
                $_.LastWriteTimeUtc -ge $startedAt.AddSeconds(-2)
        } |
        Sort-Object FullName)
}
if ($newTraces.Count -eq 0) {
    throw "Gradle succeeded but no new Perfetto trace was found below '$benchmarkOutputRoot'."
}

$copiedTraces = @()
$index = 0
foreach ($trace in $newTraces) {
    $index++
    $safeName = $trace.Name -replace '[^A-Za-z0-9._-]', '_'
    $destination = Join-Path $traceDirectory ('{0:D2}-{1}' -f $index, $safeName)
    Copy-Item -LiteralPath $trace.FullName -Destination $destination
    $copiedTraces += Get-Item -LiteralPath $destination
}

$traceMetadata = for ($traceIndex = 0; $traceIndex -lt $copiedTraces.Count; $traceIndex++) {
    $trace = $copiedTraces[$traceIndex]
    $requestedIdentity = Resolve-RequestedTraceJourney -FileName $trace.Name -Selector $TestClass
    $iteration = if ($trace.Name -match '(?i)(?:iter|iteration)[_-]?(\d+)') {
        [int]$Matches[1]
    } else {
        $traceIndex + 1
    }
    [ordered]@{
        fileName = $trace.Name
        lengthBytes = $trace.Length
        sha256 = (Get-FileHash -LiteralPath $trace.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        journeyId = $requestedIdentity.journeyId
        testMethod = $requestedIdentity.method
        iteration = $iteration
        capturedAtUtc = $trace.LastWriteTimeUtc.ToString('o')
        cacheState = $requestedIdentity.cacheState
        decoderPath = $requestedIdentity.decoderPath
    }
}
$metadata = [ordered]@{
    schemaVersion = 3
    status = 'succeeded'
    runId = $runId
    mode = 'diagnostic-full-compose-tracing'
    fullComposeTracing = $true
    baselineEligible = $false
    startedAtUtc = $startedAt.ToString('o')
    completedAtUtc = [DateTime]::UtcNow.ToString('o')
    commit = $provenance.commit
    repository = $provenance
    testClass = $TestClass
    resolvedTestClass = 'TraceCaptureTest'
    resolvedTestMethod = if ($TestClass -match '#([A-Za-z0-9_$]+)$') { $Matches[1] } else { $null }
    allowDirty = [bool]$AllowDirty
    traceCount = $copiedTraces.Count
    traces = @($traceMetadata)
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
$runManifestPath = Join-Path $runDirectory 'run-manifest.json'
$reportingManifest = $metadataJson | ConvertFrom-Json
$reportingManifest.status = 'reporting'
$reportingManifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $runManifestPath -Encoding utf8

try {
    if (-not $SkipSummary) {
        $processor = Resolve-TraceProcessor
        foreach ($trace in $copiedTraces) {
            $traceIdentity = @($traceMetadata | Where-Object { $_.fileName -eq $trace.Name })[0]
            $reportDirectory = Join-Path $runDirectory (Join-Path 'reports' ([IO.Path]::GetFileNameWithoutExtension($trace.Name)))
            & $summaryScript -TracePath $trace.FullName -OutputDirectory $reportDirectory `
                -Journey $traceIdentity.journeyId `
                -Mode 'diagnostic-full-compose-tracing' `
                -CompilationMode 'Partial(warmupIterations=3)' -TraceProcessorPath $processor `
                -RunMetadataPath $runMetadataPath
            if ($LASTEXITCODE -ne 0) {
                throw "Trace summary failed for '$($trace.FullName)' with exit code $LASTEXITCODE."
            }
        }
    }
} catch {
    $failedManifest = $metadataJson | ConvertFrom-Json
    $failedManifest.status = 'failed'
    $failedManifest | Add-Member failedAtUtc ([DateTime]::UtcNow.ToString('o'))
    $failedManifest | Add-Member reportError $_.Exception.Message
    $failedManifest | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $runManifestPath -Encoding utf8
    throw
}
$metadataJson | Set-Content -LiteralPath $runManifestPath -Encoding utf8

Write-Host "Compose trace capture completed. Results: $runDirectory"
