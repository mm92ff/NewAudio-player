package com.example.newaudio.domain.usecase.file

import com.example.newaudio.data.database.DirectSubFolderSongCount
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongMinimal
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.repository.IFolderOrderRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import java.util.Collections
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class) // FIX: Annotation for flatMapLatest
@Singleton
class GetSortedFileTreeUseCase @Inject constructor(
    private val songDao: SongDao,
    private val folderOrderRepository: IFolderOrderRepository,
    private val getUserSettingsUseCase: GetUserSettingsUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private data class FolderInfo(
        val path: String,
        val songCount: Int?
    )

    private class LruCache<K, V>(private val maxSize: Int) :
        LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    private val cacheScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val cache = Collections.synchronizedMap(LruCache<String, StateFlow<List<FileItem>>>(50))

    fun peekCached(path: String): List<FileItem>? =
        cache[normalize(path)]?.value

    operator fun invoke(path: String): Flow<List<FileItem>> {
        val targetPath = normalize(path)

        Timber.tag(TAG).d("invoke(): %s", targetPath)

        return synchronized(cache) {
            cache[targetPath] ?: run {
                Timber.tag(TAG).d("cache miss -> building shared flow for: $targetPath")
                buildFlow(targetPath)
                    .stateIn(
                        scope = cacheScope,
                        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 30_000),
                        initialValue = emptyList(),
                    ).also { cache[targetPath] = it }
            }
        }
    }

    private fun buildFlow(targetPath: String): Flow<List<FileItem>> {
        val songsFlow: Flow<List<SongMinimal>> = songDao.observeSongsInFolderMinimal(targetPath)

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
                        // Recursive count
                        songDao.observeAllSubFolderSongCounts(targetPath)
                            .map { list: List<DirectSubFolderSongCount> ->
                                val aggregatedCounts = HashMap<String, Int>()

                                for (item in list) {
                                    if (!item.path.startsWith(targetPath)) continue

                                    val relativePath = item.path.removePrefix(targetPath).trimStart('/')
                                    if (relativePath.isEmpty()) continue

                                    val directChildName = relativePath.substringBefore('/')

                                    // FIX: Instead of getOrDefault (API 24+) use Kotlin standard (API 1+)
                                    val currentCount = aggregatedCounts[directChildName] ?: 0
                                    aggregatedCounts[directChildName] = currentCount + item.songCount
                                }

                                aggregatedCounts.map { (name, count) ->
                                    val fullPath = if (targetPath.endsWith('/')) "$targetPath$name" else "$targetPath/$name"
                                    FolderInfo(path = fullPath, songCount = count)
                                }
                            }
                    } else {
                        songDao.observeSubFolders(targetPath)
                            .map { paths -> paths.map { FolderInfo(path = it, songCount = null) } }
                    }

                combine(
                    songsFlow,
                    subFolderInfoFlow,
                    orderMapFlow
                ) { songs, subFoldersInfo, orderMap ->
                    Timber.tag(TAG).d("Re-computing file list for $targetPath")

                    val out = ArrayList<FileItem>(subFoldersInfo.size + songs.size)

                    // Folders
                    for (info in subFoldersInfo) {
                        val name = info.path.substringAfterLast('/')
                        if (!settings.showHiddenFiles && name.startsWith(".")) continue
                        out.add(FileItem.Folder(name = name, path = info.path, songCount = info.songCount))
                    }

                    // Audio
                    for (s in songs) {
                        val name = s.filename
                        if (!settings.showHiddenFiles && name.startsWith(".")) continue

                        val songId = try {
                            s.contentUri.substringAfterLast('/').toLong()
                        } catch (_: NumberFormatException) {
                            0L
                        }

                        val song = Song(
                            path = s.path,
                            contentUri = s.contentUri,
                            title = s.title,
                            artist = s.artist,
                            duration = s.duration,
                            albumArtPath = s.albumArtPath
                        )

                        out.add(
                            FileItem.AudioFile(
                                name = name,
                                path = s.path,
                                songId = songId,
                                song = song
                            )
                        )
                    }

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

    private fun normalize(path: String): String = path.trimEnd('/')
}