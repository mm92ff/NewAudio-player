package com.example.newaudio.data.repository

import android.content.Context
import androidx.media3.session.MediaController
import com.example.newaudio.data.database.AppDatabase
import com.example.newaudio.data.database.SongDao
import com.example.newaudio.data.database.VideoDao
import com.example.newaudio.domain.model.Video
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.fake.FakeMediaScannerRepository
import com.example.newaudio.fake.FakeSettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MediaRepositoryImplTest {

    @Test
    fun `skipNext wraps last video to first media item`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = mockk<MediaController>(relaxed = true) {
            every { mediaItemCount } returns 3
            every { currentMediaItemIndex } returns 2
        }
        val repo = buildRepository(dispatcher).withInitializedController(controller)
        repo.setPlaybackState(IMediaRepository.PlaybackState(currentVideo = video("/video/last.mp4")))

        repo.skipNext()

        verify(exactly = 1) { controller.seekTo(0, 0L) }
        verify(exactly = 0) { controller.seekToNextMediaItem() }
    }

    @Test
    fun `skipPrevious wraps first video to last media item`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = mockk<MediaController>(relaxed = true) {
            every { mediaItemCount } returns 3
            every { currentMediaItemIndex } returns 0
        }
        val repo = buildRepository(dispatcher).withInitializedController(controller)
        repo.setPlaybackState(IMediaRepository.PlaybackState(currentVideo = video("/video/first.mp4")))

        repo.skipPrevious()

        verify(exactly = 1) { controller.seekTo(2, 0L) }
        verify(exactly = 0) { controller.seekToPreviousMediaItem() }
    }

    @Test
    fun `skipNext delegates to regular player navigation when no video is active`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = mockk<MediaController>(relaxed = true) {
            every { mediaItemCount } returns 3
            every { currentMediaItemIndex } returns 2
        }
        val repo = buildRepository(dispatcher).withInitializedController(controller)
        repo.setPlaybackState(IMediaRepository.PlaybackState(currentVideo = null))

        repo.skipNext()

        verify(exactly = 1) { controller.seekToNextMediaItem() }
        verify(exactly = 0) { controller.seekTo(any<Int>(), any<Long>()) }
    }

    private fun buildRepository(dispatcher: kotlinx.coroutines.CoroutineDispatcher): MediaRepositoryImpl {
        return MediaRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            settingsRepository = FakeSettingsRepository(),
            mediaScannerRepository = FakeMediaScannerRepository(),
            songDao = mockk<SongDao>(relaxed = true),
            videoDao = mockk<VideoDao>(relaxed = true),
            appDatabase = mockk<AppDatabase>(relaxed = true),
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher
        )
    }

    private fun MediaRepositoryImpl.withInitializedController(controller: MediaController): MediaRepositoryImpl {
        setPrivateField("mediaController", controller)
        setPrivateField("initStarted", true)
        setPrivateField("initDone", CompletableDeferred<Unit>().apply { complete(Unit) })
        return this
    }

    @Suppress("UNCHECKED_CAST")
    private fun MediaRepositoryImpl.setPlaybackState(state: IMediaRepository.PlaybackState) {
        val field = javaClass.getDeclaredField("_playbackState").apply { isAccessible = true }
        val flow = field.get(this) as MutableStateFlow<IMediaRepository.PlaybackState>
        flow.value = state
    }

    private fun Any.setPrivateField(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }

    private fun video(path: String): Video {
        return Video(
            path = path,
            contentUri = "content://video/${path.hashCode()}",
            title = path.substringAfterLast('/').substringBeforeLast('.'),
            duration = 1_000L,
            thumbnailUri = null,
            width = 1920,
            height = 1080
        )
    }
}
