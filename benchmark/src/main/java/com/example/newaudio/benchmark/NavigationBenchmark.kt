package com.example.newaudio.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun seedFixtures() {
        BenchmarkFixtures().seedAll()
    }

    @Test
    fun nv01BrowserSettingsBrowser() = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = BenchmarkConfig.compilationMode,
        iterations = BenchmarkConfig.metricIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration(TraceJourney.NAVIGATION_SETTINGS)
            BenchmarkFixtures().seedAll()
            BenchmarkJourneys(this).launchBrowserFromStoppedState()
        }
    ) {
        BenchmarkJourneys(this).apply {
            openSettings()
            navigateBackToBrowser()
        }
    }

    @Test
    fun nv02BrowserPlaylistBrowser() = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = BenchmarkConfig.compilationMode,
        iterations = BenchmarkConfig.metricIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration(TraceJourney.NAVIGATION_PLAYLIST)
            BenchmarkFixtures().seedAll()
            BenchmarkJourneys(this).launchBrowserFromStoppedState()
        }
    ) {
        BenchmarkJourneys(this).apply {
            openPlaylistManager()
            navigateBackToBrowser()
        }
    }

    /** Opt-in red probe used only to verify host-retained diagnostics and exit status. */
    @Test
    fun diagnosticFailureArtifactProbe() {
        assumeTrue(
            "Enable with -Pnewaudio.benchmark.failureProbe=true",
            InstrumentationRegistry.getArguments()
                .getString("newaudio.benchmark.failureProbe")
                .toBoolean()
        )
        BenchmarkDevice.beginIteration("DIAG-FAILURE")
        BenchmarkDevice().fail("Intentional failure-artifact contract probe")
    }
}
