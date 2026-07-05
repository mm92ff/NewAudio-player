package com.example.newaudio.domain.usecase.file

import androidx.room.Room
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.PlaylistEntity
import com.example.newaudio.data.database.PlaylistSongEntity
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.data.database.VideoPlaylistEntity
import com.example.newaudio.data.database.VideoPlaylistItemEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderDeleteCascadeTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `deleting audio folder cascades playlist song entries`() = runTest {
        val folderPath = "/storage/emulated/0/Music/training"
        val songPath = "$folderPath/track.mp3"
        database.songDao().insert(
            SongEntity(
                path = songPath,
                contentUri = songPath,
                title = "track",
                artist = "Artist",
                album = "Album",
                duration = 1_000L,
                albumArtPath = null,
                parentPath = folderPath,
                filename = "track.mp3",
                lastModified = 0L,
                size = 12L
            )
        )
        val playlistId = database.playlistDao().insertPlaylist(PlaylistEntity(name = "Training"))
        database.playlistDao().insertPlaylistSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songPath = songPath,
                position = 0
            )
        )

        database.songDao().deleteByFolder(folderPath)

        assertTrue(database.playlistDao().getSongsInPlaylistSync(playlistId).isEmpty())
    }

    @Test
    fun `deleting video folder cascades video playlist entries`() = runTest {
        val folderPath = "/storage/emulated/0/Movies/training"
        val videoPath = "$folderPath/clip.mp4"
        database.videoDao().insert(
            VideoEntity(
                path = videoPath,
                contentUri = videoPath,
                title = "clip",
                duration = 1_000L,
                thumbnailUri = null,
                parentPath = folderPath,
                filename = "clip.mp4",
                lastModified = 0L,
                size = 12L,
                width = 1920,
                height = 1080
            )
        )
        val playlistId = database.videoPlaylistDao().insertPlaylist(VideoPlaylistEntity(name = "Training"))
        database.videoPlaylistDao().insertPlaylistVideo(
            VideoPlaylistItemEntity(
                playlistId = playlistId,
                videoPath = videoPath,
                position = 0
            )
        )

        database.videoDao().deleteByFolder(folderPath)

        assertTrue(database.videoPlaylistDao().getVideosInPlaylistSync(playlistId).isEmpty())
    }
}
