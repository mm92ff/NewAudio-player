package com.example.newaudio.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class FixtureContractTest {
    @Test
    fun seedAllIsIdempotentAcrossThreeFreshCycles() {
        val fixtures = BenchmarkFixtures()
        val states = List(3) { fixtures.seedAll() }

        assertEquals(1, states.map { it.sha256 }.distinct().size)
        states.forEach { state ->
            with(state.summary) {
                assertEquals(30, getInt("songs"))
                assertEquals(BenchmarkConfig.VIDEO_COUNT, getInt("videos"))
                assertEquals(2, getInt("audioPlaylists"))
                assertEquals(40, getInt("audioPlaylistItems"))
                assertEquals(2, getInt("videoPlaylists"))
                assertEquals(40, getInt("videoPlaylistItems"))
                assertEquals(3, getInt("videoMarkers"))
                assertEquals(BenchmarkConfig.FIXTURE_FILE_COUNT, getInt("fixtureFiles"))
                assertEquals(ImageCacheState.COLD_EMPTY_IMAGE_CACHE.id, getString("cacheState"))
                assertEquals(0, getInt("preloadedImageCacheEntries"))
            }
            assertEquals(ImageCacheState.COLD_EMPTY_IMAGE_CACHE, state.imageCacheState)
            assertEquals(
                BenchmarkConfig.VIDEO_COUNT - BenchmarkConfig.VIDEO_FRAME_DECODER_COUNT,
                state.decoderSummary.getInt(PreviewDecoderPath.ARTWORK_URI.id)
            )
            assertEquals(
                BenchmarkConfig.VIDEO_FRAME_DECODER_COUNT,
                state.decoderSummary.getInt(PreviewDecoderPath.VIDEO_FRAME_DECODER.id)
            )
        }
    }

    @Test
    fun warmCacheAcknowledgesBothDecoderPaths() {
        val state = BenchmarkFixtures().seedAll(
            FixtureOptions(imageCacheState = ImageCacheState.WARM_PRELOADED_IMAGE_CACHE)
        )

        assertEquals(ImageCacheState.WARM_PRELOADED_IMAGE_CACHE, state.imageCacheState)
        assertEquals(
            ImageCacheState.WARM_PRELOADED_IMAGE_CACHE.id,
            state.summary.getString("cacheState")
        )
        assertTrue(state.summary.getInt("preloadedImageCacheEntries") >= 2)
        assertEquals(
            state.summary.getInt("preloadedImageCacheEntries"),
            state.summary.getInt("imageMemoryCacheEntries")
        )
        assertTrue(state.summary.getLong("imageDiskCacheBytes") >= 0L)
        val paths = state.decoderSummary.getJSONArray("preloadedDecoderPaths")
        assertEquals(state.summary.getInt("preloadedImageCacheEntries"), paths.length())
        assertEquals(
            setOf(
                PreviewDecoderPath.ARTWORK_URI.id,
                PreviewDecoderPath.VIDEO_FRAME_DECODER.id
            ),
            buildSet { repeat(paths.length()) { add(paths.getString(it)) } }
        )
    }

    @Test
    fun galleryScenarioMappingIsUniqueAndRejectsUnknownMethods() {
        GalleryBenchmarkScenario.validateContract()
        GalleryBenchmarkScenario.entries.forEach { scenario ->
            assertEquals(scenario, GalleryBenchmarkScenario.fromMethod(scenario.methodName))
        }
        assertThrows(IllegalStateException::class.java) {
            GalleryBenchmarkScenario.fromMethod("unknownGalleryBenchmark")
        }
    }

}
