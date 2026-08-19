[CmdletBinding()]
param(
    [string[]]$CandidatePath,
    [string]$RunId,
    [string]$OutputPath,
    [ValidateRange(2, 16)][int]$ExpectedShardCount = 4,
    [ValidateRange(2, 64)][int]$ExpectedJourneyCount = 13,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$compatibilityPaths = @(
    'build.appId', 'build.variant', 'build.compilationMode', 'build.versionName', 'build.versionCode',
    'fixtures.manifestVersion', 'fixtures.manifestSha256', 'fixtures.cacheState',
    'device.manufacturer', 'device.model', 'device.apiLevel', 'device.buildFingerprint',
    'device.abi', 'device.hardware', 'device.role', 'device.roleId', 'device.physical',
    'device.emulator', 'device.decoderPolicy', 'device.screenResolution', 'device.screenDensity',
    'device.refreshRate', 'device.fontScale', 'device.powerSource', 'device.thermalStatus',
    'device.animations.window', 'device.animations.transition', 'device.animations.animator'
)

function Get-RequiredPathValue {
    param($Object, [string]$Path, [string]$Context)

    $current = $Object
    foreach ($segment in $Path.Split('.')) {
        if ($null -eq $current -or $null -eq $current.PSObject.Properties[$segment]) {
            throw "$Context is missing required property '$Path'."
        }
        $current = $current.$segment
    }
    if ($null -eq $current -or ($current -is [string] -and [string]::IsNullOrWhiteSpace($current))) {
        throw "$Context has an empty required property '$Path'."
    }
    return $current
}

function Get-SeriesKey {
    param($Series)

    return '{0}|{1}|{2}|{3}|{4}|{5}' -f $Series.journey, $Series.layoutVariant,
        $Series.cacheState, $Series.decoderPath, $Series.metric, $Series.unit
}

function Assert-CompatibleEnvironment {
    param($Reference, $Candidate, [string]$Context)

    foreach ($path in $compatibilityPaths) {
        $expected = Get-RequiredPathValue -Object $Reference.environment -Path $path `
            -Context 'reference shard environment'
        $actual = Get-RequiredPathValue -Object $Candidate.environment -Path $path `
            -Context "$Context environment"
        $expectedJson = ConvertTo-Json $expected -Compress -Depth 4
        $actualJson = ConvertTo-Json $actual -Compress -Depth 4
        if ($expectedJson -ne $actualJson) {
            throw "Incompatible '$path' in $Context. Expected='$expected', actual='$actual'."
        }
    }
}

function Assert-ShardCandidate {
    param($Candidate, [string]$Context)

    foreach ($property in @('schemaVersion', 'runId', 'commit', 'mode', 'fullComposeTracing',
            'baselineEligible', 'repository', 'environment', 'sourceFiles', 'metricSeries',
            'independentSeriesCount')) {
        if ($null -eq $Candidate.PSObject.Properties[$property]) {
            throw "$Context is missing required property '$property'."
        }
    }
    if ([int]$Candidate.schemaVersion -ne 2 -or $Candidate.mode -ne 'metrics' -or
        [bool]$Candidate.fullComposeTracing) {
        throw "$Context is not a supported normalized metric candidate."
    }
    if (-not [bool]$Candidate.baselineEligible -or [bool]$Candidate.repository.dirty) {
        throw "$Context is not baseline eligible."
    }
    if ([int]$Candidate.independentSeriesCount -ne 1) {
        throw "$Context must represent exactly one independent shard run."
    }
    if ([string]::IsNullOrWhiteSpace([string]$Candidate.runId) -or
        @($Candidate.metricSeries).Count -eq 0) {
        throw "$Context has no runId or metric series."
    }
    foreach ($series in @($Candidate.metricSeries)) {
        foreach ($property in @('journey', 'benchmark', 'metric', 'layoutVariant', 'cacheState',
                'decoderPath', 'unit', 'type', 'direction', 'gateEligible', 'values', 'batches',
                'independentSeriesCount')) {
            if ($null -eq $series.PSObject.Properties[$property]) {
                throw "$Context series '$(Get-SeriesKey $series)' is missing '$property'."
            }
        }
        if (@($series.values).Count -eq 0 -or @($series.batches).Count -ne 1 -or
            [int]$series.independentSeriesCount -ne 1) {
            throw "$Context series '$(Get-SeriesKey $series)' is not one complete shard batch."
        }
    }
}

function Merge-NewAudioMetricShards {
    param(
        [object[]]$Candidates,
        [string]$LogicalRunId,
        [int]$RequiredShardCount,
        [int]$RequiredJourneyCount
    )

    if ($Candidates.Count -ne $RequiredShardCount) {
        throw "Expected $RequiredShardCount metric shard candidates; found $($Candidates.Count)."
    }
    if ([string]::IsNullOrWhiteSpace($LogicalRunId)) { throw 'Logical RunId is required.' }

    $sourceRunIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $seriesKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $journeyIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $mergedSeries = [Collections.Generic.List[object]]::new()
    $sourceFiles = [Collections.Generic.List[object]]::new()

    for ($index = 0; $index -lt $Candidates.Count; $index++) {
        $candidate = $Candidates[$index]
        $context = "metric shard candidate[$index]"
        Assert-ShardCandidate -Candidate $candidate -Context $context
        if (-not $sourceRunIds.Add([string]$candidate.runId)) {
            throw "Shard runId '$($candidate.runId)' is duplicated."
        }
        if ([string]$candidate.commit -ne [string]$Candidates[0].commit) {
            throw "$context uses a different source commit."
        }
        Assert-CompatibleEnvironment -Reference $Candidates[0] -Candidate $candidate -Context $context

        foreach ($file in @($candidate.sourceFiles)) { $sourceFiles.Add($file) }
        foreach ($series in @($candidate.metricSeries)) {
            $key = Get-SeriesKey $series
            if (-not $seriesKeys.Add($key)) {
                throw "Metric shard candidates overlap at series '$key'."
            }
            $null = $journeyIds.Add([string]$series.journey)
            $copy = $series | ConvertTo-Json -Depth 12 | ConvertFrom-Json
            $values = @($copy.values | ForEach-Object { [double]$_ })
            $copy.batches = @([pscustomobject]@{ runId = $LogicalRunId; values = $values })
            $copy.independentSeriesCount = 1
            $mergedSeries.Add($copy)
        }
    }
    if ($journeyIds.Count -ne $RequiredJourneyCount) {
        throw "Expected $RequiredJourneyCount distinct metric journeys; found $($journeyIds.Count)."
    }

    $repository = $Candidates[0].repository | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    $environment = $Candidates[0].environment | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    $batteryValues = [double[]]@($Candidates | ForEach-Object {
        [double](Get-RequiredPathValue -Object $_.environment -Path 'device.batteryPercent' `
            -Context 'metric shard environment')
    })
    $environment.device | Add-Member shardBatteryPercentRange ([pscustomobject]@{
        min = [double]($batteryValues | Measure-Object -Minimum).Minimum
        max = [double]($batteryValues | Measure-Object -Maximum).Maximum
    }) -Force

    return [ordered]@{
        schemaVersion = 2
        mode = 'metrics'
        fullComposeTracing = $false
        baselineEligible = $true
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        commit = [string]$Candidates[0].commit
        repository = $repository
        runId = $LogicalRunId
        runBatches = @([ordered]@{
            runId = $LogicalRunId
            commit = [string]$Candidates[0].commit
            repository = $repository
        })
        independentSeriesCount = 1
        environment = $environment
        sourceFiles = $sourceFiles.ToArray()
        sourceShardRunIds = @($sourceRunIds | Sort-Object)
        metricSeries = $mergedSeries.ToArray()
    }
}

function New-SelfTestEnvironment {
    return [pscustomobject]@{
        build = [pscustomobject]@{
            appId = 'com.example.newaudio'; variant = 'benchmark'; compilationMode = 'Partial(warmupIterations=3)'
            versionName = '1'; versionCode = 1
        }
        fixtures = [pscustomobject]@{
            manifestVersion = 2; manifestSha256 = 'fixture'; cacheState = 'MIXED_PER_JOURNEY'
        }
        device = [pscustomobject]@{
            manufacturer = 'Google'; model = 'sdk_gphone64_x86_64'; apiLevel = '35'
            buildFingerprint = 'fingerprint'; abi = 'x86_64'; hardware = 'ranchu'
            role = 'emulator-smoke'; roleId = 'api35-default-series'; physical = $false; emulator = $true
            decoderPolicy = 'software-avc'; screenResolution = '1080x2400'; screenDensity = '420'
            refreshRate = '60'; fontScale = '1.0'; powerSource = 'ac'; thermalStatus = '0'
            batteryPercent = 90
            animations = [pscustomobject]@{ window = '0'; transition = '0'; animator = '0' }
        }
    }
}

function New-SelfTestCandidate {
    param([string]$ShardRunId, [string]$Journey)

    $repository = [pscustomobject]@{
        commit = ('a' * 40); dirty = $false; statusSha256 = 'status'; diffSha256 = 'diff'
        untrackedSha256 = 'untracked'; worktreeStateSha256 = 'state'; untrackedPresent = $false
    }
    $series = [pscustomobject]@{
        journey = $Journey; benchmark = "benchmark-$Journey"; metric = 'frameDurationCpuMs.P95'
        layoutVariant = 'default'; cacheState = 'COLD_EMPTY_IMAGE_CACHE'; decoderPath = 'NOT_APPLICABLE'
        unit = 'ms'; type = 'latency'; direction = 'lower-is-better'; gateEligible = $true
        values = @(10, 11); batches = @([pscustomobject]@{ runId = $ShardRunId; values = @(10, 11) })
        independentSeriesCount = 1
    }
    return [pscustomobject]@{
        schemaVersion = 2; mode = 'metrics'; fullComposeTracing = $false; baselineEligible = $true
        generatedAtUtc = '2026-01-01T00:00:00Z'; commit = ('a' * 40); repository = $repository
        runId = $ShardRunId; runBatches = @(); independentSeriesCount = 1
        environment = (New-SelfTestEnvironment)
        sourceFiles = @([pscustomobject]@{ fileName = "$ShardRunId.json" })
        metricSeries = @($series)
    }
}

function Invoke-SelfTest {
    $candidates = @(
        (New-SelfTestCandidate -ShardRunId 'shard-a' -Journey 'BR-01'),
        (New-SelfTestCandidate -ShardRunId 'shard-b' -Journey 'BR-02')
    )
    $actual = Merge-NewAudioMetricShards -Candidates $candidates `
        -LogicalRunId 'logical-browser-series-1' -RequiredShardCount 2 -RequiredJourneyCount 2
    if ($actual.runId -ne 'logical-browser-series-1' -or
        $actual.independentSeriesCount -ne 1 -or @($actual.metricSeries).Count -ne 2 -or
        @($actual.sourceShardRunIds).Count -ne 2) {
        throw 'Metric shard merger self-test failed: logical candidate shape is incorrect.'
    }
    foreach ($series in @($actual.metricSeries)) {
        if (@($series.batches).Count -ne 1 -or
            $series.batches[0].runId -ne 'logical-browser-series-1') {
            throw 'Metric shard merger self-test failed: batches were not assigned to the logical run.'
        }
    }
    $overlapRejected = $false
    try {
        $null = Merge-NewAudioMetricShards -Candidates @($candidates[0], $candidates[0]) `
            -LogicalRunId 'invalid' -RequiredShardCount 2 -RequiredJourneyCount 2
    } catch { $overlapRejected = $true }
    if (-not $overlapRejected) {
        throw 'Metric shard merger self-test failed: overlapping shards were accepted.'
    }
    Write-Host 'Metric shard merger self-test passed.'
}

if ($SelfTest) { Invoke-SelfTest; return }
if ($null -eq $CandidatePath -or $CandidatePath.Count -eq 0 -or
    [string]::IsNullOrWhiteSpace($RunId) -or [string]::IsNullOrWhiteSpace($OutputPath)) {
    throw 'CandidatePath, RunId and OutputPath are required unless -SelfTest is used.'
}
$candidates = @($CandidatePath | ForEach-Object {
    $resolved = [IO.Path]::GetFullPath($_)
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        throw "Metric shard candidate not found: $resolved"
    }
    Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
})
$output = Merge-NewAudioMetricShards -Candidates $candidates -LogicalRunId $RunId `
    -RequiredShardCount $ExpectedShardCount -RequiredJourneyCount $ExpectedJourneyCount
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
if (-not [string]::IsNullOrWhiteSpace($parent)) {
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
}
$output | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $resolvedOutput -Encoding utf8
Write-Host "Merged metric shard candidate: $resolvedOutput"
