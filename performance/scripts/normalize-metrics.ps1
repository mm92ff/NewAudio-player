[CmdletBinding()]
param(
    [string[]]$BenchmarkJsonPath,
    [string]$RunMetadataPath,
    [string]$OutputPath,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$journeyContracts = @{
    st01ColdStartupToBrowserReady = @('ST-01', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    st02WarmStartupToBrowserReady = @('ST-02', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    nv01BrowserSettingsBrowser = @('NV-01', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    nv02BrowserPlaylistBrowser = @('NV-02', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    br01AudioListScroll = @('BR-01', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    br02AudioListIdleWithMiniPlayer = @('BR-02', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    br03VideoListScroll = @('BR-03', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    br04VideoGalleryTwoColumns = @('BR-04-GRID-2-COLD', '2-columns', 'COLD_EMPTY_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    br04VideoGalleryThreeColumns = @('BR-04-GRID-3-COLD', '3-columns', 'COLD_EMPTY_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    br04VideoGalleryFourColumns = @('BR-04-GRID-4-COLD', '4-columns', 'COLD_EMPTY_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    br04VideoGalleryTwoColumnsWarm = @('BR-04-GRID-2-WARM', '2-columns', 'WARM_PRELOADED_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    br04VideoGalleryThreeColumnsWarm = @('BR-04-GRID-3-WARM', '3-columns', 'WARM_PRELOADED_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    br04VideoGalleryFourColumnsWarm = @('BR-04-GRID-4-WARM', '4-columns', 'WARM_PRELOADED_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    br05NestedFolderColdCache = @('BR-05', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    br06NestedFolderWarmCache = @('BR-06', 'default', 'WARM_PRELOADED_IMAGE_CACHE', 'NOT_APPLICABLE')
    pl01AudioPlaylistScroll = @('PL-01', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    pl02VideoPlaylistScroll = @('PL-02', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER')
    au01MiniPlayerIdle = @('AU-01', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au02FullPlayerIdle = @('AU-02', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au03SeekPauseResumeNext = @('AU-03', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au04SettingsScrollDuringPlayback = @('AU-04', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au05PausedControlIdle = @('AU-05', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au06MiniPlayerRepeatOffIdle = @('AU-06', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au07MiniPlayerRepeatOneIdle = @('AU-07', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au08LongTitleMarqueeOffIdle = @('AU-08', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    au09LongTitleMarqueeOnIdle = @('AU-09', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    vi01InlineVideoIdle = @('VI-01', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    vi02FullscreenIdle = @('VI-02', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    vi03ControlsSeekAndMarker = @('VI-03', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    vi04FullscreenInlineTransition = @('VI-04', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    vi05SwipeNextPrevious = @('VI-05', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    vi06FullscreenIdleMarkersOff = @('VI-06-MARKERS-OFF', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
    vi06FullscreenIdleMarkersOn = @('VI-06-MARKERS-ON', 'default', 'COLD_EMPTY_IMAGE_CACHE', 'NOT_APPLICABLE')
}

function Get-JourneyContract {
    param([Parameter(Mandatory = $true)][string]$BenchmarkName)

    if (-not $journeyContracts.ContainsKey($BenchmarkName)) {
        throw "Benchmark method '$BenchmarkName' has no versioned journey contract."
    }
    return $journeyContracts[$BenchmarkName]
}

function Get-Percentile {
    param([Parameter(Mandatory = $true)][double[]]$Values,
        [Parameter(Mandatory = $true)][ValidateSet(50, 90, 95, 99)][int]$Percent)

    if ($Values.Count -eq 0) { throw 'A sampled metric run contains no values.' }
    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 1) { return [double]$sorted[0] }
    $rank = ($Percent / 100.0) * ($sorted.Count - 1)
    $lower = [math]::Floor($rank)
    $upper = [math]::Ceiling($rank)
    if ($lower -eq $upper) { return [double]$sorted[$lower] }
    return [double]$sorted[$lower] +
        ($rank - $lower) * ([double]$sorted[$upper] - [double]$sorted[$lower])
}

function Get-JourneyId {
    param([Parameter(Mandatory = $true)][string]$BenchmarkName)
    return [string](Get-JourneyContract -BenchmarkName $BenchmarkName)[0]
}

function Get-JourneyDimensions {
    param([Parameter(Mandatory = $true)][string]$BenchmarkName)
    $contract = Get-JourneyContract -BenchmarkName $BenchmarkName
    return [ordered]@{
        layoutVariant = [string]$contract[1]
        cacheState = [string]$contract[2]
        decoderPath = [string]$contract[3]
    }
}

function Get-MetricPolicy {
    param([Parameter(Mandatory = $true)][string]$MetricName)

    if ($MetricName -match '(?i)Count$') {
        return [ordered]@{
            unit = 'count'
            type = 'count'
            direction = 'informational'
            gateEligible = $false
        }
    }
    if ($MetricName -match '(?i)Ms(?:\.P(?:50|90|95|99))?$') {
        return [ordered]@{
            unit = 'ms'
            type = 'latency'
            direction = 'lower-is-better'
            gateEligible = $true
        }
    }
    throw "Metric '$MetricName' has no explicit unit/type/direction policy."
}

function Convert-NewAudioMetrics {
    param([string[]]$JsonPaths, [string]$MetadataPath)

    $resolvedMetadata = [IO.Path]::GetFullPath($MetadataPath)
    if (-not (Test-Path -LiteralPath $resolvedMetadata -PathType Leaf)) {
        throw "Run metadata not found: $resolvedMetadata"
    }
    $runMetadata = Get-Content -LiteralPath $resolvedMetadata -Raw | ConvertFrom-Json
    foreach ($required in @('schemaVersion', 'runId', 'mode', 'fullComposeTracing', 'baselineEligible', 'commit', 'repository', 'environment')) {
        if ($null -eq $runMetadata.PSObject.Properties[$required]) {
            throw "Run metadata is missing required property '$required'."
        }
    }
    if ([int]$runMetadata.schemaVersion -ne 3) {
        throw "Unsupported run metadata schemaVersion '$($runMetadata.schemaVersion)'; expected 3."
    }
    if ($runMetadata.mode -ne 'metrics' -or [bool]$runMetadata.fullComposeTracing) {
        throw 'Only metric-mode metadata without Full Compose Tracing can be normalized.'
    }
    if ([string]::IsNullOrWhiteSpace([string]$runMetadata.runId)) {
        throw 'Run metadata contains an empty runId.'
    }
    foreach ($property in @('commit', 'dirty', 'statusSha256', 'diffSha256', 'untrackedSha256',
            'worktreeStateSha256', 'untrackedPresent')) {
        if ($null -eq $runMetadata.repository.PSObject.Properties[$property] -or
            [string]::IsNullOrWhiteSpace([string]$runMetadata.repository.$property)) {
            throw "Run metadata repository provenance is missing '$property'."
        }
    }
    if ([string]$runMetadata.commit -ne [string]$runMetadata.repository.commit) {
        throw 'Run metadata commit does not match repository provenance.'
    }
    if ([bool]$runMetadata.baselineEligible -and [bool]$runMetadata.repository.dirty) {
        throw 'Dirty run metadata cannot be baseline eligible.'
    }

    $series = [Collections.Generic.List[object]]::new()
    $seenKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $sourceFiles = [Collections.Generic.List[object]]::new()
    foreach ($candidatePath in $JsonPaths) {
        $resolved = [IO.Path]::GetFullPath($candidatePath)
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
            throw "Macrobenchmark JSON not found: $resolved"
        }
        $source = Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
        if ($null -eq $source.PSObject.Properties['benchmarks']) {
            throw "File is not a Macrobenchmark result JSON: $resolved"
        }
        $sourceFiles.Add([ordered]@{
            fileName = [IO.Path]::GetFileName($resolved)
            sha256 = (Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLowerInvariant()
        })

        foreach ($benchmark in @($source.benchmarks)) {
            $benchmarkName = [string]$benchmark.name
            $journey = Get-JourneyId -BenchmarkName $benchmarkName
            $dimensions = Get-JourneyDimensions -BenchmarkName $benchmarkName
            if ($null -ne $benchmark.PSObject.Properties['metrics']) {
                foreach ($metricProperty in $benchmark.metrics.PSObject.Properties) {
                    $values = @($metricProperty.Value.runs | ForEach-Object { [double]$_ })
                    if ($values.Count -eq 0) { continue }
                    $policy = Get-MetricPolicy -MetricName $metricProperty.Name
                    $key = "$journey|$($metricProperty.Name)|$($policy.unit)|$($dimensions.cacheState)|$($dimensions.layoutVariant)|$($dimensions.decoderPath)"
                    if (-not $seenKeys.Add($key)) { throw "Duplicate metric series '$key'." }
                    $series.Add([ordered]@{
                        journey = $journey
                        benchmark = $benchmarkName
                        metric = $metricProperty.Name
                        layoutVariant = $dimensions.layoutVariant
                        cacheState = $dimensions.cacheState
                        decoderPath = $dimensions.decoderPath
                        unit = $policy.unit
                        type = $policy.type
                        direction = $policy.direction
                        gateEligible = $policy.gateEligible
                        values = $values
                        batches = @([ordered]@{ runId = [string]$runMetadata.runId; values = $values })
                        independentSeriesCount = 1
                    })
                }
            }
            if ($null -ne $benchmark.PSObject.Properties['sampledMetrics']) {
                foreach ($metricProperty in $benchmark.sampledMetrics.PSObject.Properties) {
                    $runs = @($metricProperty.Value.runs)
                    foreach ($percent in @(50, 90, 95, 99)) {
                        $values = @($runs | ForEach-Object {
                            Get-Percentile -Values @($_ | ForEach-Object { [double]$_ }) -Percent $percent
                        })
                        if ($values.Count -eq 0) { continue }
                        $metricName = '{0}.P{1}' -f $metricProperty.Name, $percent
                        $policy = Get-MetricPolicy -MetricName $metricName
                        $key = "$journey|$metricName|$($policy.unit)|$($dimensions.cacheState)|$($dimensions.layoutVariant)|$($dimensions.decoderPath)"
                        if (-not $seenKeys.Add($key)) { throw "Duplicate metric series '$key'." }
                        $series.Add([ordered]@{
                            journey = $journey
                            benchmark = $benchmarkName
                            metric = $metricName
                            layoutVariant = $dimensions.layoutVariant
                            cacheState = $dimensions.cacheState
                            decoderPath = $dimensions.decoderPath
                            unit = $policy.unit
                            type = $policy.type
                            direction = $policy.direction
                            gateEligible = $policy.gateEligible
                            values = $values
                            batches = @([ordered]@{ runId = [string]$runMetadata.runId; values = $values })
                            independentSeriesCount = 1
                        })
                    }
                }
            }
        }
    }

    if ($series.Count -eq 0) { throw 'No metric series could be normalized.' }
    return [ordered]@{
        schemaVersion = 2
        mode = 'metrics'
        fullComposeTracing = $false
        baselineEligible = [bool]$runMetadata.baselineEligible
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        commit = [string]$runMetadata.commit
        repository = $runMetadata.repository
        runId = [string]$runMetadata.runId
        runBatches = @([ordered]@{
            runId = [string]$runMetadata.runId
            commit = [string]$runMetadata.commit
            repository = $runMetadata.repository
        })
        independentSeriesCount = 1
        environment = $runMetadata.environment
        sourceFiles = $sourceFiles.ToArray()
        metricSeries = $series.ToArray()
    }
}

function Invoke-SelfTest {
    $root = Join-Path ([IO.Path]::GetTempPath()) ('newaudio-normalizer-{0}' -f [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $root | Out-Null
    try {
        $metadataPath = Join-Path $root 'run.json'
        $benchmarkPath = Join-Path $root 'benchmark.json'
        [ordered]@{
            schemaVersion = 3; runId = 'metrics-selftest'; mode = 'metrics'; fullComposeTracing = $false
            baselineEligible = $true; commit = ('a' * 40)
            repository = [ordered]@{
                commit = ('a' * 40); dirty = $false; statusSha256 = 'status'; diffSha256 = 'diff'
                untrackedSha256 = 'untracked'; worktreeStateSha256 = 'state'; untrackedPresent = $false
            }
            environment = [ordered]@{ marker = 'selftest' }
        } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $metadataPath -Encoding utf8
        [ordered]@{ benchmarks = @(
            [ordered]@{ name = 'br04VideoGalleryTwoColumns'; metrics = [ordered]@{ frameCount = [ordered]@{ runs = @(8, 9) } }; sampledMetrics = [ordered]@{ frameDurationCpuMs = [ordered]@{ runs = @(@(10, 20), @(20, 30)) } } },
            [ordered]@{ name = 'br04VideoGalleryThreeColumns'; metrics = [ordered]@{ timeToInitialDisplayMs = [ordered]@{ runs = @(11, 12) } } },
            [ordered]@{ name = 'br04VideoGalleryFourColumns'; metrics = [ordered]@{ timeToInitialDisplayMs = [ordered]@{ runs = @(12, 13) } } },
            [ordered]@{ name = 'br04VideoGalleryTwoColumnsWarm'; metrics = [ordered]@{ timeToInitialDisplayMs = [ordered]@{ runs = @(10, 11) } } },
            [ordered]@{ name = 'br06NestedFolderWarmCache'; metrics = [ordered]@{ frameCount = [ordered]@{ runs = @(4) } } },
            [ordered]@{ name = 'pl02VideoPlaylistScroll'; metrics = [ordered]@{ frameCount = [ordered]@{ runs = @(5) } } },
            [ordered]@{ name = 'vi06FullscreenIdleMarkersOn'; metrics = [ordered]@{ frameCount = [ordered]@{ runs = @(10) } } },
            [ordered]@{ name = 'vi06FullscreenIdleMarkersOff'; metrics = [ordered]@{ frameCount = [ordered]@{ runs = @(10) } } }
        ) } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $benchmarkPath -Encoding utf8

        $actual = Convert-NewAudioMetrics -JsonPaths @($benchmarkPath) -MetadataPath $metadataPath |
            ConvertTo-Json -Depth 12 | ConvertFrom-Json
        $journeys = @($actual.metricSeries | Select-Object -ExpandProperty journey -Unique)
        foreach ($expected in @('BR-04-GRID-2-COLD', 'BR-04-GRID-3-COLD', 'BR-04-GRID-4-COLD', 'BR-04-GRID-2-WARM')) {
            if ($expected -notin $journeys) { throw "Self-test failed: missing journey '$expected'." }
        }
        foreach ($expected in @('VI-06-MARKERS-ON', 'VI-06-MARKERS-OFF')) {
            if ($expected -notin $journeys) { throw "Self-test failed: missing journey '$expected'." }
        }
        $cold = @($actual.metricSeries | Where-Object journey -eq 'BR-04-GRID-2-COLD')[0]
        $warm = @($actual.metricSeries | Where-Object journey -eq 'BR-04-GRID-2-WARM')[0]
        if ($cold.cacheState -ne 'COLD_EMPTY_IMAGE_CACHE' -or
            $warm.cacheState -ne 'WARM_PRELOADED_IMAGE_CACHE' -or
            $cold.layoutVariant -ne '2-columns' -or
            $cold.decoderPath -ne 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER') {
            throw 'Self-test failed: gallery comparison dimensions are incorrect.'
        }
        $nestedWarm = @($actual.metricSeries | Where-Object journey -eq 'BR-06')[0]
        $videoPlaylist = @($actual.metricSeries | Where-Object journey -eq 'PL-02')[0]
        if ($nestedWarm.cacheState -ne 'WARM_PRELOADED_IMAGE_CACHE' -or
            $videoPlaylist.decoderPath -ne 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER') {
            throw 'Self-test failed: warm-folder or video-playlist dimensions are incorrect.'
        }
        $unknownRejected = $false
        try { $null = Get-JourneyId -BenchmarkName 'br99TypoJourney' } catch { $unknownRejected = $true }
        if (-not $unknownRejected) { throw 'Self-test failed: unknown benchmark method was accepted.' }
        $frameCount = @($actual.metricSeries | Where-Object metric -eq 'frameCount')[0]
        if ($frameCount.unit -ne 'count' -or $frameCount.direction -ne 'informational' -or [bool]$frameCount.gateEligible) {
            throw 'Self-test failed: frameCount is not an informational count.'
        }
        $idleCountPolicy = Get-MetricPolicy -MetricName 'idleFrameCount'
        if ($idleCountPolicy.unit -ne 'count' -or $idleCountPolicy.direction -ne 'informational' -or
            [bool]$idleCountPolicy.gateEligible) {
            throw 'Self-test failed: idleFrameCount is not an informational count.'
        }
        $timing = @($actual.metricSeries | Where-Object metric -eq 'timeToInitialDisplayMs')[0]
        if ($timing.unit -ne 'ms' -or $timing.direction -ne 'lower-is-better' -or -not [bool]$timing.gateEligible) {
            throw 'Self-test failed: timing metric policy is incorrect.'
        }
        if ($actual.independentSeriesCount -ne 1 -or @($timing.batches).Count -ne 1) {
            throw 'Self-test failed: one normalized run must be exactly one independent batch.'
        }
        Write-Host 'Metric normalizer self-test passed.'
    } finally {
        Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($SelfTest) { Invoke-SelfTest; return }
if ($null -eq $BenchmarkJsonPath -or $BenchmarkJsonPath.Count -eq 0 -or
    [string]::IsNullOrWhiteSpace($RunMetadataPath) -or [string]::IsNullOrWhiteSpace($OutputPath)) {
    throw 'BenchmarkJsonPath, RunMetadataPath and OutputPath are required unless -SelfTest is used.'
}
$output = Convert-NewAudioMetrics -JsonPaths $BenchmarkJsonPath -MetadataPath $RunMetadataPath
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
if (-not [string]::IsNullOrWhiteSpace($parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$output | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $resolvedOutput -Encoding utf8
Write-Host "Normalized metric candidate: $resolvedOutput"
