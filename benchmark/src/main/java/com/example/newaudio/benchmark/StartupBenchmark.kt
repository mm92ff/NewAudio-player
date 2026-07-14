package com.example.newaudio.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun seedFixtures() {
        BenchmarkFixtures().seedAll()
    }

    @Test
    fun st01ColdStartupToBrowserReady() = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = BenchmarkConfig.compilationMode,
        startupMode = StartupMode.COLD,
        iterations = BenchmarkConfig.startupIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration("ST-01")
            BenchmarkFixtures().seedAll()
            pressHome()
        }
    ) {
        val journey = BenchmarkJourneys(this)
        journey.startAndWaitForBrowser()
    }

    @Test
    fun st02WarmStartupToBrowserReady() = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = BenchmarkConfig.compilationMode,
        startupMode = StartupMode.WARM,
        iterations = BenchmarkConfig.startupIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration("ST-02")
            BenchmarkFixtures().seedAll()
            pressHome()
            val journey = BenchmarkJourneys(this)
            journey.startAndWaitForBrowser()
            pressHome()
        }
    ) {
        BenchmarkJourneys(this).startAndWaitForBrowser()
    }
}
