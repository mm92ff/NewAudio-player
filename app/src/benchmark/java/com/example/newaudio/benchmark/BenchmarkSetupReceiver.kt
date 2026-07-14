package com.example.newaudio.benchmark

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import coil.annotation.ExperimentalCoilApi
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.newaudio.BuildConfig
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.data.database.VideoMarkerEntity
import com.example.newaudio.data.database.VideoPlaylistEntity
import com.example.newaudio.data.database.VideoPlaylistItemEntity
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.service.MediaPlaybackService
import com.example.newaudio.ui.NewAudioImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.abs
import javax.inject.Inject
import org.json.JSONObject

object BenchmarkPrivatePathGuard {
    @JvmStatic
    fun requireDirectPrivateChild(parent: File, candidate: File, expectedName: String): File {
        val canonicalParent = parent.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        check(canonicalCandidate.parentFile == canonicalParent && canonicalCandidate.name == expectedName) {
            "Refusing to control unsafe benchmark path: $canonicalCandidate"
        }
        return canonicalCandidate
    }
}

@OptIn(ExperimentalCoilApi::class)
@AndroidEntryPoint
class BenchmarkSetupReceiver : BroadcastReceiver() {

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var settingsRepository: ISettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val fixtureVersion = intent.getIntExtra(
            BenchmarkSetupContract.EXTRA_FIXTURE_VERSION,
            -1
        )
        if (
            !BuildConfig.BENCHMARK ||
            intent.action != BenchmarkSetupContract.ACTION_SETUP ||
            fixtureVersion != BenchmarkSetupContract.FIXTURE_VERSION
        ) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "${BenchmarkSetupContract.RESULT_FAILURE_PREFIX}unsupported target or fixture version"
            return
        }

        val command = intent.getStringExtra(BenchmarkSetupContract.EXTRA_COMMAND)
            ?.uppercase()
            ?: BenchmarkSetupContract.COMMAND_SEED_ALL
        val options = BenchmarkSeedOptions(
            marqueeEnabled = intent.getBooleanExtra(
                BenchmarkSetupContract.EXTRA_MARQUEE_ENABLED,
                true
            ),
            repeatEnabled = intent.getBooleanExtra(
                BenchmarkSetupContract.EXTRA_REPEAT_ENABLED,
                true
            ),
            repeatOne = intent.getBooleanExtra(BenchmarkSetupContract.EXTRA_REPEAT_ONE, false),
            videoMarkersEnabled = intent.getBooleanExtra(
                BenchmarkSetupContract.EXTRA_VIDEO_MARKERS_ENABLED,
                true
            ),
            imageCacheState = intent.getStringExtra(BenchmarkSetupContract.EXTRA_IMAGE_CACHE_STATE)
                ?: BenchmarkSetupContract.CACHE_STATE_COLD
        )
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val runtimeState = runCommand(context.applicationContext, command, options)
                val state = benchmarkState(context.applicationContext, runtimeState)
                pendingResult.resultCode = Activity.RESULT_OK
                pendingResult.resultData = "${BenchmarkSetupContract.RESULT_READY_PREFIX}$command"
                pendingResult.setResultExtras(android.os.Bundle().apply {
                    putString(BenchmarkSetupContract.EXTRA_STATE_SHA256, state.sha256)
                    putString(BenchmarkSetupContract.EXTRA_STATE_SUMMARY, state.summary.toString())
                    putString(BenchmarkSetupContract.EXTRA_CACHE_STATE, runtimeState.cacheState)
                    putString(
                        BenchmarkSetupContract.EXTRA_DECODER_SUMMARY,
                        runtimeState.decoderSummary.toString()
                    )
                })
            } catch (error: Throwable) {
                pendingResult.resultCode = Activity.RESULT_CANCELED
                pendingResult.resultData = buildString {
                    append(BenchmarkSetupContract.RESULT_FAILURE_PREFIX)
                    append(error::class.java.simpleName)
                    error.message?.let { append(':').append(it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun runCommand(
        context: Context,
        command: String,
        options: BenchmarkSeedOptions
    ): BenchmarkRuntimeState {
        val fixtureContract = if (command != BenchmarkSetupContract.COMMAND_RESET) {
            verifyFixtureAssets(context)
        } else {
            null
        }
        return when (command) {
            BenchmarkSetupContract.COMMAND_RESET -> reset(context)
            BenchmarkSetupContract.COMMAND_APPLY_IMAGE_CACHE -> {
                val videos = database.videoDao().getAllVideosInTree(
                    File(fixtureRoot(context), VIDEO_DIRECTORY).absolutePath
                )
                check(videos.size == VIDEO_COUNT) {
                    "Image-cache control requires $VIDEO_COUNT seeded videos; found ${videos.size}"
                }
                applyImageCacheState(context, options.imageCacheState, videos)
            }
            BenchmarkSetupContract.COMMAND_SEED_AUDIO -> {
                reset(context)
                seed(
                    context,
                    includeAudio = true,
                    includeVideo = false,
                    includePlaylists = false,
                    options = options,
                    fixtureContract = checkNotNull(fixtureContract)
                )
            }
            BenchmarkSetupContract.COMMAND_SEED_VIDEO -> {
                reset(context)
                seed(
                    context,
                    includeAudio = false,
                    includeVideo = true,
                    includePlaylists = false,
                    options = options,
                    fixtureContract = checkNotNull(fixtureContract)
                )
            }
            BenchmarkSetupContract.COMMAND_SEED_PLAYLISTS,
            BenchmarkSetupContract.COMMAND_SEED_ALL -> {
                reset(context)
                seed(
                    context,
                    includeAudio = true,
                    includeVideo = true,
                    includePlaylists = true,
                    options = options,
                    fixtureContract = checkNotNull(fixtureContract)
                )
            }
            else -> error("Unknown benchmark command: $command")
        }
    }

    private suspend fun reset(context: Context): BenchmarkRuntimeState {
        context.stopService(Intent(context, MediaPlaybackService::class.java))
        database.clearAllTables()
        dataStore.edit { it.clear() }
        clearImageCaches(context)
        deleteFixtureRoot(context)
        settingsRepository.restoreUserPreferences(UserPreferences.default())
        return BenchmarkRuntimeState.cold()
    }

    private suspend fun seed(
        context: Context,
        includeAudio: Boolean,
        includeVideo: Boolean,
        includePlaylists: Boolean,
        options: BenchmarkSeedOptions,
        fixtureContract: FixtureAssetContract
    ): BenchmarkRuntimeState {
        val fixtureRoot = fixtureRoot(context).apply { mkdirsOrThrow() }
        val artworkSmall = copyAsset(
            context,
            "fixtures/artwork-small.png",
            File(fixtureRoot, "artwork-small.png")
        )
        val artworkMedium = copyAsset(
            context,
            "fixtures/artwork.png",
            File(fixtureRoot, "artwork.png")
        )
        val artworkLarge = copyAsset(
            context,
            "fixtures/artwork-large.png",
            File(fixtureRoot, "artwork-large.png")
        )
        listOf(artworkSmall, artworkMedium, artworkLarge).forEachIndexed { index, file ->
            file.setDeterministicTimestamp(FIXED_TIMESTAMP_MS + ARTWORK_TIMESTAMP_OFFSET + index)
        }

        val songs = if (includeAudio) {
            seedAudio(
                context,
                fixtureRoot,
                listOf(artworkSmall, artworkMedium, artworkLarge),
                fixtureContract
            )
        } else {
            emptyList()
        }
        val videos = if (includeVideo) {
            seedVideo(
                context,
                fixtureRoot,
                listOf(artworkSmall, artworkMedium, artworkLarge),
                fixtureContract
            )
        } else {
            emptyList()
        }

        val audioRoot = File(fixtureRoot, AUDIO_DIRECTORY)
        val videoRoot = File(fixtureRoot, VIDEO_DIRECTORY)
        val preferences = UserPreferences.default().copy(
            musicFolderPath = audioRoot.takeIf { includeAudio }?.absolutePath.orEmpty(),
            videoFolderPath = videoRoot.takeIf { includeVideo }?.absolutePath.orEmpty(),
            isAutoPlayOnStart = false,
            isAutoPlayOnBluetooth = false,
            isMarqueeEnabled = options.marqueeEnabled,
            useMarquee = options.marqueeEnabled,
            repeatMode = when {
                !options.repeatEnabled -> UserPreferences.RepeatMode.NONE
                options.repeatOne -> UserPreferences.RepeatMode.ONE
                else -> UserPreferences.RepeatMode.ALL
            },
            videoDisplayMode = UserPreferences.VideoDisplayMode.LIST,
            videoGalleryColumns = 3,
            showVideoNamesInGallery = true,
            videoMarkersEnabled = includeVideo && options.videoMarkersEnabled
        )
        settingsRepository.restoreUserPreferences(preferences)

        if (includePlaylists) {
            seedPlaylists(songs, videos)
        }
        if (videos.isNotEmpty()) {
            val markerVideo = videos.firstOrNull { it.filename == MARKER_VIDEO_FILENAME }
                ?: videos.first()
            seedMarkers(markerVideo)
        }
        return applyImageCacheState(context, options.imageCacheState, videos)
    }

    private suspend fun seedAudio(
        context: Context,
        fixtureRoot: File,
        artwork: List<File>,
        fixtureContract: FixtureAssetContract
    ): List<SongEntity> {
        val audioRoot = File(fixtureRoot, AUDIO_DIRECTORY).apply { mkdirsOrThrow() }
        val nestedFolders = listOf("Albums/One", "Albums/Two", "Unicode", "Long Titles")

        val songs = (0 until AUDIO_COUNT).map { index ->
            val parent = if (index < nestedFolders.size) {
                File(audioRoot, nestedFolders[index]).apply { mkdirsOrThrow() }
            } else {
                audioRoot
            }
            val filename = audioFilename(index)
            val destination = File(parent, filename)
            val templatePath = audioTemplate(index)
            copyAsset(context, templatePath, destination)
            destination.setDeterministicTimestamp(FIXED_TIMESTAMP_MS + index)
            val hash = destination.sha256()
            val albumArtwork = when (index) {
                4 -> artwork[1]
                27 -> artwork[0]
                29 -> artwork[2]
                else -> artwork[1]
            }

            SongEntity(
                path = destination.absolutePath,
                contentUri = Uri.fromFile(destination).toString(),
                title = destination.nameWithoutExtension,
                artist = "NewAudio Benchmark Artist ${index % 4}",
                album = "NewAudio Benchmark Album ${index % 3}",
                duration = fixtureContract.durationMs(templatePath.removePrefix("fixtures/")),
                albumArtPath = albumArtwork.absolutePath,
                parentPath = parent.absolutePath,
                filename = filename,
                lastModified = FIXED_TIMESTAMP_MS + index,
                size = destination.length(),
                fileHash = hash
            )
        }
        database.songDao().insertAll(songs)
        return songs
    }

    private suspend fun seedVideo(
        context: Context,
        fixtureRoot: File,
        artwork: List<File>,
        fixtureContract: FixtureAssetContract
    ): List<VideoEntity> {
        val videoRoot = File(fixtureRoot, VIDEO_DIRECTORY).apply { mkdirsOrThrow() }
        val template = copyAsset(
            context,
            "fixtures/video-template.mp4",
            File(fixtureRoot, "video-template.mp4")
        )
        template.setDeterministicTimestamp(FIXED_TIMESTAMP_MS + VIDEO_TEMPLATE_TIMESTAMP_OFFSET)
        val nestedFolders = listOf("Clips/One", "Clips/Two", "Unicode", "Markers")

        val videos = (0 until VIDEO_COUNT).map { index ->
            val parent = if (index < nestedFolders.size) {
                File(videoRoot, nestedFolders[index]).apply { mkdirsOrThrow() }
            } else {
                videoRoot
            }
            val filename = videoFilename(index)
            val destination = File(parent, filename)
            template.copyTo(destination, overwrite = true)
            destination.setDeterministicTimestamp(FIXED_TIMESTAMP_MS + VIDEO_TIMESTAMP_OFFSET + index)
            val hash = destination.sha256()
            val thumbnail = videoThumbnail(index, artwork, fixtureContract.videoFrameDecoderIndexes)

            VideoEntity(
                path = destination.absolutePath,
                contentUri = Uri.fromFile(destination).toString(),
                title = destination.nameWithoutExtension,
                duration = fixtureContract.durationMs("video-template.mp4"),
                thumbnailUri = thumbnail?.let { Uri.fromFile(it).toString() },
                parentPath = parent.absolutePath,
                filename = filename,
                lastModified = FIXED_TIMESTAMP_MS + VIDEO_TIMESTAMP_OFFSET + index,
                size = destination.length(),
                width = VIDEO_WIDTH,
                height = VIDEO_HEIGHT,
                fileHash = hash
            )
        }
        database.videoDao().insertAll(videos)
        return videos
    }

    private suspend fun seedPlaylists(songs: List<SongEntity>, videos: List<VideoEntity>) {
        if (songs.isNotEmpty()) {
            val firstId = database.playlistDao().insertPlaylist(
                PlaylistEntity(id = 1L, name = "Benchmark Audio A", position = 0, createdAt = FIXED_TIMESTAMP_MS)
            )
            val secondId = database.playlistDao().insertPlaylist(
                PlaylistEntity(id = 2L, name = "Benchmark Audio B", position = 1, createdAt = FIXED_TIMESTAMP_MS + 1)
            )
            val selected = songs.take(PLAYLIST_ITEM_COUNT)
            database.playlistDao().insertPlaylistSongs(selected.mapIndexed { index, song ->
                PlaylistSongEntity(firstId, song.path, index)
            })
            database.playlistDao().insertPlaylistSongs(selected.asReversed().mapIndexed { index, song ->
                PlaylistSongEntity(secondId, song.path, index)
            })
        }

        if (videos.isNotEmpty()) {
            val firstId = database.videoPlaylistDao().insertPlaylist(
                VideoPlaylistEntity(id = 1L, name = "Benchmark Video A", position = 0, createdAt = FIXED_TIMESTAMP_MS)
            )
            val secondId = database.videoPlaylistDao().insertPlaylist(
                VideoPlaylistEntity(id = 2L, name = "Benchmark Video B", position = 1, createdAt = FIXED_TIMESTAMP_MS + 1)
            )
            val selected = videos.take(PLAYLIST_ITEM_COUNT)
            database.videoPlaylistDao().insertPlaylistVideos(selected.mapIndexed { index, video ->
                VideoPlaylistItemEntity(firstId, video.path, index)
            })
            database.videoPlaylistDao().insertPlaylistVideos(selected.asReversed().mapIndexed { index, video ->
                VideoPlaylistItemEntity(secondId, video.path, index)
            })
        }
    }

    private suspend fun seedMarkers(video: VideoEntity) {
        listOf(2_000L, 6_000L, 10_000L).forEachIndexed { index, positionMs ->
            database.videoMarkerDao().insert(
                VideoMarkerEntity(
                    id = index + 1L,
                    videoPath = video.path,
                    fileHash = video.fileHash,
                    filename = video.filename,
                    fileSize = video.size,
                    durationMs = video.duration,
                    positionMs = positionMs,
                    createdAt = FIXED_TIMESTAMP_MS + index,
                    updatedAt = FIXED_TIMESTAMP_MS + index
                )
            )
        }
    }

    private fun copyAsset(context: Context, assetPath: String, destination: File): File {
        destination.parentFile?.mkdirsOrThrow()
        context.assets.open(assetPath).use { input ->
            destination.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        check(destination.isFile && destination.length() > 0L) {
            "Benchmark asset was not copied: $assetPath"
        }
        val sourceHash = context.assets.open(assetPath).use { it.sha256() }
        check(destination.sha256() == sourceHash) {
            "Benchmark asset hash changed while copying: $assetPath"
        }
        return destination
    }

    private fun verifyFixtureAssets(context: Context): FixtureAssetContract {
        val manifest = context.assets.open(FIXTURE_MANIFEST).bufferedReader().use { reader ->
            JSONObject(reader.readText())
        }
        check(manifest.getInt("schemaVersion") == BenchmarkSetupContract.FIXTURE_VERSION) {
            "Unsupported benchmark fixture manifest version"
        }
        check(manifest.getInt("audioCount") == AUDIO_COUNT)
        check(manifest.getInt("videoCount") == VIDEO_COUNT)
        val videoFrameDecoderIndexes = manifest.getJSONArray("videoFrameDecoderIndexes").let { values ->
            buildSet {
                repeat(values.length()) {
                    add(values.getInt(it).also { index -> check(index in 0 until VIDEO_COUNT) })
                }
            }
        }
        check(videoFrameDecoderIndexes.size >= 2) {
            "At least two distributed video-frame decoder fixtures are required"
        }
        val templates = manifest.getJSONObject("templates")
        val metadata = manifest.getJSONObject("templateMetadata")
        check(templates.length() == metadata.length()) {
            "Every binary template requires exactly one metadata contract"
        }
        val ids = mutableSetOf<String>()
        val verified = mutableMapOf<String, FixtureTemplateMetadata>()
        val names = templates.keys()
        while (names.hasNext()) {
            val name = names.next()
            val expected = templates.getString(name).lowercase()
            val bytes = context.assets.open("fixtures/$name").use { it.readBytes() }
            val actual = bytes.inputStream().use { it.sha256() }
            check(actual == expected) { "Benchmark fixture SHA-256 mismatch: $name" }
            val contractJson = metadata.getJSONObject(name)
            val contract = FixtureTemplateMetadata.fromJson(name, contractJson)
            check(ids.add(contract.id)) { "Duplicate benchmark template ID: ${contract.id}" }
            check(bytes.size.toLong() == contract.byteSize) {
                "Benchmark fixture byte-size mismatch: $name"
            }
            validateTemplateMetadata(context, contract, bytes)
            verified[name] = contract
        }
        check(verified.keys == metadata.keys().asSequence().toSet()) {
            "Template and metadata names differ"
        }
        return FixtureAssetContract(verified, videoFrameDecoderIndexes)
    }

    private fun validateTemplateMetadata(
        context: Context,
        contract: FixtureTemplateMetadata,
        bytes: ByteArray
    ) {
        when (contract.mediaType) {
            "artwork" -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                check(bounds.outWidth == contract.width && bounds.outHeight == contract.height) {
                    "Artwork dimensions differ for ${contract.name}"
                }
            }
            "audio", "video" -> validateMediaMetadata(context, contract)
            else -> error("Unsupported fixture media type '${contract.mediaType}'")
        }
    }

    private fun validateMediaMetadata(context: Context, contract: FixtureTemplateMetadata) {
        val retriever = MediaMetadataRetriever()
        try {
            context.assets.openFd("fixtures/${contract.name}").use { descriptor ->
                retriever.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
                val actualDuration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: error("Missing duration for ${contract.name}")
                check(abs(actualDuration - checkNotNull(contract.durationMs)) <= contract.durationToleranceMs) {
                    "Duration mismatch for ${contract.name}: expected ${contract.durationMs}±" +
                        "${contract.durationToleranceMs}ms, got ${actualDuration}ms"
                }
                if (contract.mediaType == "video") {
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()
                    check(width == contract.width && height == contract.height) {
                        "Video dimensions differ for ${contract.name}: ${width}x$height"
                    }
                }
                if (contract.embeddedArtworkWidth != null) {
                    val picture = retriever.embeddedPicture
                        ?: error("Missing embedded artwork for ${contract.name}")
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
                    check(
                        bounds.outWidth == contract.embeddedArtworkWidth &&
                            bounds.outHeight == contract.embeddedArtworkHeight
                    ) { "Embedded artwork dimensions differ for ${contract.name}" }
                }
            }
        } finally {
            retriever.release()
        }

        if (contract.mediaType == "video") {
            val extractor = MediaExtractor()
            try {
                context.assets.openFd("fixtures/${contract.name}").use { descriptor ->
                    extractor.setDataSource(
                        descriptor.fileDescriptor,
                        descriptor.startOffset,
                        descriptor.length
                    )
                    val trackMimes = (0 until extractor.trackCount).mapNotNull { track ->
                        extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME)
                    }.toSet()
                    check(contract.videoCodecMime in trackMimes) {
                        "Missing ${contract.videoCodecMime} track in ${contract.name}"
                    }
                    check(contract.audioCodecMime in trackMimes) {
                        "Missing ${contract.audioCodecMime} track in ${contract.name}"
                    }
                }
            } finally {
                extractor.release()
            }
        }
    }

    private suspend fun applyImageCacheState(
        context: Context,
        requestedState: String,
        videos: List<VideoEntity>
    ): BenchmarkRuntimeState {
        val decoderSummary = decoderSummary(videos)
        val imageLoader = clearImageCaches(context)
        return when (requestedState) {
            BenchmarkSetupContract.CACHE_STATE_COLD -> BenchmarkRuntimeState(
                cacheState = requestedState,
                preloadedEntries = 0,
                memoryCacheEntries = 0,
                diskCacheBytes = checkNotNull(imageLoader.diskCache).size,
                decoderSummary = decoderSummary
            )
            BenchmarkSetupContract.CACHE_STATE_WARM -> {
                val targets = videos.map { video ->
                    val thumbnail = video.thumbnailUri
                    PreviewCacheTarget(
                        model = thumbnail ?: video.contentUri,
                        cacheKey = "video-preview:${video.contentUri.ifBlank { video.path }}",
                        decoderPath = if (thumbnail == null) {
                            BenchmarkSetupContract.DECODER_VIDEO_FRAME
                        } else {
                            BenchmarkSetupContract.DECODER_ARTWORK_URI
                        }
                    )
                }.distinctBy { it.cacheKey }
                check(targets.map { it.decoderPath }.toSet() == setOf(
                    BenchmarkSetupContract.DECODER_ARTWORK_URI,
                    BenchmarkSetupContract.DECODER_VIDEO_FRAME
                )) {
                    "Warm image cache must cover every declared preview decoder path"
                }
                targets.forEach { target -> preload(imageLoader, context, target) }
                val memoryEntries = imageLoader.memoryCache?.keys?.size ?: 0
                check(memoryEntries >= targets.size) {
                    "Warm image cache contains $memoryEntries entries for ${targets.size} preview targets"
                }
                val diskBytes = checkNotNull(imageLoader.diskCache).size
                BenchmarkRuntimeState(
                    cacheState = requestedState,
                    preloadedEntries = targets.size,
                    memoryCacheEntries = memoryEntries,
                    diskCacheBytes = diskBytes,
                    decoderSummary = decoderSummary.put(
                        "preloadedDecoderPaths",
                        org.json.JSONArray(targets.map { it.decoderPath })
                    )
                )
            }
            else -> error("Unknown benchmark image-cache state '$requestedState'")
        }
    }

    private suspend fun preload(
        imageLoader: ImageLoader,
        context: Context,
        target: PreviewCacheTarget
    ) {
        val request = ImageRequest.Builder(context)
            .data(target.model)
            .memoryCacheKey(target.cacheKey)
            .diskCacheKey(target.cacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.DISABLED)
            .crossfade(false)
            .build()
        check(imageLoader.execute(request) is SuccessResult) {
            "Could not preload ${target.decoderPath} fixture into Coil cache"
        }
    }

    private fun clearImageCaches(context: Context): ImageLoader {
        val expectedDirectory = BenchmarkPrivatePathGuard.requireDirectPrivateChild(
            context.cacheDir,
            File(context.cacheDir, COIL_CACHE_DIRECTORY),
            COIL_CACHE_DIRECTORY
        )
        val imageLoader = NewAudioImageLoader.get(context)
        val diskCache = checkNotNull(imageLoader.diskCache) { "Coil disk cache is unavailable" }
        val actualDirectory = File(diskCache.directory.toString()).canonicalFile
        check(actualDirectory == expectedDirectory) {
            "Unexpected Coil cache directory: $actualDirectory"
        }
        imageLoader.memoryCache?.clear()
        diskCache.clear()
        check(imageLoader.memoryCache?.keys?.isEmpty() != false) {
            "Coil memory cache could not be cleared"
        }
        check(diskCache.size == 0L) { "Coil disk cache could not be cleared" }
        return imageLoader
    }

    private fun decoderSummary(videos: List<VideoEntity>): JSONObject = JSONObject().apply {
        put(
            BenchmarkSetupContract.DECODER_ARTWORK_URI,
            videos.count { !it.thumbnailUri.isNullOrBlank() }
        )
        put(
            BenchmarkSetupContract.DECODER_VIDEO_FRAME,
            videos.count { it.thumbnailUri.isNullOrBlank() }
        )
    }

    private fun deleteFixtureRoot(context: Context) {
        val canonicalRoot = BenchmarkPrivatePathGuard.requireDirectPrivateChild(
            context.filesDir,
            fixtureRoot(context),
            FIXTURE_DIRECTORY
        )
        if (canonicalRoot.exists()) {
            check(canonicalRoot.deleteRecursively()) { "Could not clear benchmark fixtures" }
        }
    }

    private fun fixtureRoot(context: Context): File = File(context.filesDir, FIXTURE_DIRECTORY)

    private fun File.mkdirsOrThrow() {
        check(isDirectory || mkdirs()) { "Could not create benchmark directory: $this" }
    }

    private fun File.sha256(): String = inputStream().buffered().use { it.sha256() }

    private fun InputStream.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private suspend fun benchmarkState(
        context: Context,
        runtimeState: BenchmarkRuntimeState
    ): BenchmarkState {
        val sqlite = database.openHelper.readableDatabase
        fun count(table: String): Int = sqlite.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst()) { "Could not count $table" }
            cursor.getInt(0)
        }
        fun rows(query: String): List<String> = sqlite.query(query).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).joinToString("|") { column ->
                        if (cursor.isNull(column)) "<null>" else cursor.getString(column)
                    })
                }
            }
        }

        val fixtureRoot = fixtureRoot(context)
        val fixtureFiles = if (fixtureRoot.isDirectory) {
            fixtureRoot.walkTopDown()
                .filter { it.isFile }
                .map { file ->
                    "${file.relativeTo(fixtureRoot).invariantSeparatorsPath}:" +
                        "${file.sha256()}:${file.length()}:${file.lastModified()}"
                }
                .sorted()
                .toList()
        } else {
            emptyList()
        }
        val preferences = dataStore.data.first().asMap().entries
            .sortedBy { it.key.name }
            .map { (key, value) ->
                val normalized = if (value is Set<*>) {
                    value.map { it.toString() }.sorted().joinToString(",")
                } else {
                    value.toString()
                }
                "${key.name}=$normalized"
            }
        val orderedState = buildList {
            addAll(rows(
                "SELECT path, contentUri, title, artist, album, duration, albumArtPath, " +
                    "parentPath, filename, lastModified, size, fileHash FROM songs ORDER BY path"
            ))
            addAll(rows(
                "SELECT path, contentUri, title, duration, thumbnailUri, parentPath, filename, " +
                    "lastModified, size, width, height, fileHash FROM videos ORDER BY path"
            ))
            addAll(rows("SELECT id, name, position, createdAt FROM playlists ORDER BY position"))
            addAll(rows("SELECT playlistId, songPath, position FROM playlist_songs ORDER BY playlistId, position"))
            addAll(rows("SELECT id, name, position, createdAt FROM video_playlists ORDER BY position"))
            addAll(rows("SELECT playlistId, videoPath, position FROM video_playlist_items ORDER BY playlistId, position"))
            addAll(rows(
                "SELECT id, videoPath, fileHash, filename, fileSize, durationMs, positionMs, " +
                    "createdAt, updatedAt FROM video_markers ORDER BY videoPath, positionMs"
            ))
            addAll(preferences)
            addAll(fixtureFiles)
        }
        val summary = JSONObject().apply {
            put("songs", count("songs"))
            put("videos", count("videos"))
            put("audioPlaylists", count("playlists"))
            put("audioPlaylistItems", count("playlist_songs"))
            put("videoPlaylists", count("video_playlists"))
            put("videoPlaylistItems", count("video_playlist_items"))
            put("videoMarkers", count("video_markers"))
            put("fixtureFiles", fixtureFiles.size)
            put("cacheState", runtimeState.cacheState)
            put("preloadedImageCacheEntries", runtimeState.preloadedEntries)
            put("imageMemoryCacheEntries", runtimeState.memoryCacheEntries)
            put("imageDiskCacheBytes", runtimeState.diskCacheBytes)
            put("decoderSummary", runtimeState.decoderSummary)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(orderedState.joinToString("\n").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return BenchmarkState(digest, summary)
    }

    private fun audioFilename(index: Int): String = when (index) {
        0 -> "Audio_%_Literal.wav"
        1 -> "Audio_underscore_.wav"
        2 -> "Audio_Ünicode_你好.wav"
        3 -> "Audio with a deliberately very long benchmark title for marquee validation.wav"
        4 -> "Audio_05_Cover_Medium.mp3"
        27 -> "Audio_28_Cover_Small.mp3"
        29 -> "Audio_30_Cover_Large.mp3"
        else -> "Audio_${(index + 1).toString().padStart(2, '0')}.wav"
    }

    private fun audioTemplate(index: Int): String = when (index) {
        4 -> "fixtures/audio-cover-medium.mp3"
        27 -> "fixtures/audio-cover-small.mp3"
        29 -> "fixtures/audio-cover-large.mp3"
        else -> "fixtures/audio-template.wav"
    }

    private fun videoFilename(index: Int): String = when (index) {
        0 -> "Video_%_Literal.mp4"
        1 -> "Video_underscore_.mp4"
        2 -> "Video_Ünicode_你好.mp4"
        else -> "Video_${(index + 1).toString().padStart(2, '0')}.mp4"
    }

    private fun videoThumbnail(
        index: Int,
        artwork: List<File>,
        videoFrameDecoderIndexes: Set<Int>
    ): File? = if (index in videoFrameDecoderIndexes) null else artwork[index % artwork.size]

    private fun File.setDeterministicTimestamp(value: Long) {
        check(setLastModified(value) && lastModified() == value) {
            "Could not set deterministic timestamp on $this"
        }
    }

    private companion object {
        const val FIXTURE_DIRECTORY = "benchmark-fixtures"
        const val AUDIO_DIRECTORY = "audio"
        const val VIDEO_DIRECTORY = "video"
        const val AUDIO_COUNT = 30
        const val VIDEO_COUNT = 48
        const val PLAYLIST_ITEM_COUNT = 20
        const val FIXED_TIMESTAMP_MS = 1_700_000_000_000L
        const val VIDEO_TIMESTAMP_OFFSET = 1_000L
        const val VIDEO_TEMPLATE_TIMESTAMP_OFFSET = 900L
        const val ARTWORK_TIMESTAMP_OFFSET = 800L
        const val VIDEO_WIDTH = 320
        const val VIDEO_HEIGHT = 180
        const val FIXTURE_MANIFEST = "fixtures/fixture-manifest.json"
        const val MARKER_VIDEO_FILENAME = "Video_05.mp4"
        const val COIL_CACHE_DIRECTORY = "image_cache"
    }
}

private data class BenchmarkSeedOptions(
    val marqueeEnabled: Boolean,
    val repeatEnabled: Boolean,
    val repeatOne: Boolean,
    val videoMarkersEnabled: Boolean,
    val imageCacheState: String
)

private data class BenchmarkState(
    val sha256: String,
    val summary: JSONObject
)

private data class BenchmarkRuntimeState(
    val cacheState: String,
    val preloadedEntries: Int,
    val memoryCacheEntries: Int,
    val diskCacheBytes: Long,
    val decoderSummary: JSONObject
) {
    companion object {
        fun cold(): BenchmarkRuntimeState = BenchmarkRuntimeState(
            cacheState = BenchmarkSetupContract.CACHE_STATE_COLD,
            preloadedEntries = 0,
            memoryCacheEntries = 0,
            diskCacheBytes = 0L,
            decoderSummary = JSONObject().apply {
                put(BenchmarkSetupContract.DECODER_ARTWORK_URI, 0)
                put(BenchmarkSetupContract.DECODER_VIDEO_FRAME, 0)
            }
        )
    }
}

private data class PreviewCacheTarget(
    val model: String,
    val cacheKey: String,
    val decoderPath: String
)

private data class FixtureAssetContract(
    val templates: Map<String, FixtureTemplateMetadata>,
    val videoFrameDecoderIndexes: Set<Int>
) {
    fun durationMs(name: String): Long = templates[name]?.durationMs
        ?: error("Fixture template '$name' has no duration contract")
}

private data class FixtureTemplateMetadata(
    val name: String,
    val id: String,
    val mediaType: String,
    val mimeType: String,
    val byteSize: Long,
    val durationMs: Long?,
    val durationToleranceMs: Long,
    val width: Int?,
    val height: Int?,
    val embeddedArtworkWidth: Int?,
    val embeddedArtworkHeight: Int?,
    val videoCodecMime: String?,
    val audioCodecMime: String?
) {
    companion object {
        fun fromJson(name: String, json: JSONObject): FixtureTemplateMetadata =
            FixtureTemplateMetadata(
                name = name,
                id = json.getString("id").also { check(it.isNotBlank()) },
                mediaType = json.getString("mediaType"),
                mimeType = json.getString("mimeType"),
                byteSize = json.getLong("byteSize"),
                durationMs = json.optLong("durationMs").takeIf { json.has("durationMs") },
                durationToleranceMs = json.optLong("durationToleranceMs", 0L),
                width = json.optInt("width").takeIf { json.has("width") },
                height = json.optInt("height").takeIf { json.has("height") },
                embeddedArtworkWidth = json.optInt("embeddedArtworkWidth")
                    .takeIf { json.has("embeddedArtworkWidth") },
                embeddedArtworkHeight = json.optInt("embeddedArtworkHeight")
                    .takeIf { json.has("embeddedArtworkHeight") },
                videoCodecMime = json.optString("videoCodecMime").takeIf { it.isNotBlank() },
                audioCodecMime = json.optString("audioCodecMime").takeIf { it.isNotBlank() }
            ).also { contract ->
                check(contract.byteSize > 0L)
                check(contract.mimeType.isNotBlank())
                if (contract.durationMs != null) {
                    check(contract.durationMs > 0L && contract.durationToleranceMs in 0L..50L)
                }
            }
    }
}
