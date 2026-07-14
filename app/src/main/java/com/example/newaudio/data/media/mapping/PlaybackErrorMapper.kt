package com.example.newaudio.data.media.mapping

import android.content.Context
import androidx.media3.common.PlaybackException
import com.example.newaudio.R
import com.example.newaudio.domain.repository.IMediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Converts Media3 errors into stable, localized repository errors. */
class PlaybackErrorMapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun map(error: PlaybackException): IMediaRepository.PlayerError {
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                context.getString(R.string.error_file_not_found)
            }

            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                context.getString(R.string.error_network)
            }

            else -> error.message?.takeIf(String::isNotBlank)
                ?: context.getString(R.string.unknown_error)
        }
        return IMediaRepository.PlayerError(error.errorCode, message)
    }
}
