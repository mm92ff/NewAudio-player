[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $PSCommandPath
$metricRunner = Join-Path $scriptRoot 'run-benchmarks.ps1'
$traceRunner = Join-Path $scriptRoot 'run-compose-trace.ps1'

function Assert-Rejected {
    param([scriptblock]$Action, [string]$Label)

    $rejected = $false
    try {
        & $Action | Out-Null
    } catch {
        $rejected = $true
    }
    if (-not $rejected) { throw "Runner contract did not reject $Label." }
}

& $metricRunner `
    -TestClass 'com.example.newaudio.benchmark.StartupBenchmark#st01ColdStartupToBrowserReady' `
    -RetryCount 1 `
    -AllowDirty `
    -DryRun | Out-Null
& $metricRunner `
    -TestClass 'com.example.newaudio.benchmark.BrowserRenderingBenchmark' `
    -MetricShard 'gallery-cold' `
    -AllowDirty `
    -DryRun | Out-Null
& $traceRunner `
    -TestClass 'com.example.newaudio.benchmark.TraceCaptureTest#traceColdStartup' `
    -AllowDirty `
    -SkipSummary `
    -DryRun | Out-Null
& $traceRunner `
    -TraceShard 'audio-core' `
    -AllowDirty `
    -SkipSummary `
    -DryRun | Out-Null

Assert-Rejected -Label 'TraceCaptureTest in the metric runner' -Action {
    & $metricRunner -TestClass 'com.example.newaudio.benchmark.TraceCaptureTest' -DryRun
}
Assert-Rejected -Label 'StartupBenchmark in the trace runner' -Action {
    & $traceRunner -TestClass 'com.example.newaudio.benchmark.StartupBenchmark' -DryRun
}
Assert-Rejected -Label 'a runner-owned class override' -Action {
    & $metricRunner `
        -AdditionalGradleArguments '-Pandroid.testInstrumentationRunnerArguments.class=invalid' `
        -DryRun
}
Assert-Rejected -Label 'a runner-owned tracing override' -Action {
    & $traceRunner -AdditionalGradleArguments '-PfullTracing=false' -DryRun
}
Assert-Rejected -Label 'an unknown metric method' -Action {
    & $metricRunner -TestClass 'com.example.newaudio.benchmark.AudioPlaybackBenchmark#au99Typo' -DryRun
}
Assert-Rejected -Label 'a metric shard on a non-browser class' -Action {
    & $metricRunner `
        -TestClass 'com.example.newaudio.benchmark.AudioPlaybackBenchmark' `
        -MetricShard 'lists' `
        -DryRun
}
Assert-Rejected -Label 'a metric shard with a method selector' -Action {
    & $metricRunner `
        -TestClass 'com.example.newaudio.benchmark.BrowserRenderingBenchmark#br01AudioListScroll' `
        -MetricShard 'lists' `
        -DryRun
}
Assert-Rejected -Label 'an unknown trace method' -Action {
    & $traceRunner -TestClass 'com.example.newaudio.benchmark.TraceCaptureTest#traceTypo' -SkipSummary -DryRun
}
Assert-Rejected -Label 'an unknown trace shard' -Action {
    & $traceRunner -TraceShard 'unknown-shard' -SkipSummary -DryRun
}
Assert-Rejected -Label 'a trace shard with a method selector' -Action {
    & $traceRunner `
        -TestClass 'com.example.newaudio.benchmark.TraceCaptureTest#traceColdStartup' `
        -TraceShard 'startup-navigation' `
        -SkipSummary `
        -DryRun
}
Assert-Rejected -Label 'a runner-owned trace shard override' -Action {
    & $traceRunner `
        -AdditionalGradleArguments '-Pandroid.testInstrumentationRunnerArguments.newaudio.trace.shard=audio-core' `
        -DryRun
}
Assert-Rejected -Label 'a runner-owned metric shard override' -Action {
    & $metricRunner `
        -AdditionalGradleArguments '-Pandroid.testInstrumentationRunnerArguments.newaudio.benchmark.shard=lists' `
        -DryRun
}
Assert-Rejected -Label 'a contradictory cache-state override' -Action {
    & $metricRunner `
        -TestClass 'com.example.newaudio.benchmark.BrowserRenderingBenchmark#br04VideoGalleryTwoColumnsWarm' `
        -CacheState COLD_EMPTY_IMAGE_CACHE `
        -AllowDirty `
        -DryRun
}
Assert-Rejected -Label 'a sensitive persisted Gradle argument' -Action {
    & $metricRunner -AdditionalGradleArguments '-PapiKey=do-not-persist' -AllowDirty -DryRun
}
Assert-Rejected -Label 'an iteration count above the metric contract' -Action {
    & $metricRunner `
        -TestClass 'com.example.newaudio.benchmark.AudioPlaybackBenchmark' `
        -Iterations 6 `
        -AllowDirty `
        -DryRun
}
Assert-Rejected -Label 'more than one automatic metric retry' -Action {
    & $metricRunner `
        -TestClass 'com.example.newaudio.benchmark.AudioPlaybackBenchmark' `
        -RetryCount 2 `
        -AllowDirty `
        -DryRun
}

Write-Host 'Runner allowlist and owned-argument contracts passed.'
