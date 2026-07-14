package com.example.newaudio.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class BenchmarkFixtures(
    private val benchmarkDevice: BenchmarkDevice = BenchmarkDevice(),
    private val context: Context = InstrumentationRegistry.getInstrumentation().context
) {
    fun seedAll(options: FixtureOptions = FixtureOptions()): FixtureState {
        benchmarkDevice.prepareSystem()
        verifyManifestOrFail()
        run(FixtureCommand.RESET, options)
        return run(FixtureCommand.SEED_ALL, options)
    }

    fun seedAudio() {
        benchmarkDevice.prepareSystem()
        verifyManifestOrFail()
        run(FixtureCommand.RESET)
        run(FixtureCommand.SEED_AUDIO)
    }

    fun seedVideo() {
        benchmarkDevice.prepareSystem()
        verifyManifestOrFail()
        run(FixtureCommand.RESET)
        run(FixtureCommand.SEED_VIDEO)
    }

    fun applyImageCacheState(state: ImageCacheState): FixtureState = run(
        FixtureCommand.APPLY_IMAGE_CACHE,
        FixtureOptions(imageCacheState = state)
    )

    fun run(
        command: FixtureCommand,
        options: FixtureOptions = FixtureOptions()
    ): FixtureState {
        val latch = CountDownLatch(1)
        val receivedCode = AtomicInteger(Activity.RESULT_CANCELED)
        val resultData = AtomicReference<String?>(null)
        val resultExtras = AtomicReference<Bundle?>(null)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                receivedCode.set(getResultCode())
                resultData.set(getResultData())
                resultExtras.set(getResultExtras(false))
                latch.countDown()
            }
        }
        val intent = Intent(BenchmarkConfig.SETUP_ACTION).apply {
            component = ComponentName(
                BenchmarkConfig.TARGET_PACKAGE,
                BenchmarkConfig.SETUP_RECEIVER
            )
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            putExtra("command", command.name)
            putExtra("fixture_version", BenchmarkConfig.FIXTURE_VERSION)
            putExtra(BenchmarkConfig.EXTRA_MARQUEE_ENABLED, options.marqueeEnabled)
            putExtra(BenchmarkConfig.EXTRA_REPEAT_ENABLED, options.repeatEnabled)
            putExtra(BenchmarkConfig.EXTRA_REPEAT_ONE, options.repeatOne)
            putExtra(BenchmarkConfig.EXTRA_VIDEO_MARKERS_ENABLED, options.videoMarkersEnabled)
            putExtra(BenchmarkConfig.EXTRA_IMAGE_CACHE_STATE, options.imageCacheState.id)
        }

        context.sendOrderedBroadcast(
            intent,
            // The receiver's manifest permission authenticates this same-signed sender.
            // Passing it here would additionally require the target app to request its
            // own permission and would silently filter the explicit receiver.
            null,
            receiver,
            Handler(Looper.getMainLooper()),
            Activity.RESULT_CANCELED,
            null,
            null
        )

        if (!latch.await(BenchmarkConfig.FIXTURE_SETUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            benchmarkDevice.fail("Benchmark fixture command ${command.name} timed out")
        }
        if (receivedCode.get() != Activity.RESULT_OK) {
            benchmarkDevice.fail(
                "Benchmark fixture command ${command.name} failed: " +
                    "resultCode=${receivedCode.get()}, resultData=${resultData.get()}, " +
                    "extras=${resultExtras.get()}"
            )
        }
        if (resultData.get() != "READY:${command.name}") {
            benchmarkDevice.fail(
                "Unexpected setup acknowledgement for ${command.name}: ${resultData.get()}"
            )
        }
        val extras = resultExtras.get()
            ?: benchmarkDevice.fail("Fixture command ${command.name} returned no state extras")
        val sha256 = extras.getString(BenchmarkConfig.EXTRA_STATE_SHA256)
            ?: benchmarkDevice.fail("Fixture command ${command.name} returned no state SHA-256")
        val summary = extras.getString(BenchmarkConfig.EXTRA_STATE_SUMMARY)
            ?: benchmarkDevice.fail("Fixture command ${command.name} returned no state summary")
        val cacheState = extras.getString(BenchmarkConfig.EXTRA_CACHE_STATE)
            ?: benchmarkDevice.fail("Fixture command ${command.name} returned no cache state")
        val decoderSummary = extras.getString(BenchmarkConfig.EXTRA_DECODER_SUMMARY)
            ?: benchmarkDevice.fail("Fixture command ${command.name} returned no decoder summary")
        return FixtureState(
            sha256 = sha256,
            summary = JSONObject(summary),
            imageCacheState = ImageCacheState.fromId(cacheState),
            decoderSummary = JSONObject(decoderSummary)
        )
    }

    private fun verifyManifestOrFail() {
        try {
            verifyManifest()
        } catch (error: Throwable) {
            benchmarkDevice.fail("Fixture manifest verification failed: ${error.message}")
        }
    }

    private fun verifyManifest() {
        val json = context.assets.open("fixtures/fixture-manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val manifest = JSONObject(json)
        check(manifest.getInt("fixtureVersion") == BenchmarkConfig.FIXTURE_VERSION)
        check(manifest.getInt("audioCount") == BenchmarkConfig.AUDIO_COUNT)
        check(manifest.getInt("videoCount") == BenchmarkConfig.VIDEO_COUNT)
        check(manifest.stringSet("cacheStates") == ImageCacheState.entries.map { it.id }.toSet())
        check(manifest.stringSet("decoderPaths") == PreviewDecoderPath.entries.map { it.id }.toSet())
        check(manifest.stringSet("specialNames") ==
            (BenchmarkConfig.SPECIAL_AUDIO_FILES + BenchmarkConfig.SPECIAL_VIDEO_FILES).toSet())

        validateTemplateMetadata(manifest.getJSONObject("templateMetadata"))
        validateAudioEntries(manifest.getJSONArray("audio"))
        validateVideoEntries(manifest.getJSONArray("video"))
        validatePlaylists(manifest.getJSONArray("playlists"))
        check(manifest.getJSONArray("videoMarkers").length() >= 1)
    }

    private fun validateTemplateMetadata(metadata: JSONObject) {
        check(metadata.length() == 8) { "Expected metadata for exactly eight binary templates" }
        val ids = mutableSetOf<String>()
        metadata.keys().forEach { name ->
            val entry = metadata.getJSONObject(name)
            check(ids.add(entry.requiredString("id"))) { "Duplicate template ID for $name" }
            entry.requiredString("mediaType")
            entry.requiredString("mimeType")
            check(entry.getLong("byteSize") > 0L) { "Invalid byte size for $name" }
            if (entry.has("durationMs")) {
                check(entry.getLong("durationMs") > 0L) { "Invalid duration for $name" }
                check(entry.getLong("durationToleranceMs") in 0L..50L) {
                    "Invalid duration tolerance for $name"
                }
            }
            if (entry.has("width")) {
                check(entry.getInt("width") > 0 && entry.getInt("height") > 0) {
                    "Invalid dimensions for $name"
                }
            }
        }
    }

    private fun validateAudioEntries(entries: JSONArray) {
        check(entries.length() == BenchmarkConfig.AUDIO_COUNT)
        val ids = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        repeat(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            check(ids.add(entry.requiredString("id"))) { "Duplicate audio ID at index $index" }
            check(names.add(entry.requiredString("displayName"))) { "Duplicate audio filename at index $index" }
            check(entry.getInt("sortIndex") == index)
            entry.requiredString("relativePath")
            entry.requiredString("title")
            entry.requiredString("artist")
            entry.requiredString("album")
            entry.requiredString("artworkPath")
            entry.requiredSha256("sha256")
            check(entry.getLong("byteSize") > 0L)
            check(entry.getLong("durationMs") > 0L)
            check(entry.getLong("durationToleranceMs") in 0L..50L)
            check(entry.getLong("timestampMs") == 1_700_000_000_000L + index)
        }
        check(names.containsAll(BenchmarkConfig.SPECIAL_AUDIO_FILES))
    }

    private fun validateVideoEntries(entries: JSONArray) {
        check(entries.length() == BenchmarkConfig.VIDEO_COUNT)
        val ids = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        val decoderPaths = mutableSetOf<String>()
        repeat(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            check(ids.add(entry.requiredString("id"))) { "Duplicate video ID at index $index" }
            check(names.add(entry.requiredString("displayName"))) { "Duplicate video filename at index $index" }
            check(entry.getInt("sortIndex") == index)
            entry.requiredString("relativePath")
            entry.requiredString("title")
            entry.requiredSha256("sha256")
            check(entry.getLong("byteSize") > 0L)
            check(entry.getLong("durationMs") > 0L)
            check(entry.getLong("durationToleranceMs") in 0L..50L)
            check(entry.getInt("width") == 320 && entry.getInt("height") == 180)
            check(entry.getInt("frameRate") == 15)
            check(entry.getLong("timestampMs") == 1_700_000_001_000L + index)
            val decoderPath = entry.requiredString("decoderPath")
            decoderPaths += decoderPath
            when (decoderPath) {
                PreviewDecoderPath.ARTWORK_URI.id -> check(!entry.isNull("thumbnailPath"))
                PreviewDecoderPath.VIDEO_FRAME_DECODER.id -> check(entry.isNull("thumbnailPath"))
                else -> error("Unknown decoder path '$decoderPath'")
            }
        }
        check(names.containsAll(BenchmarkConfig.SPECIAL_VIDEO_FILES))
        check(decoderPaths == PreviewDecoderPath.entries.map { it.id }.toSet())
    }

    private fun validatePlaylists(playlists: JSONArray) {
        check(playlists.length() == 4)
        repeat(playlists.length()) { index ->
            val playlist = playlists.getJSONObject(index)
            playlist.requiredString("name")
            playlist.requiredString("mediaType")
            check(playlist.getJSONArray("items").length() == 20)
        }
    }

    private fun JSONObject.stringSet(name: String): Set<String> = getJSONArray(name).let { values ->
        buildSet { repeat(values.length()) { add(values.getString(it)) } }
    }

    private fun JSONObject.requiredString(name: String): String = getString(name).also {
        check(it.isNotBlank()) { "Manifest field '$name' must not be blank" }
    }

    private fun JSONObject.requiredSha256(name: String): String = requiredString(name).also {
        check(it.matches(Regex("[0-9a-f]{64}"))) { "Manifest field '$name' is not SHA-256" }
    }
}

internal data class FixtureOptions(
    val marqueeEnabled: Boolean = true,
    val repeatEnabled: Boolean = true,
    val repeatOne: Boolean = false,
    val videoMarkersEnabled: Boolean = true,
    val imageCacheState: ImageCacheState = ImageCacheState.COLD_EMPTY_IMAGE_CACHE
)

internal data class FixtureState(
    val sha256: String,
    val summary: JSONObject,
    val imageCacheState: ImageCacheState,
    val decoderSummary: JSONObject
)
