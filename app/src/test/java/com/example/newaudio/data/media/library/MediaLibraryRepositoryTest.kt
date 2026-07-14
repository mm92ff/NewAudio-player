package com.example.newaudio.data.media.library

import androidx.media3.common.MediaItem
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.SongEntity
import com.example.newaudio.data.database.SongMinimal
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.data.database.VideoEntity
import com.example.newaudio.data.database.VideoMinimal
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.domain.repository.IMediaScannerRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaLibraryRepositoryTest {
    @Test
    fun `missing song is scanned once and queried again`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val songDao = mockk<SongDao>()
        val scanner = mockk<IMediaScannerRepository>(relaxed = true)
        val path = "/music/song.mp3"
        coEvery { songDao.getSongByPath(path) } returnsMany listOf(null, entity(path))
        val repository = repository(songDao, scanner, dispatcher)

        val parent = repository.ensureSongAndGetParentPath(path)

        assertEquals("/music", parent)
        coVerify(exactly = 1) { scanner.scanSingleFile(path) }
        coVerify(exactly = 2) { songDao.getSongByPath(path) }
    }

    @Test
    fun `folder query keeps content uri and applies blank title fallback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val songDao = mockk<SongDao>()
        every { songDao.observeSongsInFolderMinimal("/music") } returns flowOf(
            listOf(
                SongMinimal(
                    path = "/music/fallback.mp3",
                    contentUri = "content://songs/1",
                    title = "",
                    artist = "Artist",
                    duration = 99L,
                    albumArtPath = null,
                    parentPath = "/music",
                    filename = "fallback.mp3"
                )
            )
        )
        val repository = repository(songDao, mockk(relaxed = true), dispatcher)

        val songs = repository.getSongsInFolder("/music")

        assertEquals("fallback", songs.single().title)
        assertEquals("content://songs/1", songs.single().contentUri)
    }

    @Test
    fun `counts and database clear delegate on repository boundary`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val songDao = mockk<SongDao>(relaxed = true)
        val videoDao = mockk<VideoDao>(relaxed = true)
        val database = mockk<AppDatabase>(relaxed = true)
        coEvery { songDao.countAllSongs() } returns 4
        coEvery { videoDao.countAllVideos() } returns 2
        val repository = repository(
            songDao,
            mockk(relaxed = true),
            dispatcher,
            videoDao,
            database
        )

        assertEquals(4, repository.getSongCount())
        assertEquals(2, repository.getVideoCount())
        repository.clearDatabase()

        coVerify(exactly = 1) { database.clearAllTables() }
    }

    @Test
    fun `database video classification wins over audio-looking extension`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val path = "/media/ambiguous.mp3"
        val songDao = mockk<SongDao>(relaxed = true)
        val videoDao = mockk<VideoDao>(relaxed = true)
        coEvery { videoDao.getVideoByPath(path) } returns videoEntity(path)
        val repository = repository(
            songDao,
            mockk(relaxed = true),
            dispatcher,
            videoDao
        )

        val type = repository.resolveMediaType(MediaItem.Builder().setMediaId(path).build())

        assertEquals(Media3ItemMapper.MediaType.VIDEO, type)
    }

    @Test
    fun `video folder query preserves dimensions and title fallback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val videoDao = mockk<VideoDao>()
        every { videoDao.observeVideosInFolderMinimal("/video") } returns flowOf(
            listOf(
                VideoMinimal(
                    path = "/video/fallback.mp4",
                    contentUri = "content://videos/1",
                    title = "",
                    duration = 200L,
                    thumbnailUri = "content://thumb/1",
                    parentPath = "/video",
                    filename = "fallback.mp4",
                    width = 1920,
                    height = 1080
                )
            )
        )
        val repository = repository(
            mockk(relaxed = true),
            mockk(relaxed = true),
            dispatcher,
            videoDao
        )

        val video = repository.getVideosInFolder("/video").single()

        assertEquals("fallback", video.title)
        assertEquals(1920, video.width)
        assertEquals(1080, video.height)
    }

    private fun repository(
        songDao: SongDao,
        scanner: IMediaScannerRepository,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        videoDao: VideoDao = mockk(relaxed = true),
        appDatabase: AppDatabase = mockk(relaxed = true)
    ) = MediaLibraryRepository(
        mediaScannerRepository = scanner,
        songDao = songDao,
        videoDao = videoDao,
        appDatabase = appDatabase,
        itemMapper = Media3ItemMapper(),
        ioDispatcher = dispatcher
    )

    private fun entity(path: String) = SongEntity(
        path = path,
        contentUri = "content://song",
        title = "Song",
        artist = "Artist",
        album = "Album",
        duration = 1L,
        albumArtPath = null,
        parentPath = "/music",
        filename = "song.mp3",
        lastModified = 1L,
        size = 1L
    )

    private fun videoEntity(path: String) = VideoEntity(
        path = path,
        contentUri = "content://video",
        title = "Video",
        duration = 1L,
        thumbnailUri = null,
        parentPath = "/media",
        filename = "ambiguous.mp3",
        lastModified = 1L,
        size = 1L
    )
}
