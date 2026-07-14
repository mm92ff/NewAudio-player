package com.example.newaudio.data.media.playback

import com.example.newaudio.domain.repository.IMediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateStoreTest {
    @Test
    fun `partial update preserves unrelated state and clear only removes error`() {
        val store = PlaybackStateStore()
        val error = IMediaRepository.PlayerError(7, "failed")
        store.update {
            it.copy(isPlaying = true, currentPosition = 42L, playerError = error)
        }

        store.clearPlayerError()

        assertTrue(store.value.isPlaying)
        assertEquals(42L, store.value.currentPosition)
        assertNull(store.value.playerError)
    }
}
