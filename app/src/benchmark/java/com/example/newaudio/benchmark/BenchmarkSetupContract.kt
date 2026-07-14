package com.example.newaudio.benchmark

internal object BenchmarkSetupContract {
    const val ACTION_SETUP = "com.example.newaudio.benchmark.SETUP"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_FIXTURE_VERSION = "fixture_version"
    const val EXTRA_MARQUEE_ENABLED = "marquee_enabled"
    const val EXTRA_REPEAT_ONE = "repeat_one"
    const val EXTRA_REPEAT_ENABLED = "repeat_enabled"
    const val EXTRA_VIDEO_MARKERS_ENABLED = "video_markers_enabled"
    const val EXTRA_IMAGE_CACHE_STATE = "image_cache_state"
    const val EXTRA_STATE_SHA256 = "state_sha256"
    const val EXTRA_STATE_SUMMARY = "state_summary"
    const val EXTRA_CACHE_STATE = "cache_state"
    const val EXTRA_DECODER_SUMMARY = "decoder_summary"
    const val FIXTURE_VERSION = 2

    const val CACHE_STATE_COLD = "COLD_EMPTY_IMAGE_CACHE"
    const val CACHE_STATE_WARM = "WARM_PRELOADED_IMAGE_CACHE"
    const val DECODER_ARTWORK_URI = "ARTWORK_URI"
    const val DECODER_VIDEO_FRAME = "VIDEO_FRAME_DECODER"

    const val COMMAND_RESET = "RESET"
    const val COMMAND_APPLY_IMAGE_CACHE = "APPLY_IMAGE_CACHE"
    const val COMMAND_SEED_AUDIO = "SEED_AUDIO"
    const val COMMAND_SEED_VIDEO = "SEED_VIDEO"
    const val COMMAND_SEED_PLAYLISTS = "SEED_PLAYLISTS"
    const val COMMAND_SEED_ALL = "SEED_ALL"

    const val RESULT_READY_PREFIX = "READY:"
    const val RESULT_FAILURE_PREFIX = "FAILURE:"
}
