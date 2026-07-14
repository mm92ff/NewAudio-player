package com.example.newaudio.data.media.mapping

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.newaudio.data.media.library.MediaLibraryRepository
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class Media3PlaybackStateSynchronizerTest {
    @Test
    fun `empty controller cannot synchronize and keeps restoring state`() = runTest {
        val player = mockk<Player>(relaxed = true) {
            every { currentMediaItem } returns null
        }
        val stateStore = PlaybackStateStore()

        val synchronized = Media3PlaybackStateSynchronizer(
            mockk(relaxed = true),
            Media3ItemMapper(),
            stateStore,
            PlaybackQueueState()
        ).synchronize(player)

        assertFalse(synchronized)
        assertEquals(true, stateStore.value.isRestoring)
    }

    @Test
    fun `audio controller state restores queue player flags and active song`() = runTest {
        val items = listOf(item("/music/a.mp3"), item("/music/b.mp3"))
        val songs = listOf(song("a"), song("b"))
        val library = mockk<MediaLibraryRepository>()
        coEvery { library.resolveMediaType(items[1]) } returns Media3ItemMapper.MediaType.AUDIO
        coEvery { library.mapSongs(items) } returns songs
        val player = player(items, currentIndex = 1, isPlaying = true)
        val stateStore = PlaybackStateStore()
        val queueState = PlaybackQueueState()

        val synchronized = Media3PlaybackStateSynchronizer(
            library,
            Media3ItemMapper(),
            stateStore,
            queueState
        ).synchronize(player)

        assertEquals(true, synchronized)
        assertEquals(songs[1], stateStore.value.currentSong)
        assertNull(stateStore.value.currentVideo)
        assertEquals(55L, stateStore.value.currentPosition)
        assertEquals(500L, stateStore.value.totalDuration)
        assertFalse(stateStore.value.isRestoring)
        assertEquals(songs, queueState.snapshot().songs)
    }

    @Test
    fun `video restoration keeps song mutually exclusive`() = runTest {
        val item = item("/video/a.mp4")
        val video = Video("/video/a.mp4", "content://a", "A", 700L, null)
        val library = mockk<MediaLibraryRepository>()
        coEvery { library.resolveMediaType(item) } returns Media3ItemMapper.MediaType.VIDEO
        coEvery { library.mapVideos(listOf(item)) } returns listOf(video)
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = song("old")) } }
        val queueState = PlaybackQueueState()

        Media3PlaybackStateSynchronizer(library, Media3ItemMapper(), stateStore, queueState)
            .synchronize(player(listOf(item), currentIndex = 0, isPlaying = false))

        assertNull(stateStore.value.currentSong)
        assertEquals(video, stateStore.value.currentVideo)
        assertEquals(listOf(video), queueState.snapshot().videos)
    }

    private fun player(items: List<MediaItem>, currentIndex: Int, isPlaying: Boolean): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns items[currentIndex]
        every { player.mediaItemCount } returns items.size
        every { player.currentMediaItemIndex } returns currentIndex
        every { player.getMediaItemAt(any()) } answers { items[firstArg()] }
        every { player.currentPosition } returns 55L
        every { player.duration } returns 500L
        every { player.isPlaying } returns isPlaying
        every { player.shuffleModeEnabled } returns true
        every { player.repeatMode } returns Player.REPEAT_MODE_ALL
        return player
    }

    private fun item(path: String) = MediaItem.Builder().setMediaId(path).build()

    private fun song(name: String) = Song(
        "/music/$name.mp3", "content://$name", name, "Artist", 1_000L, null
    )
}
