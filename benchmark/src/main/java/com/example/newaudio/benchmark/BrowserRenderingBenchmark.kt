package com.example.newaudio.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BrowserRenderingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @get:Rule
    val testName = TestName()

    @Before
    fun seedFixtures() {
        val requestedShard = InstrumentationRegistry.getArguments().getString(METRIC_SHARD_ARGUMENT)
        if (!requestedShard.isNullOrBlank()) {
            val shardMethods = METRIC_SHARDS[requestedShard]
                ?: error("Unknown metric shard '$requestedShard'")
            Assume.assumeTrue(
                "${testName.methodName} is outside metric shard '$requestedShard'",
                testName.methodName in shardMethods
            )
        }
        BenchmarkFixtures().seedAll()
    }

    @Test
    fun br01AudioListScroll() = measure(
        expectedJourney = TraceJourney.BROWSER_AUDIO_SCROLL,
        setup = { ensureMusicMode() },
        journey = { scrollAudioBrowserDownAndBack() }
    )

    @Test
    fun br02AudioListIdleWithMiniPlayer() = measure(
        expectedJourney = TraceJourney.BROWSER_AUDIO_IDLE,
        setup = { startFirstAudio() },
        journey = {
            idleWindow(TraceJourney.BROWSER_AUDIO_IDLE, restartFirstAudioFromBrowser = true)
        }
    )

    @Test
    fun br03VideoListScroll() = measure(
        expectedJourney = TraceJourney.BROWSER_VIDEO_SCROLL,
        setup = { ensureVideoMode() },
        journey = { scrollVideoBrowserDownAndBack() }
    )

    @Test
    fun br04VideoGalleryTwoColumns() = galleryMeasure(GalleryBenchmarkScenario.TWO_COLUMNS_COLD)

    @Test
    fun br04VideoGalleryThreeColumns() = galleryMeasure(GalleryBenchmarkScenario.THREE_COLUMNS_COLD)

    @Test
    fun br04VideoGalleryFourColumns() = galleryMeasure(GalleryBenchmarkScenario.FOUR_COLUMNS_COLD)

    @Test
    fun br04VideoGalleryTwoColumnsWarm() = galleryMeasure(GalleryBenchmarkScenario.TWO_COLUMNS_WARM)

    @Test
    fun br04VideoGalleryThreeColumnsWarm() = galleryMeasure(GalleryBenchmarkScenario.THREE_COLUMNS_WARM)

    @Test
    fun br04VideoGalleryFourColumnsWarm() = galleryMeasure(GalleryBenchmarkScenario.FOUR_COLUMNS_WARM)

    @Test
    fun br05NestedFolderColdCache() = measure(
        expectedJourney = TraceJourney.BROWSER_FOLDER_COLD,
        setup = { ensureMusicMode() },
        journey = { openNestedAudioFolderAndReturn() }
    )

    @Test
    fun br06NestedFolderWarmCache() = measure(
        expectedJourney = TraceJourney.BROWSER_FOLDER_WARM,
        setup = { openNestedAudioFolderAndReturn() },
        journey = { openNestedAudioFolderAndReturn() }
    )

    @Test
    fun pl01AudioPlaylistScroll() = measure(
        expectedJourney = TraceJourney.PLAYLIST_AUDIO,
        setup = {},
        journey = {
            openAudioPlaylist()
            audioPlaylistScroll()
        }
    )

    @Test
    fun pl02VideoPlaylistScroll() = measure(
        expectedJourney = TraceJourney.PLAYLIST_VIDEO,
        setup = {},
        journey = {
            openVideoPlaylist()
            videoPlaylistScroll()
        }
    )

    private fun measure(
        expectedJourney: TraceJourney,
        fixtureOptions: FixtureOptions = FixtureOptions(),
        setup: BenchmarkJourneys.() -> Unit,
        journey: BenchmarkJourneys.() -> Unit
    ) = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = if (expectedJourney == TraceJourney.BROWSER_AUDIO_IDLE) {
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

    private fun galleryMeasure(scenario: GalleryBenchmarkScenario) = benchmarkRule.measureRepeated(
        packageName = BenchmarkConfig.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = BenchmarkConfig.compilationMode,
        iterations = BenchmarkConfig.metricIterations(),
        setupBlock = {
            BenchmarkDevice.beginIteration(scenario.journeyId)
            // The receiver and browser deliberately share one process-wide ImageLoader.
            // Kill first, then establish the requested cache state, and do not kill the
            // process again before the measured UI consumes it.
            killProcess()
            BenchmarkFixtures().seedAll()
            BenchmarkJourneys(this).apply {
                startAndWaitForBrowser()
                configureVideoGallery(columns = scenario.columns)
            }
            GalleryScrollProbe().resetToStart(scenario)
            BenchmarkFixtures().applyImageCacheState(scenario.cacheState)
        }
    ) {
        GalleryScrollProbe().scrollForwardAndBack(scenario)
    }

    private companion object {
        const val METRIC_SHARD_ARGUMENT = "newaudio.benchmark.shard"

        val METRIC_SHARDS = mapOf(
            "lists" to setOf(
                "br01AudioListScroll",
                "br02AudioListIdleWithMiniPlayer",
                "br03VideoListScroll"
            ),
            "gallery-cold" to setOf(
                "br04VideoGalleryTwoColumns",
                "br04VideoGalleryThreeColumns",
                "br04VideoGalleryFourColumns"
            ),
            "gallery-warm" to setOf(
                "br04VideoGalleryTwoColumnsWarm",
                "br04VideoGalleryThreeColumnsWarm",
                "br04VideoGalleryFourColumnsWarm"
            ),
            "folders-playlists" to setOf(
                "br05NestedFolderColdCache",
                "br06NestedFolderWarmCache",
                "pl01AudioPlaylistScroll",
                "pl02VideoPlaylistScroll"
            )
        )
    }
}
