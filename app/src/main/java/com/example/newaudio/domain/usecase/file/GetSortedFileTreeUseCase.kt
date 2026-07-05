package com.example.newaudio.domain.usecase.file

import com.example.newaudio.data.database.DirectSubFolderSongCount
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongMinimal
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoMinimal
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.repository.IFolderOrderRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class) // FIX: Annotation for flatMapLatest
@Singleton
class GetSortedFileTreeUseCase @Inject constructor(
    private val songDao: SongDao,
    private val videoDao: VideoDao,
    private val folderOrderRepository: IFolderOrderRepository,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private data class FolderInfo(
        val path: String,
        val mediaCount: Int?
    )

    private class LruCache<K, V>(private val maxSize: Int) :
        LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    private val cacheScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val cache = Collections.synchronizedMap(LruCache<String, StateFlow<List<FileItem>>>(50))
    private val refreshSignals = Collections.synchronizedMap(mutableMapOf<String, MutableStateFlow<Int>>())

    fun peekCached(path: String, mode: MediaBrowserMode = MediaBrowserMode.MUSIC): List<FileItem>? =
        cache[cacheKey(path, mode)]?.value

    fun invalidate(path: String, mode: MediaBrowserMode = MediaBrowserMode.MUSIC) {
        val key = cacheKey(path, mode)
        refreshSignalFor(key).update { it + 1 }
    }

    operator fun invoke(
        path: String,
        mode: MediaBrowserMode = MediaBrowserMode.MUSIC
    ): Flow<List<FileItem>> {
        val targetPath = normalize(path)
        val key = cacheKey(targetPath, mode)

        Timber.tag(TAG).d("invoke(): %s mode=%s", targetPath, mode)

        return synchronized(cache) {
            cache[key] ?: run {
                Timber.tag(TAG).d("cache miss -> building shared flow for: $key")
                buildFlow(targetPath, mode, refreshSignalFor(key))
                    .stateIn(
                        scope = cacheScope,
                        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 30_000),
                        initialValue = emptyList(),
                    ).also { cache[key] = it }
            }
        }
    }

    private fun buildFlow(
        targetPath: String,
        mode: MediaBrowserMode,
        refreshSignal: StateFlow<Int>
    ): Flow<List<FileItem>> {
        val orderMapFlow: Flow<Map<String, Int>?> =
            folderOrderRepository.observeFolderOrder(targetPath)
                .distinctUntilChanged()
                .map { order ->
                    if (order.isNullOrEmpty()) null
                    else {
                        val m = HashMap<String, Int>(order.size * 2)
                        order.forEachIndexed { idx, name -> m[name] = idx }
                        m
                    }
                }
                .distinctUntilChanged()

        val defaultComparator = Comparator<FileItem> { a, b ->
            val aFolder = a is FileItem.Folder
            val bFolder = b is FileItem.Folder
            if (aFolder != bFolder) return@Comparator if (aFolder) -1 else 1
            a.name.compareTo(b.name, ignoreCase = true)
        }

        return getUserSettingsUseCase()
            .distinctUntilChanged()
            .flatMapLatest { settings ->
                val subFolderInfoFlow: Flow<List<FolderInfo>> =
                    if (settings.showFolderSongCount) {
                        when (mode) {
                            MediaBrowserMode.MUSIC -> songDao.observeAllSubFolderSongCounts(targetPath)
                                .map { list: List<DirectSubFolderSongCount> ->
                                    aggregateFolderCounts(targetPath, list) { it.path to it.songCount }
                                }
                            MediaBrowserMode.VIDEO -> videoDao.observeAllSubFolderVideoCounts(targetPath)
                                .map { list ->
                                    aggregateFolderCounts(targetPath, list) { it.path to it.videoCount }
                                }
                        }
                    } else {
                        when (mode) {
                            MediaBrowserMode.MUSIC -> songDao.observeSubFolders(targetPath)
                            MediaBrowserMode.VIDEO -> videoDao.observeSubFolders(targetPath)
                        }.map { paths -> paths.map { FolderInfo(path = it, mediaCount = null) } }
                    }

                val fileSystemFolderInfoFlow: Flow<List<FolderInfo>> =
                    refreshSignal.map { readDirectFileSystemFolders(targetPath) }

                val mediaItemsFlow: Flow<List<FileItem>> = when (mode) {
                    MediaBrowserMode.MUSIC -> songDao.observeSongsInFolderMinimal(targetPath)
                        .map { songs -> songs.mapNotNull { it.toAudioFile(settings.showHiddenFiles) } }
                    MediaBrowserMode.VIDEO -> videoDao.observeVideosInFolderMinimal(targetPath)
                        .map { videos -> videos.mapNotNull { it.toVideoFile(settings.showHiddenFiles) } }
                }

                combine(
                    mediaItemsFlow,
                    subFolderInfoFlow,
                    fileSystemFolderInfoFlow,
                    orderMapFlow
                ) { mediaItems, subFoldersInfo, fileSystemFoldersInfo, orderMap ->
                    Timber.tag(TAG).d("Re-computing file list for $targetPath mode=$mode")

                    val mergedFoldersInfo = mergeFolderInfo(
                        databaseFolders = subFoldersInfo,
                        fileSystemFolders = fileSystemFoldersInfo,
                        showFolderSongCount = settings.showFolderSongCount
                    )
                    val out = ArrayList<FileItem>(mergedFoldersInfo.size + mediaItems.size)

                    // Folders
                    for (info in mergedFoldersInfo) {
                        val name = info.path.substringAfterLast('/')
                        if (!settings.showHiddenFiles && name.startsWith(".")) continue
                        out.add(FileItem.Folder(name = name, path = info.path, mediaCount = info.mediaCount))
                    }

                    out.addAll(mediaItems)

                    // Sort in-place
                    if (orderMap == null) {
                        out.sortWith(defaultComparator)
                    } else {
                        val orderedComparator = Comparator<FileItem> { a, b ->
                            val ai = orderMap[a.name] ?: Int.MAX_VALUE
                            val bi = orderMap[b.name] ?: Int.MAX_VALUE
                            if (ai != bi) return@Comparator ai - bi

                            val aFolder = a is FileItem.Folder
                            val bFolder = b is FileItem.Folder
                            if (aFolder != bFolder) return@Comparator if (aFolder) -1 else 1

                            a.name.compareTo(b.name, ignoreCase = true)
                        }
                        out.sortWith(orderedComparator)
                    }

                    out
                }
            }
            .onStart {
                val jobId = currentCoroutineContext()[Job]?.hashCode() ?: 0
                Timber.tag(TAG).d("Observing reactively: $targetPath (job=$jobId)")
            }
            .distinctUntilChanged()
            .flowOn(dispatcher)
    }

    private companion object {
        private const val TAG = "GetSortedFileTree"
    }

    private fun normalize(path: String): String = path.toBrowserPath().trimEnd('/')

    private fun cacheKey(path: String, mode: MediaBrowserMode): String = "${mode.name}:${normalize(path)}"

    private fun refreshSignalFor(key: String): MutableStateFlow<Int> =
        synchronized(refreshSignals) {
            refreshSignals.getOrPut(key) { MutableStateFlow(0) }
        }

    private fun readDirectFileSystemFolders(targetPath: String): List<FolderInfo> {
        val children = runCatching {
            File(targetPath).listFiles { file -> file.isDirectory }
        }.getOrNull() ?: return emptyList()

        return children.map { child ->
            FolderInfo(path = normalize(child.path), mediaCount = null)
        }
    }

    private fun mergeFolderInfo(
        databaseFolders: List<FolderInfo>,
        fileSystemFolders: List<FolderInfo>,
        showFolderSongCount: Boolean
    ): List<FolderInfo> {
        val foldersByPath = LinkedHashMap<String, FolderInfo>(databaseFolders.size + fileSystemFolders.size)

        fileSystemFolders.forEach { info ->
            foldersByPath[normalize(info.path)] = FolderInfo(path = normalize(info.path), mediaCount = null)
        }

        databaseFolders.forEach { info ->
            val normalizedPath = normalize(info.path)
            foldersByPath[normalizedPath] = FolderInfo(
                path = normalizedPath,
                mediaCount = if (showFolderSongCount) info.mediaCount else null
            )
        }

        return foldersByPath.values.toList()
    }

    private fun String.toBrowserPath(): String = replace('\\', '/')

    private fun <T> aggregateFolderCounts(
        targetPath: String,
        list: List<T>,
        mapper: (T) -> Pair<String, Int>
    ): List<FolderInfo> {
        val aggregatedCounts = HashMap<String, Int>()

        for (item in list) {
            val (path, count) = mapper(item)
            if (!path.startsWith(targetPath)) continue

            val relativePath = path.removePrefix(targetPath).trimStart('/')
            if (relativePath.isEmpty()) continue

            val directChildName = relativePath.substringBefore('/')
            val currentCount = aggregatedCounts[directChildName] ?: 0
            aggregatedCounts[directChildName] = currentCount + count
        }

        return aggregatedCounts.map { (name, count) ->
            val fullPath = if (targetPath.endsWith('/')) "$targetPath$name" else "$targetPath/$name"
            FolderInfo(path = fullPath, mediaCount = count)
        }
    }

    private fun SongMinimal.toAudioFile(showHiddenFiles: Boolean): FileItem.AudioFile? {
        val name = filename
        if (!showHiddenFiles && name.startsWith(".")) return null

        val songId = contentUri.substringAfterLast('/').toLongOrNull() ?: 0L
        val song = Song(
            path = path,
            contentUri = contentUri,
            title = title.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension.ifBlank { name },
            artist = artist,
            duration = duration,
            albumArtPath = albumArtPath
        )

        return FileItem.AudioFile(
            name = name,
            path = path,
            songId = songId,
            song = song
        )
    }

    private fun VideoMinimal.toVideoFile(showHiddenFiles: Boolean): FileItem.VideoFile? {
        val name = filename
        if (!showHiddenFiles && name.startsWith(".")) return null

        val videoId = contentUri.substringAfterLast('/').toLongOrNull() ?: 0L
        val video = Video(
            path = path,
            contentUri = contentUri,
            title = title.takeIf { it.isNotBlank() } ?: File(path).nameWithoutExtension.ifBlank { name },
            duration = duration,
            thumbnailUri = thumbnailUri,
            width = width,
            height = height
        )

        return FileItem.VideoFile(
            name = name,
            path = path,
            videoId = videoId,
            video = video
        )
    }
}
