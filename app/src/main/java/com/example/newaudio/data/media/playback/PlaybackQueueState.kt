package com.example.newaudio.data.media.playback

import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-side queue context shared by repository orchestration and player
 * callbacks. Snapshots prevent callers from mutating lists behind this owner.
 */
@Singleton
class PlaybackQueueState @Inject constructor() {
    data class Snapshot(
        val songs: List<Song> = emptyList(),
        val videos: List<Video> = emptyList(),
        val folderPath: String? = null
    )

    @Volatile
    private var current = Snapshot()

    fun snapshot(): Snapshot = current

    @Synchronized
    fun restore(snapshot: Snapshot) {
        require(snapshot.songs.isEmpty() || snapshot.videos.isEmpty()) {
            "Audio and video queues cannot be active at the same time"
        }
        current = snapshot.copy(
            songs = snapshot.songs.toList(),
            videos = snapshot.videos.toList()
        )
    }

    @Synchronized
    fun setMusic(songs: List<Song>, folderPath: String?) {
        current = Snapshot(songs = songs.toList(), folderPath = folderPath)
    }

    @Synchronized
    fun setVideos(videos: List<Video>, folderPath: String?) {
        current = Snapshot(videos = videos.toList(), folderPath = folderPath)
    }

    @Synchronized
    fun replace(songs: List<Song>, videos: List<Video>, folderPath: String?) {
        require(songs.isEmpty() || videos.isEmpty()) {
            "Audio and video queues cannot be active at the same time"
        }
        current = Snapshot(
            songs = songs.toList(),
            videos = videos.toList(),
            folderPath = folderPath
        )
    }

    @Synchronized
    fun clear() {
        current = Snapshot()
    }
}
