[CmdletBinding()]
param(
    [string]$BaselinePath,
    [string]$CandidatePath,
    [string]$OutputPath,
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$compatibilityMappings = @(
    @('app id', 'build.appId', 'build.appId'),
    @('variant', 'build.variant', 'build.variant'),
    @('compilation mode', 'build.compilationMode', 'build.compilationMode'),
    @('fixture manifest version', 'fixtures.manifestVersion', 'fixtures.manifestVersion'),
    @('fixture manifest', 'fixtures.manifestSha256', 'fixtures.manifestSha256'),
    @('cache state', 'fixtures.cacheState', 'fixtures.cacheState'),
    @('manufacturer', 'device.manufacturer', 'device.manufacturer'),
    @('model', 'device.model', 'device.model'),
    @('API level', 'device.apiLevel', 'device.apiLevel'),
    @('device fingerprint', 'device.buildFingerprint', 'device.buildFingerprint'),
    @('ABI', 'device.abi', 'device.abi'),
    @('hardware', 'device.hardware', 'device.hardware'),
    @('device role ID', 'device.roleId', 'device.roleId'),
    @('emulator flag', 'device.emulator', 'device.emulator'),
    @('decoder policy', 'device.decoderPolicy', 'device.decoderPolicy'),
    @('screen resolution', 'device.screenResolution', 'device.screenResolution'),
    @('screen density', 'device.screenDensity', 'device.screenDensity'),
    @('refresh rate', 'device.refreshRate', 'device.refreshRate'),
    @('font scale', 'device.fontScale', 'device.fontScale'),
    @('power source', 'device.powerSource', 'device.powerSource'),
    @('thermal status', 'device.thermalStatus', 'device.thermalStatus'),
    @('window animation scale', 'device.animations.window', 'device.animations.window'),
    @('transition animation scale', 'device.animations.transition', 'device.animations.transition'),
    @('animator duration scale', 'device.animations.animator', 'device.animations.animator')
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
    return Get-Median -Values @($Values | ForEach-Object { [math]::Abs($_ - $median) })
}

function Get-SeriesKey {
    param($Series)
    return '{0}|{1}|{2}|{3}|{4}|{5}' -f $Series.journey, $Series.layoutVariant,
        $Series.cacheState, $Series.decoderPath, $Series.metric, $Series.unit
}

function Get-BatchMedians {
    param($Series, [string]$Property = 'batches')

    return [double[]]@(@($Series.$Property) | ForEach-Object {
        Get-Median -Values @($_.values | ForEach-Object { [double]$_ })
    })
}

function Assert-SeriesBatchCount {
    param($Series, [int]$MinimumCount, [string]$Context, [switch]$Repeat)

    $batchProperty = if ($Repeat) { 'repeatBatches' } else { 'batches' }
    $countProperty = if ($Repeat) { 'repeatIndependentSeriesCount' } else { 'independentSeriesCount' }
    if ($null -eq $Series.PSObject.Properties[$batchProperty] -or $null -eq $Series.PSObject.Properties[$countProperty]) {
        throw "$Context is missing independent batch metadata '$batchProperty/$countProperty'."
    }
    $batches = @($Series.$batchProperty)
    $declared = [int]$Series.$countProperty
    if ($declared -ne $batches.Count) {
        throw "$Context declares $declared independent series but contains $($batches.Count) batches."
    }
    $ids = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($batch in $batches) {
        $runId = [string](Get-RequiredPathValue -Object $batch -Path 'runId' -Context $Context)
        if (-not $ids.Add($runId)) { throw "$Context contains duplicate batch runId '$runId'." }
        if ($null -eq $batch.PSObject.Properties['values'] -or @($batch.values).Count -eq 0) {
            throw "$Context batch '$runId' has no values."
        }
    }
    if ($declared -lt $MinimumCount) {
        throw "$Context requires at least $MinimumCount independent run batches; found $declared. Iteration values do not count as independent series."
    }
}

function Assert-SeriesRunIdSet {
    param($Series, [string[]]$ExpectedRunIds, [string]$Context, [switch]$Repeat)

    $batchProperty = if ($Repeat) { 'repeatBatches' } else { 'batches' }
    $countProperty = if ($Repeat) { 'repeatIndependentSeriesCount' } else { 'independentSeriesCount' }
    if ($null -eq $Series.PSObject.Properties[$batchProperty] -or
        $null -eq $Series.PSObject.Properties[$countProperty]) {
        throw "$Context is missing '$batchProperty/$countProperty'."
    }
    $actual = @($Series.$batchProperty | ForEach-Object { [string]$_.runId } | Sort-Object)
    $expected = @($ExpectedRunIds | Sort-Object)
    if ([int]$Series.$countProperty -ne $expected.Count -or
        ($actual -join "`n") -ne ($expected -join "`n")) {
        throw "$Context batch runIds do not match the aggregate top-level run set."
    }
}

function Assert-CompatibleEnvironment {
    param($Baseline, $Candidate)

    foreach ($property in @('mode', 'fullComposeTracing', 'baselineEligible', 'environment', 'runBatches',
            'independentSeriesCount', 'repeatRunBatches', 'repeatIndependentSeriesCount', 'metricSeries')) {
        if ($null -eq $Candidate.PSObject.Properties[$property]) { throw "Candidate is missing required property '$property'." }
    }
    if ($Candidate.mode -ne 'metrics' -or [bool]$Candidate.fullComposeTracing) {
        throw 'Only metric-mode candidates without Full Compose Tracing may be compared.'
    }
    if (-not [bool]$Candidate.baselineEligible) {
        throw 'Candidate is not baseline eligible.'
    }
    $topBatches = @($Candidate.runBatches)
    if ($topBatches.Count -eq 0) { throw 'Candidate contains no independent run batches.' }
    if ([int]$Candidate.independentSeriesCount -ne $topBatches.Count) {
        throw 'Candidate top-level independentSeriesCount does not match runBatches.'
    }
    $topRunIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $candidateCommit = $null
    foreach ($batch in $topBatches) {
        $null = Get-RequiredPathValue -Object $batch -Path 'runId' -Context 'candidate run batch'
        if (-not $topRunIds.Add([string]$batch.runId)) { throw "Candidate duplicates runId '$($batch.runId)'." }
        $batchCommit = Get-RequiredPathValue -Object $batch -Path 'commit' -Context 'candidate run batch'
        if ($null -eq $candidateCommit) { $candidateCommit = [string]$batchCommit }
        elseif ($candidateCommit -ne [string]$batchCommit) { throw 'Candidate run batches use different source commits.' }
        $dirty = Get-RequiredPathValue -Object $batch -Path 'repository.dirty' -Context 'candidate run batch'
        $repositoryCommit = Get-RequiredPathValue -Object $batch -Path 'repository.commit' -Context 'candidate run batch'
        if ([string]$repositoryCommit -ne [string]$batchCommit) { throw "Candidate run '$($batch.runId)' has inconsistent commit provenance." }
        $null = Get-RequiredPathValue -Object $batch -Path 'repository.statusSha256' -Context 'candidate run batch'
        $null = Get-RequiredPathValue -Object $batch -Path 'repository.diffSha256' -Context 'candidate run batch'
        $null = Get-RequiredPathValue -Object $batch -Path 'repository.untrackedSha256' -Context 'candidate run batch'
        $null = Get-RequiredPathValue -Object $batch -Path 'repository.worktreeStateSha256' -Context 'candidate run batch'
        $null = Get-RequiredPathValue -Object $batch -Path 'repository.untrackedPresent' -Context 'candidate run batch'
        if ([bool]$dirty) { throw "Candidate run '$($batch.runId)' was recorded from a dirty worktree." }
    }
    $repeatTopBatches = @($Candidate.repeatRunBatches)
    if ([int]$Candidate.repeatIndependentSeriesCount -ne $repeatTopBatches.Count) {
        throw 'Candidate top-level repeatIndependentSeriesCount does not match repeatRunBatches.'
    }
    $repeatRunIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($batch in $repeatTopBatches) {
        $runId = [string](Get-RequiredPathValue -Object $batch -Path 'runId' -Context 'candidate repeat run batch')
        if (-not $repeatRunIds.Add($runId) -or $topRunIds.Contains($runId)) {
            throw "Candidate repeat runId '$runId' is duplicated across primary/repeat batches."
        }
        $batchCommit = Get-RequiredPathValue -Object $batch -Path 'commit' -Context 'candidate repeat run batch'
        if ([string]$batchCommit -ne [string]$candidateCommit) { throw 'Candidate repeat batches use a different source commit.' }
        $dirty = Get-RequiredPathValue -Object $batch -Path 'repository.dirty' -Context 'candidate repeat run batch'
        $repositoryCommit = Get-RequiredPathValue -Object $batch -Path 'repository.commit' -Context 'candidate repeat run batch'
        if ([string]$repositoryCommit -ne [string]$batchCommit -or [bool]$dirty) {
            throw "Candidate repeat run '$runId' has invalid clean commit provenance."
        }
        foreach ($property in @('statusSha256', 'diffSha256', 'untrackedSha256', 'worktreeStateSha256', 'untrackedPresent')) {
            $null = Get-RequiredPathValue -Object $batch -Path "repository.$property" -Context 'candidate repeat run batch'
        }
    }

    $baselineDirty = Get-RequiredPathValue -Object $Baseline -Path 'repository.dirty' -Context 'baseline'
    $null = Get-RequiredPathValue -Object $Baseline -Path 'repository.commit' -Context 'baseline'
    $null = Get-RequiredPathValue -Object $Baseline -Path 'repository.statusSha256' -Context 'baseline'
    $null = Get-RequiredPathValue -Object $Baseline -Path 'repository.diffSha256' -Context 'baseline'
    $null = Get-RequiredPathValue -Object $Baseline -Path 'repository.untrackedSha256' -Context 'baseline'
    $null = Get-RequiredPathValue -Object $Baseline -Path 'repository.worktreeStateSha256' -Context 'baseline'
    $null = Get-RequiredPathValue -Object $Baseline -Path 'repository.untrackedPresent' -Context 'baseline'
    if ([bool]$baselineDirty) { throw 'A calibrated baseline must come from a clean worktree.' }
    $role = Get-RequiredPathValue -Object $Baseline -Path 'device.role' -Context 'baseline'
    if ([string]$role -ne 'physical-reference-device') {
        throw "Baseline device role must be 'physical-reference-device'; found '$role'."
    }
    if (-not [bool](Get-RequiredPathValue -Object $Baseline -Path 'device.physical' -Context 'baseline') -or
        -not [bool](Get-RequiredPathValue -Object $Candidate.environment -Path 'device.physical' -Context 'candidate environment')) {
        throw 'Calibrated performance comparison requires physical devices.'
    }
    $candidateRole = Get-RequiredPathValue -Object $Candidate.environment -Path 'device.role' -Context 'candidate environment'
    if ([string]$candidateRole -ne 'physical-candidate-device') {
        throw "Candidate device role must be 'physical-candidate-device'; found '$candidateRole'."
    }
    $baselineRoleId = [string](Get-RequiredPathValue -Object $Baseline -Path 'device.roleId' -Context 'baseline')
    if ($baselineRoleId -notmatch '^[a-z0-9][a-z0-9._-]{2,63}$') {
        throw 'Baseline device.roleId is not a stable lowercase role alias.'
    }
    foreach ($mapping in $compatibilityMappings) {
        $name, $baselinePath, $candidatePath = $mapping
        $expected = Get-RequiredPathValue -Object $Baseline -Path $baselinePath -Context 'baseline'
        $actual = Get-RequiredPathValue -Object $Candidate.environment -Path $candidatePath -Context 'candidate environment'
        if ((ConvertTo-Json $expected -Compress -Depth 4) -ne (ConvertTo-Json $actual -Compress -Depth 4)) {
            throw "Incompatible $name. Baseline='$expected', candidate='$actual'."
        }
    }
    if ([bool]$Baseline.device.emulator -or [bool]$Candidate.environment.device.emulator) {
        throw 'Calibrated performance comparison requires a physical baseline and physical candidate.'
    }
    if ([string]$Baseline.device.thermalStatus -eq 'unavailable' -or
        [string]$Candidate.environment.device.thermalStatus -eq 'unavailable') {
        throw 'Calibrated physical comparison requires an available thermal status.'
    }
    $minimumBattery = [double](Get-RequiredPathValue -Object $Baseline -Path 'device.batteryPercentRange.min' -Context 'baseline')
    $maximumBattery = [double](Get-RequiredPathValue -Object $Baseline -Path 'device.batteryPercentRange.max' -Context 'baseline')
    $candidateMinimumBattery = [double](Get-RequiredPathValue -Object $Candidate.environment -Path 'device.batteryPercentRange.min' -Context 'candidate environment')
    $candidateMaximumBattery = [double](Get-RequiredPathValue -Object $Candidate.environment -Path 'device.batteryPercentRange.max' -Context 'candidate environment')
    if ($minimumBattery -lt 0 -or $maximumBattery -gt 100 -or $minimumBattery -gt $maximumBattery -or
        $candidateMinimumBattery -lt 0 -or $candidateMaximumBattery -gt 100 -or
        $candidateMinimumBattery -gt $candidateMaximumBattery -or
        $candidateMinimumBattery -lt $minimumBattery -or $candidateMaximumBattery -gt $maximumBattery) {
        throw "Candidate battery range $candidateMinimumBattery..$candidateMaximumBattery% is outside the calibrated range $minimumBattery..$maximumBattery%."
    }
}

function Compare-NewAudioBaseline {
    param($Baseline, $Candidate)

    if ($null -eq $Baseline.PSObject.Properties['schemaVersion'] -or [int]$Baseline.schemaVersion -ne 2) {
        throw "Baseline has an unsupported or missing schemaVersion; expected 2."
    }
    if ($null -eq $Baseline.PSObject.Properties['status'] -or $null -eq $Baseline.PSObject.Properties['thresholdPolicy']) {
        throw 'Baseline is missing status or thresholdPolicy.'
    }
    if ($Baseline.status -ne 'calibrated' -or $Baseline.thresholdPolicy.state -ne 'calibrated') {
        return [ordered]@{ status = 'disabled-until-calibrated'; comparisons = @(); hardFailure = $false }
    }
    if ($null -eq $Candidate.PSObject.Properties['schemaVersion'] -or [int]$Candidate.schemaVersion -ne 2) {
        throw "Candidate has an unsupported or missing schemaVersion; expected 2."
    }
    Assert-CompatibleEnvironment -Baseline $Baseline -Candidate $Candidate
    if (@($Baseline.metricSeries).Count -eq 0) { throw 'Calibrated baseline contains no metricSeries.' }
    if (@($Candidate.metricSeries).Count -eq 0) { throw 'Candidate contains no metricSeries.' }

    foreach ($property in @('minimumSeriesCount', 'relativeWarningPercent', 'absoluteMinimumDeltaMs',
            'madMultiplier', 'requiredRepeatOnRegression', 'hardGateEnabled')) {
        $null = Get-RequiredPathValue -Object $Baseline.thresholdPolicy -Path $property -Context 'baseline thresholdPolicy'
    }
    $minimumCount = [int]$Baseline.thresholdPolicy.minimumSeriesCount
    if ($minimumCount -lt 3) { throw 'A calibrated baseline must require at least three independent series.' }
    $relativePercent = [double]$Baseline.thresholdPolicy.relativeWarningPercent
    $absoluteDelta = [double]$Baseline.thresholdPolicy.absoluteMinimumDeltaMs
    $madMultiplier = [double]$Baseline.thresholdPolicy.madMultiplier
    $requiredRepeat = [bool]$Baseline.thresholdPolicy.requiredRepeatOnRegression
    $hardGate = [bool]$Baseline.thresholdPolicy.hardGateEnabled

    $candidateByKey = @{}
    foreach ($series in @($Candidate.metricSeries)) {
        $key = Get-SeriesKey $series
        if ($candidateByKey.ContainsKey($key)) { throw "Candidate duplicates metric series '$key'." }
        $candidateByKey[$key] = $series
    }
    $primaryRunIds = [string[]]@($Candidate.runBatches | ForEach-Object { [string]$_.runId })
    $repeatRunIds = [string[]]@($Candidate.repeatRunBatches | ForEach-Object { [string]$_.runId })
    foreach ($series in @($Candidate.metricSeries)) {
        $key = Get-SeriesKey $series
        Assert-SeriesRunIdSet -Series $series -ExpectedRunIds $primaryRunIds -Context "candidate series '$key'"
        if ($repeatRunIds.Count -gt 0) {
            Assert-SeriesRunIdSet -Series $series -ExpectedRunIds $repeatRunIds -Context "candidate repeat series '$key'" -Repeat
        } elseif ($null -ne $series.PSObject.Properties['repeatBatches'] -or
            $null -ne $series.PSObject.Properties['repeatValues']) {
            throw "Candidate series '$key' contains repeat data without top-level repeat batches."
        }
    }

    $comparisons = [Collections.Generic.List[object]]::new()
    $hardFailure = $false
    $baselineKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($baselineSeries in @($Baseline.metricSeries)) {
        $key = Get-SeriesKey $baselineSeries
        if (-not $baselineKeys.Add($key)) { throw "Baseline duplicates metric series '$key'." }
        if (-not $candidateByKey.ContainsKey($key)) { throw "Candidate is missing metric series '$key'." }
        $candidateSeries = $candidateByKey[$key]
        foreach ($property in @('layoutVariant', 'cacheState', 'decoderPath', 'unit', 'type',
                'direction', 'gateEligible', 'values')) {
            if ($null -eq $baselineSeries.PSObject.Properties[$property] -or $null -eq $candidateSeries.PSObject.Properties[$property]) {
                throw "Metric series '$key' is missing required policy property '$property'."
            }
            if ($property -ne 'values' -and ([string]$baselineSeries.$property) -ne ([string]$candidateSeries.$property)) {
                throw "Metric series '$key' has incompatible '$property'."
            }
        }
        Assert-SeriesBatchCount -Series $baselineSeries -MinimumCount $minimumCount -Context "baseline series '$key'"
        Assert-SeriesBatchCount -Series $candidateSeries -MinimumCount $minimumCount -Context "candidate series '$key'"

        if (-not [bool]$baselineSeries.gateEligible) {
            if ($baselineSeries.direction -ne 'informational') { throw "Non-gated series '$key' must be informational." }
            $comparisons.Add([ordered]@{ journey = $baselineSeries.journey; metric = $baselineSeries.metric; status = 'informational' })
            continue
        }
        if ($baselineSeries.unit -ne 'ms' -or $baselineSeries.type -ne 'latency' -or $baselineSeries.direction -ne 'lower-is-better') {
            throw "Gated series '$key' must be a lower-is-better latency in milliseconds."
        }

        $baselineValues = Get-BatchMedians -Series $baselineSeries
        $candidateValues = Get-BatchMedians -Series $candidateSeries
        if ($baselineValues.Count -eq 0 -or $candidateValues.Count -eq 0) { throw "Metric series '$key' has no independent batch medians." }
        $baselineMedian = Get-Median $baselineValues
        $baselineMad = Get-Mad $baselineValues
        $candidateMedian = Get-Median $candidateValues
        $delta = $candidateMedian - $baselineMedian
        $threshold = [math]::Max($absoluteDelta,
            [math]::Max($baselineMedian * $relativePercent / 100.0, $baselineMad * $madMultiplier))
        $regressed = $delta -gt $threshold
        $repeatConfirmed = $false
        if ($regressed -and $null -ne $candidateSeries.PSObject.Properties['repeatValues']) {
            Assert-SeriesBatchCount -Series $candidateSeries -MinimumCount $minimumCount -Context "candidate repeat series '$key'" -Repeat
            $repeatValues = Get-BatchMedians -Series $candidateSeries -Property 'repeatBatches'
            $repeatConfirmed = (Get-Median $repeatValues) - $baselineMedian -gt $threshold
        }
        $status = if (-not $regressed) { 'pass' }
            elseif ($requiredRepeat -and -not $repeatConfirmed) { 'repeat-required' }
            elseif ($hardGate) { $hardFailure = $true; 'hard-failure' }
            else { 'warning' }
        $comparisons.Add([ordered]@{
            journey = $baselineSeries.journey; metric = $baselineSeries.metric; unit = $baselineSeries.unit
            baselineMedian = [math]::Round($baselineMedian, 4); baselineMad = [math]::Round($baselineMad, 4)
            candidateMedian = [math]::Round($candidateMedian, 4); delta = [math]::Round($delta, 4)
            calibratedThreshold = [math]::Round($threshold, 4); repeatConfirmed = $repeatConfirmed; status = $status
        })
    }
    if ($candidateByKey.Count -ne $baselineKeys.Count) {
        throw 'Baseline and candidate contain different metric-series sets.'
    }
    $attention = @($comparisons | Where-Object { $_.status -notin @('pass', 'informational') }).Count -gt 0
    return [ordered]@{
        status = if ($hardFailure) { 'hard-failure' } elseif ($attention) { 'attention' } else { 'pass' }
        comparisons = $comparisons.ToArray()
        hardFailure = $hardFailure
    }
}

function New-TestBatches {
    param([string]$Prefix, [double[][]]$Values)
    return @(for ($i = 0; $i -lt $Values.Count; $i++) { [pscustomobject]@{ runId = "$Prefix-$i"; values = $Values[$i] } })
}

function Invoke-SelfTest {
    $device = [pscustomobject]@{
        role = 'physical-reference-device'; manufacturer = 'Google'; model = 'Pixel'; apiLevel = '35'; buildFingerprint = 'device'
        physical = $true; abi = 'arm64-v8a'; hardware = 'tensor'; emulator = $false; decoderPolicy = 'device-default-unforced'
        roleId = 'lab-pixel'; screenResolution = '1080x2400'; screenDensity = '420'; refreshRate = '60'; fontScale = '1.0'
        powerSource = 'usb'; thermalStatus = '0'; batteryPercentRange = [pscustomobject]@{ min = 80; max = 100 }
        animations = [pscustomobject]@{ window = '0'; transition = '0'; animator = '0' }
    }
    $baselineBatches = New-TestBatches 'baseline' @(@(9, 10), @(10, 11), @(9, 11))
    $baseline = [pscustomobject]@{
        schemaVersion = 2; status = 'calibrated'; repository = [pscustomobject]@{
            commit = ('a' * 40); dirty = $false; statusSha256 = 'status'; diffSha256 = 'diff'
            untrackedSha256 = 'untracked'; worktreeStateSha256 = 'state'; untrackedPresent = $false
        }
        build = [pscustomobject]@{ appId = 'com.example.newaudio'; variant = 'benchmark'; compilationMode = 'Partial(warmupIterations=3)' }
        fixtures = [pscustomobject]@{ manifestVersion = 1; manifestSha256 = 'fixture'; cacheState = 'seeded-app-private' }
        device = $device
        thresholdPolicy = [pscustomobject]@{
            state = 'calibrated'; minimumSeriesCount = 3; requiredRepeatOnRegression = $true
            relativeWarningPercent = 10; absoluteMinimumDeltaMs = 2; madMultiplier = 3; hardGateEnabled = $false
        }
        metricSeries = @(
            [pscustomobject]@{ journey = 'NV-01'; metric = 'frameDurationCpuMs.P95'; layoutVariant = 'default'; cacheState = 'COLD_EMPTY_IMAGE_CACHE'; decoderPath = 'NOT_APPLICABLE'; unit = 'ms'; type = 'latency'; direction = 'lower-is-better'; gateEligible = $true; values = @(9,10,10,11,9,11); batches = $baselineBatches; independentSeriesCount = 3 },
            [pscustomobject]@{ journey = 'NV-01'; metric = 'frameCount'; layoutVariant = 'default'; cacheState = 'COLD_EMPTY_IMAGE_CACHE'; decoderPath = 'NOT_APPLICABLE'; unit = 'count'; type = 'count'; direction = 'informational'; gateEligible = $false; values = @(8,9,8); batches = (New-TestBatches 'baseline-count' @(@(8),@(9),@(8))); independentSeriesCount = 3 }
        )
    }
    function Candidate([double[][]]$Values, [double[][]]$Repeat = @()) {
        $batches = New-TestBatches 'candidate' $Values
        $runBatches = @($batches | ForEach-Object { [pscustomobject]@{ runId = $_.runId; commit = ('b' * 40); repository = [pscustomobject]@{ commit = ('b' * 40); dirty = $false; statusSha256 = 'status'; diffSha256 = 'diff'; untrackedSha256 = 'untracked'; worktreeStateSha256 = 'state'; untrackedPresent = $false } } })
        $timing = [pscustomobject]@{ journey = 'NV-01'; metric = 'frameDurationCpuMs.P95'; layoutVariant = 'default'; cacheState = 'COLD_EMPTY_IMAGE_CACHE'; decoderPath = 'NOT_APPLICABLE'; unit = 'ms'; type = 'latency'; direction = 'lower-is-better'; gateEligible = $true; values = @($Values | ForEach-Object { @($_) }); batches = $batches; independentSeriesCount = $batches.Count }
        $countBatches = @(for ($i = 0; $i -lt $batches.Count; $i++) {
            [pscustomobject]@{ runId = $batches[$i].runId; values = @([double](100 * ($i + 1))) }
        })
        $countSeries = [pscustomobject]@{
            journey = 'NV-01'; metric = 'frameCount'; layoutVariant = 'default'; cacheState = 'COLD_EMPTY_IMAGE_CACHE'
            decoderPath = 'NOT_APPLICABLE'; unit = 'count'; type = 'count'; direction = 'informational'; gateEligible = $false
            values = @($countBatches | ForEach-Object { @($_.values) }); batches = $countBatches; independentSeriesCount = $countBatches.Count
        }
        $repeatBatches = @()
        $repeatRunBatches = @()
        if ($Repeat.Count -gt 0) {
            $repeatBatches = New-TestBatches 'repeat' $Repeat
            $repeatRunBatches = @($repeatBatches | ForEach-Object { [pscustomobject]@{ runId = $_.runId; commit = ('b' * 40); repository = [pscustomobject]@{ commit = ('b' * 40); dirty = $false; statusSha256 = 'status'; diffSha256 = 'diff'; untrackedSha256 = 'untracked'; worktreeStateSha256 = 'state'; untrackedPresent = $false } } })
            $timing | Add-Member repeatValues @($Repeat | ForEach-Object { @($_) })
            $timing | Add-Member repeatBatches $repeatBatches
            $timing | Add-Member repeatIndependentSeriesCount $repeatBatches.Count
            $countRepeatBatches = @(for ($i = 0; $i -lt $repeatBatches.Count; $i++) {
                [pscustomobject]@{ runId = $repeatBatches[$i].runId; values = @([double](100 * ($i + 1))) }
            })
            $countSeries | Add-Member repeatValues @($countRepeatBatches | ForEach-Object { @($_.values) })
            $countSeries | Add-Member repeatBatches $countRepeatBatches
            $countSeries | Add-Member repeatIndependentSeriesCount $countRepeatBatches.Count
        }
        return [pscustomobject]@{
            schemaVersion = 2; mode = 'metrics'; fullComposeTracing = $false; baselineEligible = $true; independentSeriesCount = $batches.Count; runBatches = $runBatches
            repeatIndependentSeriesCount = $repeatRunBatches.Count; repeatRunBatches = $repeatRunBatches
            environment = [pscustomobject]@{
                build = [pscustomobject]@{ appId = 'com.example.newaudio'; variant = 'benchmark'; compilationMode = 'Partial(warmupIterations=3)' }
                fixtures = [pscustomobject]@{ manifestVersion = 1; manifestSha256 = 'fixture'; cacheState = 'seeded-app-private' }
                device = [pscustomobject]@{
                    role = 'physical-candidate-device'; physical = $true; manufacturer = $device.manufacturer; model = $device.model
                    apiLevel = $device.apiLevel; buildFingerprint = $device.buildFingerprint; abi = $device.abi; hardware = $device.hardware
                    emulator = $device.emulator; decoderPolicy = $device.decoderPolicy; roleId = $device.roleId; screenResolution = $device.screenResolution
                    screenDensity = $device.screenDensity; refreshRate = $device.refreshRate; fontScale = $device.fontScale; animations = $device.animations
                    powerSource = $device.powerSource; thermalStatus = $device.thermalStatus
                    batteryPercentRange = [pscustomobject]@{ min = 88; max = 92 }
                }
            }
            metricSeries = @($timing, $countSeries)
        }
    }
    if ((Compare-NewAudioBaseline $baseline (Candidate @(@(9,10),@(10,11),@(9,11)))).status -ne 'pass') { throw 'Self-test failed: stable series did not pass.' }
    if ((Compare-NewAudioBaseline $baseline (Candidate @(@(10),@(10),@(100)))).status -ne 'pass') { throw 'Self-test failed: one outlier defeated median policy.' }
    $regression = Compare-NewAudioBaseline $baseline (Candidate @(@(14,15),@(15,16),@(14,16)))
    if ($regression.comparisons[0].status -ne 'repeat-required') { throw 'Self-test failed: regression did not request a repeat.' }
    $batchWeighted = Compare-NewAudioBaseline $baseline (Candidate @(@(10,10,10,10,10,10),@(20),@(20)))
    if ($batchWeighted.comparisons[0].status -ne 'repeat-required') {
        throw 'Self-test failed: comparison pooled iterations instead of weighting independent batch medians equally.'
    }
    $confirmed = Compare-NewAudioBaseline $baseline (Candidate @(@(14,15),@(15,16),@(14,16)) @(@(14,15),@(15,16),@(14,16)))
    if ($confirmed.comparisons[0].status -ne 'warning') { throw 'Self-test failed: confirmed regression did not warn.' }
    $insufficientRejected = $false
    $insufficient = Candidate @(@(9),@(10),@(11))
    $insufficient.metricSeries[0].values = @(9, 10, 11)
    $insufficient.metricSeries[0].batches = @([pscustomobject]@{ runId = 'only-one-run'; values = @(9, 10, 11) })
    $insufficient.metricSeries[0].independentSeriesCount = 1
    try { $null = Compare-NewAudioBaseline $baseline $insufficient } catch { $insufficientRejected = $true }
    if (-not $insufficientRejected) { throw 'Self-test failed: iterations were counted as independent series.' }
    $emptyRejected = $false
    $emptyBaseline = $baseline | ConvertTo-Json -Depth 12 | ConvertFrom-Json
    $emptyBaseline.metricSeries = @()
    try { $null = Compare-NewAudioBaseline $emptyBaseline (Candidate @(@(9),@(10),@(11))) } catch { $emptyRejected = $true }
    if (-not $emptyRejected) { throw 'Self-test failed: an empty calibrated baseline was accepted.' }
    $foreignBatchRejected = $false
    $foreignBatch = Candidate @(@(9),@(10),@(11))
    $foreignBatch.metricSeries[1].batches[0].runId = 'foreign-run'
    try { $null = Compare-NewAudioBaseline $baseline $foreignBatch } catch { $foreignBatchRejected = $true }
    if (-not $foreignBatchRejected) { throw 'Self-test failed: a metric series was not tied to the top-level run set.' }
    $batteryRangeRejected = $false
    $outsideBatteryRange = Candidate @(@(9),@(10),@(11))
    $outsideBatteryRange.environment.device.batteryPercentRange.max = 101
    try { $null = Compare-NewAudioBaseline $baseline $outsideBatteryRange } catch { $batteryRangeRejected = $true }
    if (-not $batteryRangeRejected) { throw 'Self-test failed: an invalid candidate battery range was accepted.' }
    $hardBaseline = $baseline | ConvertTo-Json -Depth 12 | ConvertFrom-Json
    $hardBaseline.thresholdPolicy.hardGateEnabled = $true
    if (-not (Compare-NewAudioBaseline $hardBaseline (Candidate @(@(14,15),@(15,16),@(14,16)) @(@(14,15),@(15,16),@(14,16)))).hardFailure) {
        throw 'Self-test failed: confirmed hard-gate regression did not fail.'
    }
    Write-Host 'Baseline comparison self-test passed.'
}

if ($SelfTest) { Invoke-SelfTest; return }
if ([string]::IsNullOrWhiteSpace($BaselinePath) -or [string]::IsNullOrWhiteSpace($CandidatePath)) {
    throw 'BaselinePath and CandidatePath are required unless -SelfTest is used.'
}
$baseline = Get-Content -LiteralPath ([IO.Path]::GetFullPath($BaselinePath)) -Raw | ConvertFrom-Json
$candidate = Get-Content -LiteralPath ([IO.Path]::GetFullPath($CandidatePath)) -Raw | ConvertFrom-Json
$result = Compare-NewAudioBaseline -Baseline $baseline -Candidate $candidate
$json = $result | ConvertTo-Json -Depth 12
if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $resolvedOutput = [IO.Path]::GetFullPath($OutputPath)
    $parent = Split-Path -Parent $resolvedOutput
    if (-not [string]::IsNullOrWhiteSpace($parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
    $json | Set-Content -LiteralPath $resolvedOutput -Encoding utf8
}
$json
if ($result.hardFailure) { exit 2 }
