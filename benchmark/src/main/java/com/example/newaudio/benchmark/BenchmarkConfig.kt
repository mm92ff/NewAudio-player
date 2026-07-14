package com.example.newaudio.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.test.platform.app.InstrumentationRegistry

internal object BenchmarkConfig {
    const val TARGET_PACKAGE = "com.example.newaudio"
    const val BENCHMARK_PACKAGE = "com.example.newaudio.benchmark"
    const val SETUP_PERMISSION = "com.example.newaudio.permission.BENCHMARK_SETUP"
    const val SETUP_ACTION = "com.example.newaudio.benchmark.SETUP"
    const val SETUP_RECEIVER = "com.example.newaudio.benchmark.BenchmarkSetupReceiver"
    const val EXTRA_MARQUEE_ENABLED = "marquee_enabled"
    const val EXTRA_REPEAT_ONE = "repeat_one"
    const val EXTRA_REPEAT_ENABLED = "repeat_enabled"
    const val EXTRA_VIDEO_MARKERS_ENABLED = "video_markers_enabled"
    const val EXTRA_IMAGE_CACHE_STATE = "image_cache_state"
    const val EXTRA_STATE_SHA256 = "state_sha256"
    const val EXTRA_STATE_SUMMARY = "state_summary"
    const val EXTRA_CACHE_STATE = "cache_state"
    const val EXTRA_DECODER_SUMMARY = "decoder_summary"

    const val DEFAULT_TIMEOUT_MS = 20_000L
    const val FIXTURE_SETUP_TIMEOUT_MS = 90_000L
    const val PLAYBACK_READY_TIMEOUT_MS = 30_000L
    const val FIXTURE_VERSION = 2
    const val METRIC_ITERATIONS = 5
    const val STARTUP_ITERATIONS = 10
    const val TRACE_ITERATIONS = 3
    const val AUDIO_COUNT = 30
    const val VIDEO_COUNT = 48
    const val VIDEO_FRAME_DECODER_COUNT = 9
    const val FIXTURE_FILE_COUNT = AUDIO_COUNT + VIDEO_COUNT + 4
    const val ITERATIONS_ARGUMENT = "newaudio.benchmark.iterations"
    const val IDLE_TRACE_WINDOW_MS = 10_000L

    const val FIRST_AUDIO = "Audio_05_Cover_Medium.mp3"
    const val FIRST_AUDIO_TITLE = "Audio_05_Cover_Medium"
    const val LAST_AUDIO = "Audio_30_Cover_Large.mp3"
    const val FIRST_PLAYLIST_AUDIO_TITLE = "Audio_%_Literal"
    const val LAST_PLAYLIST_AUDIO_TITLE = "Audio_20"
    const val LONG_TITLE_AUDIO =
        "Audio with a deliberately very long benchmark title for marquee validation.wav"
    const val LONG_TITLE_AUDIO_TITLE =
        "Audio with a deliberately very long benchmark title for marquee validation"
    const val NEXT_AUDIO_TITLE = "Audio_06"
    const val FIRST_VIDEO = "Video_05.mp4"
    const val FIRST_PLAYLIST_VIDEO_TITLE = "Video_%_Literal"
    const val LAST_PLAYLIST_VIDEO_TITLE = "Video_20"
    const val SECOND_VIDEO = "Video_06.mp4"
    const val LAST_VIDEO = "Video_24.mp4"
    const val FIRST_VIDEO_TITLE = "Video_05"
    const val SECOND_VIDEO_TITLE = "Video_06"
    const val AUDIO_PLAYLIST = "Benchmark Audio A"
    const val VIDEO_PLAYLIST = "Benchmark Video A"
    val SPECIAL_AUDIO_FILES = listOf(
        "Audio_%_Literal.wav",
        "Audio_underscore_.wav",
        "Audio_Ünicode_你好.wav"
    )
    val SPECIAL_VIDEO_FILES = listOf(
        "Video_%_Literal.mp4",
        "Video_underscore_.mp4",
        "Video_Ünicode_你好.mp4"
    )

    val compilationMode: CompilationMode
        get() = CompilationMode.Partial(warmupIterations = 3)

    fun metricIterations(): Int = overriddenIterations(METRIC_ITERATIONS)

    fun startupIterations(): Int = overriddenIterations(STARTUP_ITERATIONS)

    fun traceIterations(): Int = overriddenIterations(TRACE_ITERATIONS)

    private fun overriddenIterations(defaultValue: Int): Int =
        InstrumentationRegistry.getArguments()
            .getString(ITERATIONS_ARGUMENT)
            ?.toIntOrNull()
            ?.takeIf { it in 1..defaultValue }
            ?: defaultValue
}

internal enum class ImageCacheState(val id: String) {
    COLD_EMPTY_IMAGE_CACHE("COLD_EMPTY_IMAGE_CACHE"),
    WARM_PRELOADED_IMAGE_CACHE("WARM_PRELOADED_IMAGE_CACHE");

    companion object {
        fun fromId(value: String): ImageCacheState = entries.firstOrNull { it.id == value }
            ?: error("Unknown benchmark image-cache state '$value'")
    }
}

internal enum class PreviewDecoderPath(val id: String) {
    ARTWORK_URI("ARTWORK_URI"),
    VIDEO_FRAME_DECODER("VIDEO_FRAME_DECODER")
}

internal enum class GalleryBenchmarkScenario(
    val methodName: String,
    val journeyId: String,
    val columns: Int,
    val cacheState: ImageCacheState
) {
    TWO_COLUMNS_COLD(
        "br04VideoGalleryTwoColumns",
        "BR-04-GRID-2-COLD",
        2,
        ImageCacheState.COLD_EMPTY_IMAGE_CACHE
    ),
    THREE_COLUMNS_COLD(
        "br04VideoGalleryThreeColumns",
        "BR-04-GRID-3-COLD",
        3,
        ImageCacheState.COLD_EMPTY_IMAGE_CACHE
    ),
    FOUR_COLUMNS_COLD(
        "br04VideoGalleryFourColumns",
        "BR-04-GRID-4-COLD",
        4,
        ImageCacheState.COLD_EMPTY_IMAGE_CACHE
    ),
    TWO_COLUMNS_WARM(
        "br04VideoGalleryTwoColumnsWarm",
        "BR-04-GRID-2-WARM",
        2,
        ImageCacheState.WARM_PRELOADED_IMAGE_CACHE
    ),
    THREE_COLUMNS_WARM(
        "br04VideoGalleryThreeColumnsWarm",
        "BR-04-GRID-3-WARM",
        3,
        ImageCacheState.WARM_PRELOADED_IMAGE_CACHE
    ),
    FOUR_COLUMNS_WARM(
        "br04VideoGalleryFourColumnsWarm",
        "BR-04-GRID-4-WARM",
        4,
        ImageCacheState.WARM_PRELOADED_IMAGE_CACHE
    );

    companion object {
        fun fromMethod(methodName: String): GalleryBenchmarkScenario =
            entries.firstOrNull { it.methodName == methodName }
                ?: error("Unknown gallery benchmark method '$methodName'")

        fun coldForColumns(columns: Int): GalleryBenchmarkScenario =
            entries.firstOrNull {
                it.columns == columns && it.cacheState == ImageCacheState.COLD_EMPTY_IMAGE_CACHE
            } ?: error("No cold gallery scenario for $columns columns")

        fun validateContract() {
            check(entries.map { it.methodName }.distinct().size == entries.size) {
                "Gallery benchmark method names must be unique"
            }
            check(entries.map { it.journeyId }.distinct().size == entries.size) {
                "Gallery benchmark journey IDs must be unique"
            }
        }
    }
}

internal enum class FixtureCommand {
    RESET,
    APPLY_IMAGE_CACHE,
    SEED_AUDIO,
    SEED_VIDEO,
    SEED_PLAYLISTS,
    SEED_ALL
}

internal enum class TraceJourney(val sectionName: String) {
    STARTUP("NewAudio:ST01"),
    NAVIGATION_SETTINGS("NewAudio:NV01"),
    NAVIGATION_PLAYLIST("NewAudio:NV02"),
    BROWSER_AUDIO_SCROLL("NewAudio:BR01"),
    BROWSER_AUDIO_IDLE("NewAudio:BR02"),
    BROWSER_VIDEO_SCROLL("NewAudio:BR03"),
    BROWSER_GALLERY_TWO("NewAudio:BR04_GRID_2_COLD"),
    BROWSER_GALLERY_THREE("NewAudio:BR04_GRID_3_COLD"),
    BROWSER_GALLERY_FOUR("NewAudio:BR04_GRID_4_COLD"),
    BROWSER_FOLDER_COLD("NewAudio:BR05"),
    BROWSER_FOLDER_WARM("NewAudio:BR06"),
    PLAYLIST_AUDIO("NewAudio:PL01"),
    PLAYLIST_VIDEO("NewAudio:PL02"),
    AUDIO_MINI_PLAYER("NewAudio:AU01"),
    AUDIO_FULL_PLAYER("NewAudio:AU02"),
    AUDIO_CONTROLS("NewAudio:AU03"),
    AUDIO_SETTINGS("NewAudio:AU04"),
    AUDIO_PAUSED("NewAudio:AU05"),
    AUDIO_REPEAT_OFF("NewAudio:AU06"),
    AUDIO_REPEAT_ONE("NewAudio:AU07"),
    AUDIO_MARQUEE_OFF("NewAudio:AU08"),
    AUDIO_MARQUEE_ON("NewAudio:AU09"),
    VIDEO_INLINE("NewAudio:VI01"),
    VIDEO_FULLSCREEN("NewAudio:VI02"),
    VIDEO_CONTROLS("NewAudio:VI03"),
    VIDEO_TRANSITION("NewAudio:VI04"),
    VIDEO_SWIPE("NewAudio:VI05"),
    VIDEO_MARKERS_ON("NewAudio:VI06_MARKERS_ON"),
    VIDEO_MARKERS_OFF("NewAudio:VI06_MARKERS_OFF")
}
