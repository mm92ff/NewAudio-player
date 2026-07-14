package com.example.newaudio.data.media.controller

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.newaudio.service.MediaPlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await

internal const val CONTROLLER_UNAVAILABLE_MESSAGE =
    "Playback service is temporarily unavailable"

class MediaControllerUnavailableException(
    message: String = CONTROLLER_UNAVAILABLE_MESSAGE,
    cause: Throwable
) : IllegalStateException(message, cause)

/**
 * Builds Media3 controllers.
 *
 * Implementations may normalize only failures that mean the service is
 * temporarily unreachable. Cancellation, security/policy rejection and
 * programming failures must keep their original type.
 */
interface MediaControllerFactory {
    suspend fun create(): MediaController

    suspend fun create(onDisconnected: (MediaController) -> Unit): MediaController = create()
}

@OptIn(UnstableApi::class)
class AndroidMediaControllerFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaControllerFactory {
    override suspend fun create(): MediaController = create {}

    override suspend fun create(
        onDisconnected: (MediaController) -> Unit
    ): MediaController {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )
        val connection = MediaController.Builder(context, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    onDisconnected(controller)
                }
            })
            .buildAsync()
        return try {
            connection.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            throw error
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            val rootCause = generateSequence(error as Throwable) { it.cause }.last()
            when (rootCause) {
                is SecurityException -> throw rootCause
                is IllegalArgumentException -> throw rootCause
            }
            // Only asynchronous connection failures are normalized. Immediate
            // token/builder/programming failures occur outside this boundary
            // and therefore keep propagating unchanged.
            throw MediaControllerUnavailableException(cause = error)
        }
    }
}
