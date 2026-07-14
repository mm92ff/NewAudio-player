package com.example.newaudio.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AudioPlaybackBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun seedFixtures() {
        BenchmarkFixtures().seedAll()
    }

    @Test
    fun au01MiniPlayerIdle() = measure(
        expectedJourney = TraceJourney.AUDIO_MINI_PLAYER,
        setup = { startFirstAudio() },
        journey = {
            idleWindow(TraceJourney.AUDIO_MINI_PLAYER, restartFirstAudioFromBrowser = true)
        }
    )

    @Test
    fun au02FullPlayerIdle() = measure(
        expectedJourney = TraceJourney.AUDIO_FULL_PLAYER,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = {
            startFirstAudio()
            openFullPlayer()
        },
        journey = { idleWindow(TraceJourney.AUDIO_FULL_PLAYER) }
    )

    @Test
    fun au03SeekPauseResumeNext() = measure(
        expectedJourney = TraceJourney.AUDIO_CONTROLS,
        setup = {
            startFirstAudio()
            openFullPlayer()
        },
        journey = { audioControlsJourney() }
    )

    @Test
    fun au04SettingsScrollDuringPlayback() = measure(
        expectedJourney = TraceJourney.AUDIO_SETTINGS,
        setup = { startFirstAudio() },
        journey = { settingsScrollDuringPlayback() }
    )

    @Test
    fun au05PausedControlIdle() = measure(
        expectedJourney = TraceJourney.AUDIO_PAUSED,
        setup = {
            startFirstAudio()
            openFullPlayer()
            pauseAudioAndWait()
        },
        journey = { idleWindow(TraceJourney.AUDIO_PAUSED) }
    )

    @Test
    fun au06MiniPlayerRepeatOffIdle() = measure(
        expectedJourney = TraceJourney.AUDIO_REPEAT_OFF,
        fixtureOptions = FixtureOptions(repeatEnabled = false),
        setup = { startFirstAudio() },
        journey = {
            idleWindow(TraceJourney.AUDIO_REPEAT_OFF, restartFirstAudioFromBrowser = true)
        }
    )

    @Test
    fun au07MiniPlayerRepeatOneIdle() = measure(
        expectedJourney = TraceJourney.AUDIO_REPEAT_ONE,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = { startFirstAudio() },
        journey = { idleWindow(TraceJourney.AUDIO_REPEAT_ONE) }
    )

    @Test
    fun au08LongTitleMarqueeOffIdle() = measure(
        expectedJourney = TraceJourney.AUDIO_MARQUEE_OFF,
        fixtureOptions = FixtureOptions(marqueeEnabled = false, repeatOne = true),
        setup = {
            val title = startLongTitleAudio()
            openFullPlayer(title, expectEmbeddedAlbumArt = false)
        },
        journey = { idleWindow(TraceJourney.AUDIO_MARQUEE_OFF) }
    )

    @Test
    fun au09LongTitleMarqueeOnIdle() = measure(
        expectedJourney = TraceJourney.AUDIO_MARQUEE_ON,
        fixtureOptions = FixtureOptions(marqueeEnabled = true, repeatOne = true),
        setup = {
            val title = startLongTitleAudio()
            openFullPlayer(title, expectEmbeddedAlbumArt = false)
        },
        journey = { idleWindow(TraceJourney.AUDIO_MARQUEE_ON) }
    )

    private fun measure(
        expectedJourney: TraceJourney,
        fixtureOptions: FixtureOptions = FixtureOptions(),
        setup: BenchmarkJourneys.() -> Unit,
        journey: BenchmarkJourneys.() -> Unit
    ) = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = metricsFor(expectedJourney),
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

    private fun metricsFor(expectedJourney: TraceJourney): List<Metric> =
        if (expectedJourney in setOf(
                TraceJourney.AUDIO_MINI_PLAYER,
                TraceJourney.AUDIO_PAUSED,
                TraceJourney.AUDIO_REPEAT_OFF
            )) {
            listOf(IdleWindowMetric(expectedJourney))
        } else {
            listOf(FrameTimingMetric())
        }
}
