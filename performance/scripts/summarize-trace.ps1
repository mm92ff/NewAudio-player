[CmdletBinding()]
param(
    [string]$TracePath,
    [string]$OutputDirectory,
    [string]$TraceProcessorPath,
    [string]$Journey = 'unspecified',
    [string]$Mode = 'diagnostic-full-compose-tracing',
    [string]$CompilationMode = 'unspecified',
    [string]$RunMetadataPath,
    [switch]$AllowMissingComposeSlices,
    [switch]$AllowMissingFrames,
    [switch]$AllowMissingAppThreads,
    [switch]$SelfTest,
    [switch]$DryRun,
    [switch]$CheckPrerequisites
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $PSCommandPath
$performanceRoot = [IO.Path]::GetFullPath((Join-Path $scriptRoot '..'))
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $performanceRoot '..'))
$resultsRoot = [IO.Path]::GetFullPath((Join-Path $performanceRoot 'results'))
$queryRoot = Join-Path $performanceRoot 'trace-queries'
$queryNames = @(
    'discover_compose_slices',
    'compose_hotspots',
    'frame_summary',
    'long_frames',
    'frame_slice_correlation',
    'main_thread_summary'
)

function Resolve-SafeOutputDirectory {
    param([string]$Candidate)

    $resolved = if ([string]::IsNullOrWhiteSpace($Candidate)) {
        Join-Path $resultsRoot ('report-{0}' -f [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmssfff'))
    } elseif ([IO.Path]::IsPathRooted($Candidate)) {
        [IO.Path]::GetFullPath($Candidate)
    } else {
        [IO.Path]::GetFullPath((Join-Path $repositoryRoot $Candidate))
    }
    $resolved = [IO.Path]::GetFullPath($resolved)
    $prefix = $resultsRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if ($resolved -eq $resultsRoot -or
        -not $resolved.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe output path '$resolved'. Choose a child directory below '$resultsRoot'."
    }
    return $resolved
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
    throw 'Trace Processor was not found. Set -TraceProcessorPath or add it to PATH.'
}

function Assert-Prerequisites {
    foreach ($name in $queryNames) {
        $query = Join-Path $queryRoot ($name + '.sql')
        if (-not (Test-Path -LiteralPath $query -PathType Leaf)) {
            throw "Required SQL query not found at '$query'."
        }
    }
    $processor = Resolve-TraceProcessor
    if (-not [string]::IsNullOrWhiteSpace($TracePath)) {
        $resolvedTrace = [IO.Path]::GetFullPath($TracePath)
        if (-not (Test-Path -LiteralPath $resolvedTrace -PathType Leaf)) {
            throw "Trace file not found at '$resolvedTrace'."
        }
        if ((Get-Item -LiteralPath $resolvedTrace).Length -eq 0) {
            throw "Trace file '$resolvedTrace' is empty."
        }
    }
    return $processor
}

function Invoke-TraceQuery {
    param(
        [string]$Processor,
        [string]$Query,
        [string]$Trace,
        [string]$CsvPath
    )

    $stderrPath = Join-Path ([IO.Path]::GetTempPath()) ('newaudio-trace-processor-{0}.log' -f [Guid]::NewGuid().ToString('N'))
    try {
        # The classic -q interface intentionally remains supported by Perfetto.
        # Non-interactive query output is RFC-style CSV on stdout; diagnostic
        # trace-loading messages stay on stderr.
        $firstLine = Get-Content -LiteralPath $Processor -TotalCount 1 -ErrorAction SilentlyContinue
        $isPythonWrapper = [Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
            [Runtime.InteropServices.OSPlatform]::Windows
        ) -and $firstLine -match '^#!.*python'
        if ($isPythonWrapper) {
            $python = Get-Command python -ErrorAction SilentlyContinue
            if ($null -eq $python) {
                throw "Perfetto processor '$Processor' is a Python wrapper, but Python was not found."
            }
            & $python.Source $Processor -q $Query $Trace 2> $stderrPath |
                Set-Content -LiteralPath $CsvPath -Encoding utf8
        } else {
            & $Processor -q $Query $Trace 2> $stderrPath |
                Set-Content -LiteralPath $CsvPath -Encoding utf8
        }
        $exitCode = $LASTEXITCODE
        $stderr = if (Test-Path -LiteralPath $stderrPath) {
            (Get-Content -LiteralPath $stderrPath -Raw -ErrorAction SilentlyContinue).Trim()
        } else { '' }
        if ($exitCode -ne 0) {
            throw "Trace Processor failed for '$Query' with exit code $exitCode. $stderr"
        }
        if (-not (Test-Path -LiteralPath $CsvPath -PathType Leaf) -or
            (Get-Item -LiteralPath $CsvPath).Length -eq 0) {
            throw "Trace Processor produced no CSV output for '$Query'. $stderr"
        }
        if (-not [string]::IsNullOrWhiteSpace($stderr) -and
            $stderr -match '(?im)\b(warn|warning|error|fatal|failed)\b') {
            Write-Warning "Trace Processor reported: $stderr"
        }
    } finally {
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
}

function ConvertTo-MarkdownTable {
    param([object[]]$Rows)

    if ($Rows.Count -eq 0) { return '_No rows._' }
    $columns = @($Rows[0].PSObject.Properties.Name)
    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('| ' + ($columns -join ' | ') + ' |')
    $lines.Add('| ' + (($columns | ForEach-Object { '---' }) -join ' | ') + ' |')
    foreach ($row in @($Rows | Select-Object -First 20)) {
        $values = foreach ($column in $columns) {
            $value = [string]$row.$column
            $value.Replace('|', '\|').Replace("`r", ' ').Replace("`n", ' ')
        }
        $lines.Add('| ' + ($values -join ' | ') + ' |')
    }
    return ($lines -join [Environment]::NewLine)
}

function ConvertTo-StableJourneyId {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    $candidate = $Value -replace '^NewAudio:', ''
    if ($candidate -match '(?i)^([A-Z]{2})[-_]?([0-9]{2})(.*)$') {
        $id = '{0}-{1}' -f $Matches[1].ToUpperInvariant(), $Matches[2]
        $suffix = ([string]$Matches[3]).Trim('-', '_') -replace '_+', '-'
        if (-not [string]::IsNullOrWhiteSpace($suffix)) { $id += '-' + $suffix.ToUpperInvariant() }
        return $id
    }
    return $Value
}

function Resolve-TraceJourneyId {
    param([object[]]$Discovery, [string]$Fallback)

    $ids = @($Discovery |
        Where-Object { $_.slice_kind -eq 'measurement_window' -and $_.slice_name -like 'NewAudio:*' } |
        ForEach-Object { ConvertTo-StableJourneyId -Value ([string]$_.slice_name) } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Unique)
    if ($ids.Count -gt 1) { throw "Trace contains multiple NewAudio journey IDs: $($ids -join ', ')." }
    if ($ids.Count -eq 1) { return $ids[0] }
    return ConvertTo-StableJourneyId -Value $Fallback
}

function ConvertTo-Int64OrZero {
    param(
        [AllowNull()][object]$Value,
        [string]$FieldName
    )

    if ($null -eq $Value) { return [int64]0 }
    $text = ([string]$Value).Trim()
    if ([string]::IsNullOrWhiteSpace($text) -or $text -in @('[NULL]', 'NULL')) {
        return [int64]0
    }
    $parsed = [int64]0
    if (-not [int64]::TryParse(
            $text,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$parsed)) {
        throw "Trace query field '$FieldName' is not a valid Int64: '$text'."
    }
    return $parsed
}

function Test-RequiresActiveRendering {
    param([string]$JourneyId)

    return $JourneyId -notin @('AU-05', 'VI-02')
}

function Resolve-TraceReportIdentity {
    param($RunMetadata, [string]$DefaultMode, [string]$DefaultCompilationMode, [string]$TraceFileName)

    if ($null -eq $RunMetadata) {
        $commit = $null
        if (Get-Command git -ErrorAction SilentlyContinue) { $commit = (& git -C $repositoryRoot rev-parse HEAD 2>$null) }
        return [ordered]@{
            commit = $commit; mode = $DefaultMode; compilationMode = $DefaultCompilationMode
            baselineEligible = $false; runId = $null; testClass = $null; repository = $null; environment = $null
            journeyId = $null; iteration = $null; capturedAtUtc = $null; cacheState = $null; decoderPath = $null
        }
    }
    if ($null -eq $RunMetadata.PSObject.Properties['schemaVersion'] -or [int]$RunMetadata.schemaVersion -ne 3) {
        throw "Trace run metadata schemaVersion must be 3."
    }
    foreach ($property in @('status', 'runId', 'commit', 'mode', 'fullComposeTracing', 'baselineEligible', 'testClass', 'repository', 'environment')) {
        if ($null -eq $RunMetadata.PSObject.Properties[$property]) {
            throw "Run metadata is missing required authoritative property '$property'."
        }
        if ($RunMetadata.$property -is [string] -and [string]::IsNullOrWhiteSpace([string]$RunMetadata.$property)) {
            throw "Run metadata has an empty authoritative property '$property'."
        }
    }
    if ($RunMetadata.status -ne 'succeeded') {
        throw "Trace report requires a succeeded run manifest; found '$($RunMetadata.status)'."
    }
    if ($RunMetadata.mode -ne 'diagnostic-full-compose-tracing' -or -not [bool]$RunMetadata.fullComposeTracing) {
        throw 'Trace report metadata must come from a diagnostic Full Compose Tracing run.'
    }
    if ([bool]$RunMetadata.baselineEligible) {
        throw 'Diagnostic Full Compose Tracing metadata can never be baseline eligible.'
    }
    foreach ($property in @('commit', 'dirty', 'statusSha256', 'diffSha256', 'untrackedSha256',
            'worktreeStateSha256', 'untrackedPresent')) {
        if ($null -eq $RunMetadata.repository.PSObject.Properties[$property] -or
            ($RunMetadata.repository.$property -is [string] -and
                [string]::IsNullOrWhiteSpace([string]$RunMetadata.repository.$property))) {
            throw "Run metadata repository provenance is missing '$property'."
        }
    }
    if ([string]$RunMetadata.commit -ne [string]$RunMetadata.repository.commit) {
        throw 'Run metadata commit does not match repository.commit.'
    }
    foreach ($path in @(
            [pscustomobject]@{ group = 'build'; property = 'appId' },
            [pscustomobject]@{ group = 'build'; property = 'variant' },
            [pscustomobject]@{ group = 'build'; property = 'compilationMode' },
            [pscustomobject]@{ group = 'fixtures'; property = 'manifestVersion' },
            [pscustomobject]@{ group = 'fixtures'; property = 'manifestSha256' },
            [pscustomobject]@{ group = 'fixtures'; property = 'cacheState' },
            [pscustomobject]@{ group = 'device'; property = 'role' },
            [pscustomobject]@{ group = 'device'; property = 'roleId' },
            [pscustomobject]@{ group = 'device'; property = 'apiLevel' },
            [pscustomobject]@{ group = 'device'; property = 'buildFingerprint' },
            [pscustomobject]@{ group = 'device'; property = 'screenResolution' },
            [pscustomobject]@{ group = 'device'; property = 'screenDensity' },
            [pscustomobject]@{ group = 'device'; property = 'refreshRate' },
            [pscustomobject]@{ group = 'device'; property = 'fontScale' })) {
        $group = $path.group
        $property = $path.property
        if ($null -eq $RunMetadata.environment.PSObject.Properties[$group] -or
            $null -eq $RunMetadata.environment.$group.PSObject.Properties[$property] -or
            [string]::IsNullOrWhiteSpace([string]$RunMetadata.environment.$group.$property)) {
            throw "Run metadata is missing authoritative environment.$group.$property."
        }
    }
    if ($null -eq $RunMetadata.PSObject.Properties['traces']) {
        throw 'Run metadata is missing authoritative per-trace identities.'
    }
    $traceIdentity = @($RunMetadata.traces | Where-Object { $_.fileName -eq $TraceFileName })
    if ($traceIdentity.Count -ne 1) {
        throw "Run metadata must contain exactly one identity for trace '$TraceFileName'."
    }
    foreach ($property in @('fileName', 'iteration', 'capturedAtUtc', 'journeyId', 'cacheState', 'decoderPath')) {
        if ($null -eq $traceIdentity[0].PSObject.Properties[$property]) {
            throw "Trace identity '$TraceFileName' is missing '$property'."
        }
        if ($traceIdentity[0].$property -is [string] -and
            [string]::IsNullOrWhiteSpace([string]$traceIdentity[0].$property)) {
            throw "Trace identity '$TraceFileName' has an empty '$property'."
        }
    }
    if ([int]$traceIdentity[0].iteration -lt 0) { throw "Trace identity '$TraceFileName' has a negative iteration." }
    try {
        $capturedAt = [DateTimeOffset]$traceIdentity[0].capturedAtUtc
    } catch {
        throw "Trace identity '$TraceFileName' has an invalid capturedAtUtc."
    }
    return [ordered]@{
        commit = [string]$RunMetadata.commit
        mode = [string]$RunMetadata.mode
        compilationMode = [string]$RunMetadata.environment.build.compilationMode
        baselineEligible = $false
        runId = [string]$RunMetadata.runId
        testClass = [string]$RunMetadata.testClass
        repository = $RunMetadata.repository
        environment = $RunMetadata.environment
        journeyId = [string]$traceIdentity[0].journeyId
        iteration = [int]$traceIdentity[0].iteration
        capturedAtUtc = $capturedAt.ToUniversalTime().ToString('o')
        cacheState = [string]$traceIdentity[0].cacheState
        decoderPath = [string]$traceIdentity[0].decoderPath
    }
}

function Invoke-SelfTest {
    $discovery = @([pscustomobject]@{ slice_kind = 'measurement_window'; slice_name = 'NewAudio:BR04_GRID_3_COLD' })
    if ((Resolve-TraceJourneyId -Discovery $discovery -Fallback 'ignored-file-name') -ne 'BR-04-GRID-3-COLD') {
        throw 'Trace report self-test failed: stable journey normalization is incorrect.'
    }
    $markerDiscovery = @([pscustomobject]@{ slice_kind = 'measurement_window'; slice_name = 'NewAudio:VI06_MARKERS_ON' })
    if ((Resolve-TraceJourneyId -Discovery $markerDiscovery -Fallback 'ignored') -ne 'VI-06-MARKERS-ON') {
        throw 'Trace report self-test failed: named journey suffix normalization is incorrect.'
    }
    if ((ConvertTo-Int64OrZero -Value '[NULL]' -FieldName 'self-test') -ne 0 -or
        (ConvertTo-Int64OrZero -Value '42' -FieldName 'self-test') -ne 42) {
        throw 'Trace report self-test failed: nullable integer conversion is incorrect.'
    }
    $invalidIntegerRejected = $false
    try { ConvertTo-Int64OrZero -Value 'not-an-integer' -FieldName 'self-test' | Out-Null }
    catch { $invalidIntegerRejected = $true }
    if (-not $invalidIntegerRejected) {
        throw 'Trace report self-test failed: invalid integer input was accepted.'
    }
    if ((Test-RequiresActiveRendering -JourneyId 'AU-05') -or
        (Test-RequiresActiveRendering -JourneyId 'VI-02') -or
        -not (Test-RequiresActiveRendering -JourneyId 'AU-03')) {
        throw 'Trace report self-test failed: static idle rendering requirements are incorrect.'
    }
    $run = [pscustomobject]@{
        schemaVersion = 3
        status = 'succeeded'; runId = 'trace-selftest'; commit = ('c' * 40); mode = 'diagnostic-full-compose-tracing'
        fullComposeTracing = $true; baselineEligible = $false; testClass = 'TraceCaptureTest#traceVideoGalleryThreeColumns'
        repository = [pscustomobject]@{
            commit = ('c' * 40); dirty = $false; statusSha256 = 'status'; diffSha256 = 'diff'
            untrackedSha256 = 'untracked'; worktreeStateSha256 = 'state'; untrackedPresent = $false
        }
        environment = [pscustomobject]@{
            build = [pscustomobject]@{ appId = 'com.example.newaudio'; variant = 'benchmark'; compilationMode = 'authoritative-mode' }
            fixtures = [pscustomobject]@{ manifestVersion = 2; manifestSha256 = 'fixture'; cacheState = 'COLD_EMPTY_IMAGE_CACHE' }
            device = [pscustomobject]@{
                role = 'emulator-smoke'; roleId = 'emulator-smoke'; apiLevel = '35'; buildFingerprint = 'fingerprint'
                screenResolution = '1080x2400'; screenDensity = '420'; refreshRate = '60'; fontScale = '1.0'
            }
        }
        traces = @([pscustomobject]@{
            fileName = 'selftest.perfetto-trace'; iteration = 2
            capturedAtUtc = '2026-01-02T03:04:05Z'; journeyId = 'BR-04-GRID-3-COLD'
            cacheState = 'COLD_EMPTY_IMAGE_CACHE'; decoderPath = 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER'
        })
    }
    $identity = Resolve-TraceReportIdentity -RunMetadata $run -DefaultMode 'wrong-mode' `
        -DefaultCompilationMode 'wrong-compilation' -TraceFileName 'selftest.perfetto-trace'
    if ($identity.commit -ne ('c' * 40) -or $identity.mode -ne 'diagnostic-full-compose-tracing' -or
        $identity.compilationMode -ne 'authoritative-mode' -or $identity.runId -ne 'trace-selftest' -or
        $identity.journeyId -ne 'BR-04-GRID-3-COLD' -or
        $identity.decoderPath -ne 'MIXED_ARTWORK_URI_VIDEO_FRAME_DECODER') {
        throw 'Trace report self-test failed: run metadata was not authoritative.'
    }
    $jsonRoundTrip = $run | ConvertTo-Json -Depth 10 | ConvertFrom-Json
    $roundTripIdentity = Resolve-TraceReportIdentity -RunMetadata $jsonRoundTrip `
        -DefaultMode 'wrong-mode' -DefaultCompilationMode 'wrong-compilation' `
        -TraceFileName 'selftest.perfetto-trace'
    if ($roundTripIdentity.capturedAtUtc -ne '2026-01-02T03:04:05.0000000+00:00') {
        throw 'Trace report self-test failed: JSON DateTime was not normalized to UTC ISO-8601.'
    }
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($null -eq $python) { $python = Get-Command python3 -ErrorAction SilentlyContinue }
    if ($null -eq $python) { throw 'Trace SQL semantic self-test requires Python 3.' }
    & $python.Source (Join-Path $scriptRoot 'trace-sql-selftest.py') $queryRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Trace SQL semantic self-test failed with exit code $LASTEXITCODE."
    }
    Write-Host 'Trace report self-test passed.'
}

if ($SelfTest) { Invoke-SelfTest; return }

$safeOutput = Resolve-SafeOutputDirectory -Candidate $OutputDirectory
$processor = Assert-Prerequisites

if ($CheckPrerequisites -and [string]::IsNullOrWhiteSpace($TracePath)) {
    Write-Host "Trace summary prerequisites are satisfied. Processor: $processor"
    return
}
if ([string]::IsNullOrWhiteSpace($TracePath)) {
    throw 'TracePath is required unless -CheckPrerequisites is used.'
}
$resolvedTrace = [IO.Path]::GetFullPath($TracePath)
if (-not (Test-Path -LiteralPath $resolvedTrace -PathType Leaf)) {
    throw "Trace file not found at '$resolvedTrace'."
}
if ((Get-Item -LiteralPath $resolvedTrace).Length -eq 0) {
    throw "Trace file '$resolvedTrace' is empty."
}

if ($DryRun) {
    Write-Host 'Dry run only; no report is written.'
    Write-Host "Trace: $resolvedTrace"
    Write-Host "Trace Processor: $processor"
    foreach ($name in $queryNames) {
        Write-Host ("Query: {0}" -f (Join-Path $queryRoot ($name + '.sql')))
    }
    Write-Host "Report directory: $safeOutput"
    return
}

New-Item -ItemType Directory -Path $safeOutput -Force | Out-Null
$results = @{}
foreach ($name in $queryNames) {
    $query = Join-Path $queryRoot ($name + '.sql')
    $csv = Join-Path $safeOutput ($name + '.csv')
    Invoke-TraceQuery -Processor $processor -Query $query -Trace $resolvedTrace -CsvPath $csv
    $rows = @(Import-Csv -LiteralPath $csv)
    $results[$name] = $rows
    ConvertTo-Json -InputObject @($rows) -Depth 8 |
        Set-Content -LiteralPath (Join-Path $safeOutput ($name + '.json')) -Encoding utf8
}

$discovery = @($results['discover_compose_slices'])
$composeCount = @($discovery | Where-Object { $_.slice_kind -eq 'compose' }).Count
$windowCount = @($discovery | Where-Object { $_.slice_kind -eq 'measurement_window' }).Count
$benchmarkWindowCount = @($discovery | Where-Object { $_.slice_kind -eq 'benchmark_measure_block' }).Count
$windowSource = if ($windowCount -gt 0) {
    'newaudio'
} elseif ($benchmarkWindowCount -gt 0) {
    'benchmark_measure_block'
} else {
    'whole_trace_fallback'
}
$frameRows = @($results['frame_summary'])
$frameCount = if ($frameRows.Count -gt 0) {
    ConvertTo-Int64OrZero -Value $frameRows[0].frame_count -FieldName 'frame_count'
} else { [int64]0 }
$expectedFrameMissingCount = if ($frameRows.Count -gt 0) {
    ConvertTo-Int64OrZero -Value $frameRows[0].expected_frame_missing_count `
        -FieldName 'expected_frame_missing_count'
} else { [int64]0 }
$threadCount = @($results['main_thread_summary']).Count
$resolvedJourney = Resolve-TraceJourneyId -Discovery $discovery -Fallback $Journey
$activeRenderingRequired = Test-RequiresActiveRendering -JourneyId $resolvedJourney
$composeSlicesRequired = $activeRenderingRequired
$framesRequired = $activeRenderingRequired

$failures = [Collections.Generic.List[string]]::new()
if ($windowCount -eq 0) {
    $failures.Add('No explicit NewAudio:* measurement window was found.')
}
if ($composeCount -eq 0 -and $composeSlicesRequired -and -not $AllowMissingComposeSlices) {
    $failures.Add('No Compose slices were found for com.example.newaudio.')
}
if ($frameCount -eq 0 -and $framesRequired -and -not $AllowMissingFrames) {
    $failures.Add('No app FrameTimeline rows were found.')
}
if ($expectedFrameMissingCount -gt 0) {
    $failures.Add("$expectedFrameMissingCount app frames have no matching expected FrameTimeline row; overrun statistics are incomplete.")
}
if ($threadCount -eq 0 -and -not $AllowMissingAppThreads) {
    $failures.Add('No NewAudio MainThread or RenderThread scheduling rows were found.')
}

$traceItem = Get-Item -LiteralPath $resolvedTrace
$traceHash = (Get-FileHash -LiteralPath $resolvedTrace -Algorithm SHA256).Hash.ToLowerInvariant()
$runMetadata = $null
if (-not [string]::IsNullOrWhiteSpace($RunMetadataPath)) {
    $resolvedRunMetadata = [IO.Path]::GetFullPath($RunMetadataPath)
    if (-not (Test-Path -LiteralPath $resolvedRunMetadata -PathType Leaf)) {
        throw "Run metadata not found: $resolvedRunMetadata"
    }
    $runMetadata = Get-Content -LiteralPath $resolvedRunMetadata -Raw | ConvertFrom-Json
}
$identity = Resolve-TraceReportIdentity -RunMetadata $runMetadata -DefaultMode $Mode `
    -DefaultCompilationMode $CompilationMode -TraceFileName $traceItem.Name
if (-not [string]::IsNullOrWhiteSpace([string]$identity.journeyId) -and
    $identity.journeyId -ne 'unspecified' -and $identity.journeyId -ne $resolvedJourney) {
    throw "Trace measurement window journey '$resolvedJourney' does not match authoritative run metadata journey '$($identity.journeyId)'."
}
$metadata = [ordered]@{
    schemaVersion = 2
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    package = 'com.example.newaudio'
    commit = $identity.commit
    journey = $resolvedJourney
    mode = $identity.mode
    compilationMode = $identity.compilationMode
    baselineEligible = $false
    runId = $identity.runId
    testClass = $identity.testClass
    iteration = $identity.iteration
    capturedAtUtc = $identity.capturedAtUtc
    cacheState = $identity.cacheState
    decoderPath = $identity.decoderPath
    repository = $identity.repository
    runEnvironment = $identity.environment
    trace = [ordered]@{
        fileName = $traceItem.Name
        lengthBytes = $traceItem.Length
        sha256 = $traceHash
    }
    structuralChecks = [ordered]@{
        composeSliceKinds = $composeCount
        measurementWindowKinds = $windowCount
        benchmarkMeasureBlockKinds = $benchmarkWindowCount
        windowSource = $windowSource
        composeSlicesRequired = $composeSlicesRequired
        frameCount = $frameCount
        framesRequired = $framesRequired
        expectedFrameMissingCount = $expectedFrameMissingCount
        appThreadRows = $threadCount
        passed = ($failures.Count -eq 0)
        failures = @($failures)
    }
}
$metadata | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $safeOutput 'metadata.json') -Encoding utf8

$status = if ($failures.Count -eq 0) { 'PASS' } else { 'FAIL' }
$markdown = [Collections.Generic.List[string]]::new()
$markdown.Add('# NewAudio Trace Summary')
$markdown.Add('')
$markdown.Add("- Status: **$status**")
$markdown.Add(('- Journey: `{0}`' -f $resolvedJourney))
$markdown.Add(('- Mode: `{0}`' -f $identity.mode))
$markdown.Add(('- CompilationMode: `{0}`' -f $identity.compilationMode))
$markdown.Add(('- Run / iteration: `{0}` / `{1}`' -f $identity.runId, $identity.iteration))
$markdown.Add(('- Test class: `{0}`' -f $identity.testClass))
$markdown.Add(('- Captured at: `{0}`' -f $identity.capturedAtUtc))
$markdown.Add(('- Cache / decoder: `{0}` / `{1}`' -f $identity.cacheState, $identity.decoderPath))
$commitLabel = if ($null -eq $identity.commit) { '<unknown>' } else { $identity.commit }
$markdown.Add(('- Commit: `{0}`' -f $commitLabel))
if ($null -ne $identity.environment) {
    $markdown.Add(('- Device: `{0} {1}` / API `{2}` / fingerprint `{3}`' -f
        $identity.environment.device.manufacturer, $identity.environment.device.model,
        $identity.environment.device.apiLevel, $identity.environment.device.buildFingerprint))
    $markdown.Add(('- Variant / fixture: `{0}` / `{1}`' -f
        $identity.environment.build.variant, $identity.environment.fixtures.manifestSha256))
}
$markdown.Add(('- Trace: `{0}` (`{1}` bytes, SHA-256 `{2}`)' -f
    $traceItem.Name, $traceItem.Length, $traceHash))
$markdown.Add('')
$markdown.Add('## Structural checks')
$markdown.Add('')
$markdown.Add("- Compose slice groups: $composeCount")
$markdown.Add("- NewAudio measurement-window groups: $windowCount")
$markdown.Add("- Benchmark measureBlock groups: $benchmarkWindowCount")
$markdown.Add(('- Analysis window source: `{0}`' -f $windowSource))
$markdown.Add("- Compose slices required: $composeSlicesRequired")
$markdown.Add("- App frames: $frameCount")
$markdown.Add("- App frames required: $framesRequired")
$markdown.Add("- Frames missing expected duration: $expectedFrameMissingCount")
$markdown.Add("- Main/Render thread rows: $threadCount")
if ($failures.Count -gt 0) {
    $markdown.Add('')
    foreach ($failure in $failures) { $markdown.Add("- **Failure:** $failure") }
}
foreach ($name in $queryNames) {
    $markdown.Add('')
    $markdown.Add('## ' + ($name -replace '_', ' '))
    $markdown.Add('')
    $markdown.Add((ConvertTo-MarkdownTable -Rows @($results[$name])))
}
($markdown -join [Environment]::NewLine) |
    Set-Content -LiteralPath (Join-Path $safeOutput 'summary.md') -Encoding utf8

if ($failures.Count -gt 0) {
    throw "Trace report was written to '$safeOutput', but structural validation failed: $($failures -join ' ')"
}
Write-Host "Trace summary completed. Report: $safeOutput"
