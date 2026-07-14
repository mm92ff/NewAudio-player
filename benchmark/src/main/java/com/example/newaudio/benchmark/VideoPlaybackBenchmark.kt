package com.example.newaudio.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class VideoPlaybackBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun seedFixtures() {
        BenchmarkFixtures().seedAll()
    }

    @Test
    fun vi01InlineVideoIdle() = measure(
        expectedJourney = TraceJourney.VIDEO_INLINE,
        setup = { startFirstVideo() },
        journey = { idleWindow(TraceJourney.VIDEO_INLINE) }
    )

    @Test
    fun vi02FullscreenIdle() = measure(
        expectedJourney = TraceJourney.VIDEO_FULLSCREEN,
        setup = {
            startFirstVideo()
            openVideoFullscreen()
        },
        journey = { idleWindow(TraceJourney.VIDEO_FULLSCREEN) }
    )

    @Test
    fun vi03ControlsSeekAndMarker() = measure(
        expectedJourney = TraceJourney.VIDEO_CONTROLS,
        setup = {
            startFirstVideo()
            openVideoFullscreen()
        },
        journey = { videoControlsJourney() }
    )

    @Test
    fun vi04FullscreenInlineTransition() = measure(
        expectedJourney = TraceJourney.VIDEO_TRANSITION,
        setup = { startFirstVideo() },
        journey = {
            openVideoFullscreen()
            fullscreenToInline()
        }
    )

    @Test
    fun vi05SwipeNextPrevious() = measure(
        expectedJourney = TraceJourney.VIDEO_SWIPE,
        setup = {
            startFirstVideo()
            openVideoFullscreen()
        },
        journey = { swipeToNextAndPreviousVideo() }
    )

    @Test
    fun vi06FullscreenIdleMarkersOff() = measure(
        expectedJourney = TraceJourney.VIDEO_MARKERS_OFF,
        fixtureOptions = FixtureOptions(repeatOne = true, videoMarkersEnabled = false),
        setup = {
            startFirstVideo()
            openVideoFullscreen()
            pinVideoControlsForStableIdle(expectMarkers = false)
        },
        journey = { idleWindow(TraceJourney.VIDEO_MARKERS_OFF) }
    )

    @Test
    fun vi06FullscreenIdleMarkersOn() = measure(
        expectedJourney = TraceJourney.VIDEO_MARKERS_ON,
        setup = {
            startFirstVideo()
            openVideoFullscreen()
            pinVideoControlsForStableIdle(expectMarkers = true)
        },
        journey = { idleWindow(TraceJourney.VIDEO_MARKERS_ON) }
    )

    private fun measure(
        expectedJourney: TraceJourney,
        fixtureOptions: FixtureOptions = FixtureOptions(repeatOne = true),
        setup: BenchmarkJourneys.() -> Unit,
        journey: BenchmarkJourneys.() -> Unit
    ) = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = if (expectedJourney == TraceJourney.VIDEO_FULLSCREEN) {
            listOf(IdleWindowMetric(expectedJourney))
        } else {
            listOf(FrameTimingMetric())
        },
        compilationMode = BenchmarkConfig.compilationMode,
        iterations = BenchmarkConfig.metricIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration(expectedJourney)
            BenchmarkFixtures().seedAll(fixtureOptions)
            BenchmarkJourneys(this).apply {
                launchBrowserFromStoppedState()
                setup()
            }
        }
    ) {
        BenchmarkJourneys(this).journey()
    }
}
