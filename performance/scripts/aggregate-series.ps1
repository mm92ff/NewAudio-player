[CmdletBinding()]
param(
    [string[]]$CandidatePath,
    [string[]]$RepeatCandidatePath = @(),
    [string]$OutputPath,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$compatibilityPaths = @(
    'build.appId', 'build.variant', 'build.compilationMode', 'build.versionName', 'build.versionCode',
    'fixtures.manifestVersion', 'fixtures.manifestSha256', 'fixtures.cacheState',
    'device.manufacturer', 'device.model', 'device.apiLevel', 'device.buildFingerprint',
    'device.abi', 'device.hardware', 'device.role', 'device.roleId', 'device.physical', 'device.emulator', 'device.decoderPolicy',
    'device.screenResolution', 'device.screenDensity', 'device.refreshRate', 'device.fontScale',
    'device.powerSource', 'device.thermalStatus',
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

function Assert-CompatibleCandidateEnvironment {
    param($Reference, $Candidate, [string]$Context)

    foreach ($path in $compatibilityPaths) {
        $expected = Get-RequiredPathValue -Object $Reference.environment -Path $path -Context 'reference candidate environment'
        $actual = Get-RequiredPathValue -Object $Candidate.environment -Path $path -Context "$Context environment"
        if ((ConvertTo-Json $expected -Compress -Depth 4) -ne (ConvertTo-Json $actual -Compress -Depth 4)) {
            throw "Incompatible '$path' in $Context. Expected='$expected', actual='$actual'."
        }
    }
}

function Get-SeriesKey {
    param($Series)
    return '{0}|{1}|{2}|{3}|{4}|{5}' -f $Series.journey, $Series.layoutVariant,
        $Series.cacheState, $Series.decoderPath, $Series.metric, $Series.unit
}

function Get-Median {
    param([double[]]$Values)
    if ($Values.Count -eq 0) { throw 'Median requires at least one value.' }
    $sorted = @($Values | Sort-Object)
    $middle = [math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

function Get-Mad {
    param([double[]]$Values)
    $median = Get-Median -Values $Values
    return Get-Median -Values @($Values | ForEach-Object { [math]::Abs([double]$_ - $median) })
}

function Assert-NormalizedCandidate {
    param($Candidate, [string]$Context)

    foreach ($property in @('schemaVersion', 'runId', 'mode', 'fullComposeTracing', 'baselineEligible',
            'repository', 'environment', 'metricSeries', 'independentSeriesCount')) {
        if ($null -eq $Candidate.PSObject.Properties[$property]) {
            throw "$Context is missing required property '$property'."
        }
    }
    if ($Candidate.mode -ne 'metrics' -or [bool]$Candidate.fullComposeTracing) {
        throw "$Context is not a metric-mode candidate."
    }
    if ([int]$Candidate.schemaVersion -ne 2) {
        throw "$Context has unsupported schemaVersion '$($Candidate.schemaVersion)'; expected 2."
    }
    if (-not [bool]$Candidate.baselineEligible) {
        throw "$Context is not baseline eligible. Dirty and reduced-iteration runs cannot be aggregated."
    }
    foreach ($property in @('commit', 'dirty', 'statusSha256', 'diffSha256', 'untrackedSha256',
            'worktreeStateSha256', 'untrackedPresent')) {
        $null = Get-RequiredPathValue -Object $Candidate.repository -Path $property -Context "$Context repository"
    }
    if ([bool]$Candidate.repository.dirty) { throw "$Context was recorded from a dirty worktree." }
    if ([string]$Candidate.commit -ne [string]$Candidate.repository.commit) {
        throw "$Context commit does not match its repository provenance."
    }
    if ([int]$Candidate.independentSeriesCount -ne 1) {
        throw "$Context must represent exactly one independent run; aggregate files cannot be aggregated again."
    }
    if ([string]::IsNullOrWhiteSpace([string]$Candidate.runId)) {
        throw "$Context has an empty runId."
    }
    $batteryPercent = [double](Get-RequiredPathValue -Object $Candidate.environment `
        -Path 'device.batteryPercent' -Context "$Context environment")
    if ($batteryPercent -lt 0 -or $batteryPercent -gt 100) {
        throw "$Context has an invalid battery percentage '$batteryPercent'."
    }
    if (@($Candidate.metricSeries).Count -eq 0) { throw "$Context contains no metricSeries." }
    foreach ($series in @($Candidate.metricSeries)) {
        foreach ($property in @('journey', 'benchmark', 'metric', 'layoutVariant', 'cacheState',
                'decoderPath', 'unit', 'type', 'direction', 'gateEligible', 'values', 'batches',
                'independentSeriesCount')) {
            if ($null -eq $series.PSObject.Properties[$property]) {
                throw "$Context series '$(Get-SeriesKey $series)' is missing '$property'."
            }
        }
        if (@($series.values).Count -eq 0 -or @($series.batches).Count -ne 1 -or [int]$series.independentSeriesCount -ne 1) {
            throw "$Context series '$(Get-SeriesKey $series)' must contain values from exactly one batch."
        }
        if ([string]$series.batches[0].runId -ne [string]$Candidate.runId) {
            throw "$Context series '$(Get-SeriesKey $series)' batch runId does not match the candidate runId."
        }
    }
}

function Merge-NewAudioCandidates {
    param([object[]]$Candidates, [object[]]$RepeatCandidates = @())

    if ($Candidates.Count -eq 0) { throw 'At least one candidate is required.' }
    $all = @($Candidates) + @($RepeatCandidates)
    $runIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    for ($index = 0; $index -lt $all.Count; $index++) {
        $context = if ($index -lt $Candidates.Count) { "candidate[$index]" } else { "repeat[$($index - $Candidates.Count)]" }
        Assert-NormalizedCandidate -Candidate $all[$index] -Context $context
        if (-not $runIds.Add([string]$all[$index].runId)) {
            throw "RunId '$($all[$index].runId)' is duplicated; independent batches must come from distinct executions."
        }
        if ([string]$all[$index].commit -ne [string]$Candidates[0].commit) {
            throw "$context uses a different source commit. Independent runs in one batch must use identical code."
        }
        Assert-CompatibleCandidateEnvironment -Reference $Candidates[0] -Candidate $all[$index] -Context $context
    }

    $referenceByKey = @{}
    foreach ($series in @($Candidates[0].metricSeries)) {
        $key = Get-SeriesKey $series
        if ($referenceByKey.ContainsKey($key)) { throw "Reference candidate duplicates series '$key'." }
        $referenceByKey[$key] = $series
    }
    foreach ($candidate in $all) {
        $candidateByKey = @{}
        foreach ($series in @($candidate.metricSeries)) { $candidateByKey[(Get-SeriesKey $series)] = $series }
        if ($candidateByKey.Count -ne $referenceByKey.Count) { throw "Run '$($candidate.runId)' has a different metric-series set." }
        foreach ($key in $referenceByKey.Keys) {
            if (-not $candidateByKey.ContainsKey($key)) { throw "Run '$($candidate.runId)' is missing series '$key'." }
            $expected = $referenceByKey[$key]
            $actual = $candidateByKey[$key]
            foreach ($property in @('benchmark', 'layoutVariant', 'cacheState', 'decoderPath',
                    'unit', 'type', 'direction', 'gateEligible')) {
                if (([string]$expected.$property) -ne ([string]$actual.$property)) {
                    throw "Run '$($candidate.runId)' has incompatible $property for '$key'."
                }
            }
        }
    }

    $mergedSeries = [Collections.Generic.List[object]]::new()
    foreach ($key in @($referenceByKey.Keys | Sort-Object)) {
        $reference = $referenceByKey[$key]
        $batches = @($Candidates | ForEach-Object {
            $candidate = $_
            $series = @($candidate.metricSeries | Where-Object { (Get-SeriesKey $_) -eq $key })[0]
            $values = @($series.values | ForEach-Object { [double]$_ })
            [ordered]@{ runId = [string]$candidate.runId; values = $values; median = Get-Median -Values $values }
        })
        $repeatBatches = @($RepeatCandidates | ForEach-Object {
            $candidate = $_
            $series = @($candidate.metricSeries | Where-Object { (Get-SeriesKey $_) -eq $key })[0]
            $values = @($series.values | ForEach-Object { [double]$_ })
            [ordered]@{ runId = [string]$candidate.runId; values = $values; median = Get-Median -Values $values }
        })
        $seriesMedians = [double[]]@($batches | ForEach-Object { [double]$_.median })
        $item = [ordered]@{
            journey = [string]$reference.journey
            benchmark = [string]$reference.benchmark
            metric = [string]$reference.metric
            layoutVariant = [string]$reference.layoutVariant
            cacheState = [string]$reference.cacheState
            decoderPath = [string]$reference.decoderPath
            unit = [string]$reference.unit
            type = [string]$reference.type
            direction = [string]$reference.direction
            gateEligible = [bool]$reference.gateEligible
            values = @($batches | ForEach-Object { @($_.values) })
            batches = $batches
            independentSeriesCount = $batches.Count
            seriesMedians = $seriesMedians
            median = Get-Median -Values $seriesMedians
            mad = Get-Mad -Values $seriesMedians
        }
        if ($repeatBatches.Count -gt 0) {
            $repeatSeriesMedians = [double[]]@($repeatBatches | ForEach-Object { [double]$_.median })
            $item['repeatValues'] = @($repeatBatches | ForEach-Object { @($_.values) })
            $item['repeatBatches'] = $repeatBatches
            $item['repeatIndependentSeriesCount'] = $repeatBatches.Count
            $item['repeatSeriesMedians'] = $repeatSeriesMedians
            $item['repeatMedian'] = Get-Median -Values $repeatSeriesMedians
            $item['repeatMad'] = Get-Mad -Values $repeatSeriesMedians
        }
        $mergedSeries.Add($item)
    }

    $environment = $Candidates[0].environment | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    $batteryValues = [double[]]@($all | ForEach-Object { [double]$_.environment.device.batteryPercent })
    $environment.device | Add-Member batteryPercentRange ([pscustomobject]@{
        min = [double]($batteryValues | Measure-Object -Minimum).Minimum
        max = [double]($batteryValues | Measure-Object -Maximum).Maximum
    }) -Force

    return [ordered]@{
        schemaVersion = 2
        mode = 'metrics'
        fullComposeTracing = $false
        baselineEligible = $true
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        environment = $environment
        runBatches = @($Candidates | ForEach-Object {
            [ordered]@{ runId = [string]$_.runId; commit = [string]$_.commit; repository = $_.repository }
        })
        independentSeriesCount = $Candidates.Count
        repeatRunBatches = @($RepeatCandidates | ForEach-Object {
            [ordered]@{ runId = [string]$_.runId; commit = [string]$_.commit; repository = $_.repository }
        })
        repeatIndependentSeriesCount = $RepeatCandidates.Count
        metricSeries = $mergedSeries.ToArray()
    }
}

function New-SelfTestCandidate {
    param([string]$RunId, [double[]]$Values)
    $environment = [pscustomobject]@{
        build = [pscustomobject]@{ appId = 'com.example.newaudio'; variant = 'benchmark'; compilationMode = 'Partial(warmupIterations=3)'; versionName = '1'; versionCode = 1 }
        fixtures = [pscustomobject]@{ manifestVersion = 1; manifestSha256 = 'fixture'; cacheState = 'seeded-app-private' }
        device = [pscustomobject]@{
            manufacturer = 'Google'; model = 'Pixel'; apiLevel = '35'; buildFingerprint = 'fingerprint'; abi = 'arm64-v8a'; hardware = 'tensor'
            role = 'physical-candidate-device'; roleId = 'lab-pixel'; physical = $true; emulator = $false; decoderPolicy = 'device-default-unforced'
            screenResolution = '1080x2400'; screenDensity = '420'; refreshRate = '60'; fontScale = '1.0'
            powerSource = 'usb'; thermalStatus = '0'; batteryPercent = 90
            animations = [pscustomobject]@{ window = '0'; transition = '0'; animator = '0' }
        }
    }
    $series = [pscustomobject]@{
        journey = 'NV-01'; benchmark = 'nv01'; metric = 'frameDurationCpuMs.P95'
        layoutVariant = 'default'; cacheState = 'COLD_EMPTY_IMAGE_CACHE'; decoderPath = 'NOT_APPLICABLE'
        unit = 'ms'; type = 'latency'
        direction = 'lower-is-better'; gateEligible = $true; values = $Values
        batches = @([pscustomobject]@{ runId = $RunId; values = $Values }); independentSeriesCount = 1
    }
    return [pscustomobject]@{
        schemaVersion = 2; runId = $RunId; commit = ('a' * 40); mode = 'metrics'; fullComposeTracing = $false
        baselineEligible = $true; repository = [pscustomobject]@{
            commit = ('a' * 40); dirty = $false; statusSha256 = 'status'; diffSha256 = 'diff'
            untrackedSha256 = 'untracked'; worktreeStateSha256 = 'state'; untrackedPresent = $false
        }; environment = $environment
        metricSeries = @($series); independentSeriesCount = 1
    }
}

function Invoke-SelfTest {
    $primary = @(1..3 | ForEach-Object { New-SelfTestCandidate -RunId "run-$_" -Values @((9 + $_), (10 + $_)) })
    $repeat = @(1..3 | ForEach-Object { New-SelfTestCandidate -RunId "repeat-$_" -Values @((13 + $_), (14 + $_)) })
    $actual = Merge-NewAudioCandidates -Candidates $primary -RepeatCandidates $repeat
    $series = @($actual.metricSeries)[0]
    if ($actual.independentSeriesCount -ne 3 -or @($series.batches).Count -ne 3 -or @($series.values).Count -ne 6) {
        throw 'Aggregator self-test failed: primary batches were not preserved independently.'
    }
    if (@($series.seriesMedians).Count -ne 3 -or $series.median -ne 11.5 -or $series.mad -ne 1) {
        throw 'Aggregator self-test failed: series medians/median/MAD are incorrect.'
    }
    if ($actual.environment.device.batteryPercentRange.min -ne 90 -or
        $actual.environment.device.batteryPercentRange.max -ne 90) {
        throw 'Aggregator self-test failed: battery range was not aggregated.'
    }
    if ($actual.repeatIndependentSeriesCount -ne 3 -or @($series.repeatBatches).Count -ne 3 -or @($series.repeatValues).Count -ne 6) {
        throw 'Aggregator self-test failed: repeat batches were not preserved independently.'
    }
    $duplicateRejected = $false
    try { $null = Merge-NewAudioCandidates -Candidates @($primary[0], $primary[0]) } catch { $duplicateRejected = $true }
    if (-not $duplicateRejected) { throw 'Aggregator self-test failed: duplicate runId was accepted.' }
    Write-Host 'Metric series aggregator self-test passed.'
}

if ($SelfTest) { Invoke-SelfTest; return }
if ($null -eq $CandidatePath -or $CandidatePath.Count -eq 0 -or [string]::IsNullOrWhiteSpace($OutputPath)) {
    throw 'CandidatePath and OutputPath are required unless -SelfTest is used.'
}
$candidates = @($CandidatePath | ForEach-Object {
    $resolved = [IO.Path]::GetFullPath($_)
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) { throw "Candidate not found: $resolved" }
    Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
})
$repeats = @($RepeatCandidatePath | ForEach-Object {
    $resolved = [IO.Path]::GetFullPath($_)
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) { throw "Repeat candidate not found: $resolved" }
    Get-Content -LiteralPath $resolved -Raw | ConvertFrom-Json
})
$output = Merge-NewAudioCandidates -Candidates $candidates -RepeatCandidates $repeats
$resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
if (-not [string]::IsNullOrWhiteSpace($parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
$output | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $resolvedOutput -Encoding utf8
Write-Host "Aggregated metric series: $resolvedOutput"
