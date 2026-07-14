package com.example.newaudio.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class TraceCaptureTest {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun seedFixtures() {
        BenchmarkFixtures().seedAll()
    }

    @Test
    fun traceColdStartup() = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = listOf(
            StartupTimingMetric(),
            RequiredTraceSlicesMetric(TraceJourney.STARTUP)
        ),
        compilationMode = BenchmarkConfig.compilationMode,
        startupMode = StartupMode.COLD,
        iterations = BenchmarkConfig.traceIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration(TraceJourney.STARTUP)
            BenchmarkFixtures().seedAll()
            pressHome()
        }
    ) {
        BenchmarkJourneys(this).measuredWindow(TraceJourney.STARTUP) {
            startAndWaitForBrowser()
        }
    }

    @Test
    fun traceNavigationSettings() = traceJourney(
        expectedJourney = TraceJourney.NAVIGATION_SETTINGS,
        setup = {},
        journey = {
            measuredWindow(TraceJourney.NAVIGATION_SETTINGS) {
                openSettings()
                navigateBackToBrowser()
            }
        }
    )

    @Test
    fun traceNavigationPlaylist() = traceJourney(
        expectedJourney = TraceJourney.NAVIGATION_PLAYLIST,
        setup = {},
        journey = {
            measuredWindow(TraceJourney.NAVIGATION_PLAYLIST) {
                openPlaylistManager()
                navigateBackToBrowser()
            }
        }
    )

    @Test
    fun traceAudioBrowserScroll() = traceJourney(
        expectedJourney = TraceJourney.BROWSER_AUDIO_SCROLL,
        setup = { ensureMusicMode() },
        journey = {
            measuredWindow(TraceJourney.BROWSER_AUDIO_SCROLL) {
                scrollAudioBrowserDownAndBack()
            }
        }
    )

    @Test
    fun traceAudioBrowserIdle() = traceJourney(
        expectedJourney = TraceJourney.BROWSER_AUDIO_IDLE,
        setup = { startFirstAudio() },
        journey = {
            idleWindow(TraceJourney.BROWSER_AUDIO_IDLE, restartFirstAudioFromBrowser = true)
        }
    )

    @Test
    fun traceVideoBrowserScroll() = traceJourney(
        expectedJourney = TraceJourney.BROWSER_VIDEO_SCROLL,
        setup = { ensureVideoMode() },
        journey = {
            measuredWindow(TraceJourney.BROWSER_VIDEO_SCROLL) {
                scrollVideoBrowserDownAndBack()
            }
        }
    )

    @Test
    fun traceVideoGalleryTwoColumns() = traceGallery(
        columns = 2,
        journey = TraceJourney.BROWSER_GALLERY_TWO
    )

    @Test
    fun traceVideoGalleryThreeColumns() = traceGallery(
        columns = 3,
        journey = TraceJourney.BROWSER_GALLERY_THREE
    )

    @Test
    fun traceVideoGalleryFourColumns() = traceGallery(
        columns = 4,
        journey = TraceJourney.BROWSER_GALLERY_FOUR
    )

    @Test
    fun traceNestedFolderColdCache() = traceJourney(
        expectedJourney = TraceJourney.BROWSER_FOLDER_COLD,
        setup = { ensureMusicMode() },
        journey = {
            measuredWindow(TraceJourney.BROWSER_FOLDER_COLD) {
                openNestedAudioFolderAndReturn()
            }
        }
    )

    @Test
    fun traceNestedFolderWarmCache() = traceJourney(
        expectedJourney = TraceJourney.BROWSER_FOLDER_WARM,
        setup = { openNestedAudioFolderAndReturn() },
        journey = {
            measuredWindow(TraceJourney.BROWSER_FOLDER_WARM) {
                openNestedAudioFolderAndReturn()
            }
        }
    )

    @Test
    fun traceAudioPlaylistScroll() = traceJourney(
        expectedJourney = TraceJourney.PLAYLIST_AUDIO,
        setup = {},
        journey = {
            measuredWindow(TraceJourney.PLAYLIST_AUDIO) {
                openAudioPlaylist()
                audioPlaylistScroll()
            }
        }
    )

    @Test
    fun traceVideoPlaylistScroll() = traceJourney(
        expectedJourney = TraceJourney.PLAYLIST_VIDEO,
        setup = {},
        journey = {
            measuredWindow(TraceJourney.PLAYLIST_VIDEO) {
                openVideoPlaylist()
                videoPlaylistScroll()
            }
        }
    )

    @Test
    fun traceAudioMiniPlayerIdle() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_MINI_PLAYER,
        setup = { startFirstAudio() },
        journey = {
            idleWindow(TraceJourney.AUDIO_MINI_PLAYER, restartFirstAudioFromBrowser = true)
        }
    )

    @Test
    fun traceAudioFullPlayerIdle() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_FULL_PLAYER,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = {
            startFirstAudio()
            openFullPlayer()
        },
        journey = { idleWindow(TraceJourney.AUDIO_FULL_PLAYER) }
    )

    @Test
    fun traceAudioControls() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_CONTROLS,
        setup = {
            startFirstAudio()
            openFullPlayer()
        },
        journey = {
            measuredWindow(TraceJourney.AUDIO_CONTROLS) { audioControlsJourney() }
        }
    )

    @Test
    fun traceSettingsDuringAudio() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_SETTINGS,
        setup = { startFirstAudio() },
        journey = {
            measuredWindow(TraceJourney.AUDIO_SETTINGS) { settingsScrollDuringPlayback() }
        }
    )

    @Test
    fun traceAudioPausedControlIdle() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_PAUSED,
        setup = {
            startFirstAudio()
            openFullPlayer()
            pauseAudioAndWait()
        },
        journey = { idleWindow(TraceJourney.AUDIO_PAUSED) }
    )

    @Test
    fun traceAudioRepeatOffIdle() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_REPEAT_OFF,
        fixtureOptions = FixtureOptions(repeatEnabled = false),
        setup = { startFirstAudio() },
        journey = {
            idleWindow(TraceJourney.AUDIO_REPEAT_OFF, restartFirstAudioFromBrowser = true)
        }
    )

    @Test
    fun traceAudioRepeatOneIdle() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_REPEAT_ONE,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = { startFirstAudio() },
        journey = { idleWindow(TraceJourney.AUDIO_REPEAT_ONE) }
    )

    @Test
    fun traceAudioMarqueeOffIdle() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_MARQUEE_OFF,
        fixtureOptions = FixtureOptions(marqueeEnabled = false, repeatOne = true),
        setup = {
            val title = startLongTitleAudio()
            openFullPlayer(title, expectEmbeddedAlbumArt = false)
        },
        journey = { idleWindow(TraceJourney.AUDIO_MARQUEE_OFF) }
    )

    @Test
    fun traceAudioMarqueeOnIdle() = traceJourney(
        expectedJourney = TraceJourney.AUDIO_MARQUEE_ON,
        fixtureOptions = FixtureOptions(marqueeEnabled = true, repeatOne = true),
        setup = {
            val title = startLongTitleAudio()
            openFullPlayer(title, expectEmbeddedAlbumArt = false)
        },
        journey = { idleWindow(TraceJourney.AUDIO_MARQUEE_ON) }
    )

    @Test
    fun traceVideoInlineIdle() = traceJourney(
        expectedJourney = TraceJourney.VIDEO_INLINE,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = { startFirstVideo() },
        journey = { idleWindow(TraceJourney.VIDEO_INLINE) }
    )

    @Test
    fun traceVideoFullscreenIdle() = traceJourney(
        expectedJourney = TraceJourney.VIDEO_FULLSCREEN,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = {
            startFirstVideo()
            openVideoFullscreen()
        },
        journey = { idleWindow(TraceJourney.VIDEO_FULLSCREEN) }
    )

    @Test
    fun traceVideoControlsSeekAndMarker() = traceJourney(
        expectedJourney = TraceJourney.VIDEO_CONTROLS,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = {
            startFirstVideo()
            openVideoFullscreen()
        },
        journey = {
            measuredWindow(TraceJourney.VIDEO_CONTROLS) { videoControlsJourney() }
        }
    )

    @Test
    fun traceVideoFullscreenMarkersOnStable() = traceJourney(
        expectedJourney = TraceJourney.VIDEO_MARKERS_ON,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = {
            startFirstVideo()
            openVideoFullscreen()
            pinVideoControlsForStableIdle(expectMarkers = true)
        },
        journey = { idleWindow(TraceJourney.VIDEO_MARKERS_ON) }
    )

    @Test
    fun traceVideoFullscreenInlineTransition() = traceJourney(
        expectedJourney = TraceJourney.VIDEO_TRANSITION,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = { startFirstVideo() },
        journey = {
            measuredWindow(TraceJourney.VIDEO_TRANSITION) {
                openVideoFullscreen()
                fullscreenToInline()
            }
        }
    )

    @Test
    fun traceVideoSwipeNextPrevious() = traceJourney(
        expectedJourney = TraceJourney.VIDEO_SWIPE,
        fixtureOptions = FixtureOptions(repeatOne = true),
        setup = {
            startFirstVideo()
            openVideoFullscreen()
        },
        journey = {
            measuredWindow(TraceJourney.VIDEO_SWIPE) { swipeToNextAndPreviousVideo() }
        }
    )

    @Test
    fun traceVideoFullscreenMarkersOff() = traceJourney(
        expectedJourney = TraceJourney.VIDEO_MARKERS_OFF,
        fixtureOptions = FixtureOptions(repeatOne = true, videoMarkersEnabled = false),
        setup = {
            startFirstVideo()
            openVideoFullscreen()
            pinVideoControlsForStableIdle(expectMarkers = false)
        },
        journey = { idleWindow(TraceJourney.VIDEO_MARKERS_OFF) }
    )

    private fun traceGallery(columns: Int, journey: TraceJourney) = traceJourney(
        expectedJourney = journey,
        fixtureOptions = FixtureOptions(
            imageCacheState = GalleryBenchmarkScenario.coldForColumns(columns).cacheState
        ),
        preserveFixtureProcess = true,
        setup = {
            val scenario = GalleryBenchmarkScenario.coldForColumns(columns)
            configureVideoGallery(columns)
            GalleryScrollProbe().resetToStart(scenario)
            BenchmarkFixtures().applyImageCacheState(scenario.cacheState)
        },
        journey = {
            measuredWindow(journey) {
                GalleryScrollProbe().scrollForwardAndBack(
                    GalleryBenchmarkScenario.coldForColumns(columns)
                )
            }
        }
    )

    private fun traceJourney(
        expectedJourney: TraceJourney,
        fixtureOptions: FixtureOptions = FixtureOptions(),
        preserveFixtureProcess: Boolean = false,
        setup: BenchmarkJourneys.() -> Unit,
        journey: BenchmarkJourneys.() -> Unit
    ) = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = if (expectedJourney in setOf(
                TraceJourney.BROWSER_AUDIO_IDLE,
                TraceJourney.AUDIO_MINI_PLAYER,
                TraceJourney.AUDIO_PAUSED,
                TraceJourney.AUDIO_REPEAT_OFF,
                TraceJourney.VIDEO_FULLSCREEN
            )) {
            listOf(
                IdleWindowMetric(expectedJourney),
                RequiredTraceSlicesMetric(
                    expectedJourney,
                    requireFrames = expectedJourney !in setOf(
                        TraceJourney.AUDIO_PAUSED,
                        TraceJourney.VIDEO_FULLSCREEN
                    )
                )
            )
        } else {
            listOf(FrameTimingMetric(), RequiredTraceSlicesMetric(expectedJourney))
        },
        compilationMode = BenchmarkConfig.compilationMode,
        iterations = BenchmarkConfig.traceIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration(expectedJourney)
            if (preserveFixtureProcess) { killProcess() }
            BenchmarkFixtures().seedAll(fixtureOptions)
            BenchmarkJourneys(this).apply {
                if (preserveFixtureProcess) startAndWaitForBrowser()
                else launchBrowserFromStoppedState()
                setup()
            }
        }
    ) {
        BenchmarkJourneys(this).journey()
    }
}
