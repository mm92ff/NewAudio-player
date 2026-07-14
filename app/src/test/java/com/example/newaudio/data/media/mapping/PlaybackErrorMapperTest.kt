package com.example.newaudio.data.media.mapping

import android.content.Context
import androidx.media3.common.PlaybackException
import com.example.newaudio.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackErrorMapperTest {
    private val context = mockk<Context> {
        every { getString(R.string.error_file_not_found) } returns "File not found"
        every { getString(R.string.error_network) } returns "Network error"
        every { getString(R.string.unknown_error) } returns "Unknown error"
    }
    private val mapper = PlaybackErrorMapper(context)

    @Test
    fun `file not found is not reported as a network error`() {
        val mapped = mapper.map(error(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))

        assertEquals("File not found", mapped.message)
    }

    @Test
    fun `network failures use localized network message`() {
        val mapped = mapper.map(error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))

        assertEquals("Network error", mapped.message)
    }

    @Test
    fun `specific decoder message is preserved`() {
        val mapped = mapper.map(
            error(PlaybackException.ERROR_CODE_DECODING_FAILED, "decoder failed")
        )

        assertEquals("decoder failed", mapped.message)
    }

    @Test
    fun `missing message uses localized unknown error`() {
        val mapped = mapper.map(error(PlaybackException.ERROR_CODE_DECODING_FAILED))

        assertEquals("Unknown error", mapped.message)
    }

    private fun error(code: Int, message: String? = null): PlaybackException {
        return PlaybackException(message, null, code)
    }
}
