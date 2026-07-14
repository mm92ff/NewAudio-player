package com.example.newaudio.data.media.deletion

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.domain.model.Song
import com.example.newaudio.domain.model.Video
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DeletedMediaReconcilerTest {
    @Test
    fun `deleting current middle item selects its successor and keeps pause state`() {
        val songs = mutableListOf(song("a"), song("b"), song("c"))
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentSong = songs[1], isPlaying = false) }
        }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 1, isPlaying = false)
        val reconciler = DeletedMediaReconciler(
            stateStore,
            queueState,
            Media3ItemMapper(),
            DeletedMediaDecisionCalculator()
        )

        reconciler.reconcile(listOf(songs[1].path), fixture.player)

        assertEquals(listOf(songs[0], songs[2]), queueState.snapshot().songs)
        assertEquals(songs[2], stateStore.value.currentSong)
        assertEquals(0L, stateStore.value.currentPosition)
        assertFalse(stateStore.value.isPlaying)
        verify { fixture.player.seekTo(1, 0L) }
        verify { fixture.player.setPlayWhenReady(false) }
    }

    @Test
    fun `folder path does not delete prefix sibling`() {
        val sibling = Song("/music/AB/a.mp3", "content://a", "A", "Artist", 1L, null)
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = sibling) } }
        val queueState = PlaybackQueueState().apply { setMusic(listOf(sibling), "/music/AB") }
        val fixture = player(listOf(sibling.path), currentIndex = 0, isPlaying = false)

        reconciler(stateStore, queueState)
            .reconcile(listOf("/music/A"), fixture.player)

        assertEquals(listOf(sibling), queueState.snapshot().songs)
        verify(exactly = 0) { fixture.player.removeMediaItem(any()) }
    }

    @Test
    fun `deleting complete queue clears playback state`() {
        val songs = listOf(song("a"), song("b"))
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentSong = songs[0], isPlaying = true, currentPosition = 10L) }
        }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 0, isPlaying = true)

        reconciler(stateStore, queueState)
            .reconcile(listOf("/music"), fixture.player)

        assertNull(stateStore.value.currentSong)
        assertFalse(stateStore.value.isPlaying)
        assertEquals(0L, stateStore.value.currentPosition)
        assertEquals(emptyList<Song>(), queueState.snapshot().songs)
        verify { fixture.player.stop() }
        verify { fixture.player.clearMediaItems() }
    }

    @Test
    fun `deleting item before current keeps current song and shifts queue only`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentSong = songs[2], isPlaying = true) }
        }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 2, isPlaying = true)

        reconciler(stateStore, queueState)
            .reconcile(listOf(songs[0].path), fixture.player)

        assertEquals(listOf(songs[1], songs[2]), queueState.snapshot().songs)
        assertEquals(songs[2], stateStore.value.currentSong)
        verify(exactly = 0) { fixture.player.seekTo(any<Int>(), any<Long>()) }
    }

    @Test
    fun `deleting current last item selects previous remaining item`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentSong = songs[2], isPlaying = true) }
        }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 2, isPlaying = true)

        reconciler(stateStore, queueState)
            .reconcile(listOf(songs[2].path), fixture.player)

        assertEquals(songs[1], stateStore.value.currentSong)
        verify { fixture.player.seekTo(1, 0L) }
        verify { fixture.player.setPlayWhenReady(true) }
    }

    @Test
    fun `video deletion keeps video mode and playing state`() {
        val videos = listOf(video("a"), video("b"))
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentVideo = videos[0], isPlaying = true) }
        }
        val queueState = PlaybackQueueState().apply { setVideos(videos, "/video") }
        val fixture = player(videos.map { it.path }, currentIndex = 0, isPlaying = true)

        reconciler(stateStore, queueState)
            .reconcile(listOf(videos[0].path), fixture.player)

        assertNull(stateStore.value.currentSong)
        assertEquals(videos[1], stateStore.value.currentVideo)
        assertEquals(listOf(videos[1]), queueState.snapshot().videos)
        verify { fixture.player.setPlayWhenReady(true) }
    }

    @Test
    fun `queue is unchanged when controller contains no matching item`() {
        val songs = listOf(song("a"), song("b"))
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = songs[0]) } }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(listOf(songs[0].path), currentIndex = 0, isPlaying = false)

        reconciler(stateStore, queueState)
            .reconcile(listOf(songs[1].path), fixture.player)

        assertEquals(songs, queueState.snapshot().songs)
        assertEquals(songs[0], stateStore.value.currentSong)
        verify(exactly = 0) { fixture.player.removeMediaItem(any()) }
    }

    @Test
    fun `controller removal failure leaves app state unchanged and attempts rollback`() {
        val songs = listOf(song("a"), song("b"))
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = songs[0]) } }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 0, isPlaying = false)
        every { fixture.player.removeMediaItem(0) } throws IllegalStateException("remove")

        assertThrows(IllegalStateException::class.java) {
            reconciler(stateStore, queueState)
                .reconcile(listOf(songs[0].path), fixture.player)
        }

        assertEquals(songs, queueState.snapshot().songs)
        assertEquals(songs[0], stateStore.value.currentSong)
        verify { fixture.player.setMediaItems(any<List<MediaItem>>(), 0, 0L) }
    }

    @Test
    fun `buffering playback intent survives active deletion`() {
        val songs = listOf(song("a"), song("b"))
        val stateStore = PlaybackStateStore().apply {
            update { it.copy(currentSong = songs[0], isPlaying = false) }
        }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(
            songs.map { it.path },
            currentIndex = 0,
            isPlaying = false,
            playWhenReady = true
        )

        reconciler(stateStore, queueState)
            .reconcile(listOf(songs[0].path), fixture.player)

        assertEquals(songs[1], stateStore.value.currentSong)
        assertEquals(true, stateStore.value.isPlaying)
        verify { fixture.player.setPlayWhenReady(true) }
    }

    @Test
    fun `seek failure rolls back controller and does not publish filtered queue`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = songs[1]) } }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 1, isPlaying = false)
        every { fixture.player.seekTo(1, 0L) } throws IllegalStateException("seek")

        assertThrows(IllegalStateException::class.java) {
            reconciler(stateStore, queueState)
                .reconcile(listOf(songs[1].path), fixture.player)
        }

        assertEquals(songs, queueState.snapshot().songs)
        assertEquals(songs[1], stateStore.value.currentSong)
        verify { fixture.player.setMediaItems(any<List<MediaItem>>(), 1, 0L) }
    }

    @Test
    fun `rollback failure is attached to primary delete error`() {
        val songs = listOf(song("a"), song("b"))
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = songs[0]) } }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 0, isPlaying = false)
        every { fixture.player.removeMediaItem(0) } throws IllegalStateException("remove")
        every {
            fixture.player.setMediaItems(any<List<MediaItem>>(), any(), any())
        } throws IllegalArgumentException("rollback")

        val error = assertThrows(IllegalStateException::class.java) {
            reconciler(stateStore, queueState)
                .reconcile(listOf(songs[0].path), fixture.player)
        }

        assertEquals("rollback", error.suppressed.single().message)
    }

    @Test
    fun `second removal failure compensates an already removed item`() {
        val songs = listOf(song("a"), song("b"), song("c"))
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = songs[1]) } }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 1, isPlaying = false)
        every { fixture.player.removeMediaItem(0) } throws IllegalStateException("second")

        assertThrows(IllegalStateException::class.java) {
            reconciler(stateStore, queueState)
                .reconcile(listOf(songs[0].path, songs[2].path), fixture.player)
        }

        assertEquals(songs, queueState.snapshot().songs)
        assertEquals(songs[1], stateStore.value.currentSong)
        verify { fixture.player.removeMediaItem(2) }
        verify { fixture.player.setMediaItems(any<List<MediaItem>>(), 1, 0L) }
    }

    @Test
    fun `playback intent failure rolls back before publishing app state`() {
        val songs = listOf(song("a"), song("b"))
        val stateStore = PlaybackStateStore().apply { update { it.copy(currentSong = songs[0]) } }
        val queueState = PlaybackQueueState().apply { setMusic(songs, "/music") }
        val fixture = player(songs.map { it.path }, currentIndex = 0, isPlaying = false)
        var intentCalls = 0
        every { fixture.player.setPlayWhenReady(false) } answers {
            intentCalls++
            if (intentCalls == 1) throw IllegalStateException("intent")
        }

        assertThrows(IllegalStateException::class.java) {
            reconciler(stateStore, queueState)
                .reconcile(listOf(songs[0].path), fixture.player)
        }

        assertEquals(songs, queueState.snapshot().songs)
        assertEquals(songs[0], stateStore.value.currentSong)
        verify { fixture.player.setMediaItems(any<List<MediaItem>>(), 0, 0L) }
    }

    private fun player(
        paths: List<String>,
        currentIndex: Int,
        isPlaying: Boolean,
        playWhenReady: Boolean = isPlaying
    ): PlayerFixture {
        val items = paths.map { path -> MediaItem.Builder().setMediaId(path).build() }
            .toMutableList()
        val player = mockk<Player>(relaxed = true)
        every { player.mediaItemCount } answers { items.size }
        every { player.currentMediaItemIndex } returns currentIndex
        every { player.isPlaying } returns isPlaying
        every { player.playWhenReady } returns playWhenReady
        every { player.getMediaItemAt(any()) } answers { items[firstArg()] }
        every { player.removeMediaItem(any()) } answers {
            items.removeAt(firstArg())
            Unit
        }
        return PlayerFixture(player)
    }

    private data class PlayerFixture(val player: Player)

    private fun reconciler(
        stateStore: PlaybackStateStore,
        queueState: PlaybackQueueState
    ) = DeletedMediaReconciler(
        stateStore,
        queueState,
        Media3ItemMapper(),
        DeletedMediaDecisionCalculator()
    )

    private fun song(name: String) = Song(
        "/music/$name.mp3", "content://$name", name, "Artist", 1_000L, null
    )

    private fun video(name: String) = Video(
        "/video/$name.mp4", "content://$name", name, 1_000L, null
    )
}
