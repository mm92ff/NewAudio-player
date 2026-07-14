package com.example.newaudio.data.media.controller

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.example.newaudio.data.audio.PlayerListenerDelegate
import com.example.newaudio.data.media.mapping.Media3PlaybackStateSynchronizer
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.di.MainDispatcher
import com.example.newaudio.domain.repository.IMediaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Owns the process-local Media3 controller connection.
 *
 * Controller creation is single-flight and all controller/listener work runs
 * on [mainDispatcher]. A successful connection has a monotonically increasing
 * generation. Disconnect clears only the matching generation, cancels that
 * listener's child scope and lets the next access reconnect. Callers own their
 * cancellation; cancelling one waiter never cancels the shared build.
 */
@OptIn(UnstableApi::class)
@Singleton
class MediaControllerGateway @Inject constructor(
    private val controllerFactory: MediaControllerFactory,
    private val listenerFactory: PlayerListenerDelegateFactory,
    private val stateSynchronizer: Media3PlaybackStateSynchronizer,
    private val stateStore: PlaybackStateStore,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) {
    private companion object {
        private const val TAG = "MediaControllerGateway"
    }

    private val mutex = Mutex()
    private val gatewayJob = SupervisorJob()
    private val gatewayScope = CoroutineScope(mainDispatcher + gatewayJob)
    private var controller: MediaController? = null
    private var listener: PlayerListenerDelegate? = null
    private var listenerJob: Job? = null
    private var initialization: CompletableDeferred<Unit>? = null
    private var nextGeneration = 0L
    private var activeGeneration = 0L

    suspend fun initialize() {
        val completion = mutex.withLock {
            if (controller != null) return
            initialization?.let { return@withLock it }
            val newCompletion = CompletableDeferred<Unit>()
            val newGeneration = ++nextGeneration
            initialization = newCompletion
            gatewayScope.launch {
                performInitialization(newCompletion, newGeneration)
            }
            newCompletion
        }
        completion.await()
    }

    private suspend fun performInitialization(
        completion: CompletableDeferred<Unit>,
        generation: Long
    ) {
        var createdController: MediaController? = null
        var createdListener: PlayerListenerDelegate? = null
        var createdListenerJob: Job? = null
        try {
            withContext(mainDispatcher) {
                createdController = controllerFactory.create { disconnectedController ->
                    gatewayScope.launch {
                        handleDisconnected(generation, disconnectedController)
                    }
                }
                val activeController = checkNotNull(createdController)
                createdListenerJob = SupervisorJob(gatewayJob)
                val createdListenerScope = CoroutineScope(mainDispatcher + checkNotNull(createdListenerJob))
                createdListener = listenerFactory.create(activeController, createdListenerScope)
                val activeListener = checkNotNull(createdListener)
                activeController.addListener(activeListener)

                if (!stateSynchronizer.synchronize(activeController)) {
                    stateStore.update {
                        it.copy(isRestoring = false, player = activeController, playerError = null)
                    }
                }
            }

            mutex.withLock {
                controller = createdController
                listener = createdListener
                listenerJob = createdListenerJob
                activeGeneration = generation
                if (initialization === completion) initialization = null
            }
            completion.complete(Unit)
        } catch (error: CancellationException) {
            failInitialization(
                completion,
                createdController,
                createdListener,
                createdListenerJob,
                error
            )
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Controller initialization failed")
            failInitialization(
                completion,
                createdController,
                createdListener,
                createdListenerJob,
                error
            )
        }
    }

    /**
     * Runs an operation that requires a controller. Expected connection
     * failures and unexpected setup/programming failures are both propagated;
     * callers can therefore choose an explicit user-facing policy.
     */
    suspend fun <T> requireController(block: suspend (MediaController) -> T): T {
        val activeController = acquireController()
        return withContext(mainDispatcher) { block(activeController) }
    }

    /**
     * Best-effort access for non-essential controls. Only the known temporary
     * connection failure becomes a no-op; cancellations and unexpected errors
     * keep their normal propagation semantics.
     */
    suspend fun <T> withControllerOrNull(block: suspend (MediaController) -> T): T? {
        val activeController = try {
            acquireController()
        } catch (error: MediaControllerUnavailableException) {
            Timber.tag(TAG).w(error, "Controller is temporarily unavailable")
            return null
        }
        return withContext(mainDispatcher) { block(activeController) }
    }

    /** Releases the current generation. A later access may initialize again. */
    suspend fun release() {
        val pendingInitialization = mutex.withLock { initialization }
        if (pendingInitialization != null) {
            try {
                pendingInitialization.await()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Initialization ended before release")
            }
        }
        val resources = mutex.withLock {
            takeActiveConnectionLocked()
        }
        cleanup(resources, primaryError = null)
        stateStore.update { current ->
            current.copy(isPlaying = false, player = null)
        }
    }

    private suspend fun acquireController(): MediaController {
        initialize()
        return checkNotNull(mutex.withLock { controller }) {
            "Controller initialization completed without a controller"
        }
    }

    private suspend fun handleDisconnected(
        generation: Long,
        disconnectedController: MediaController
    ) {
        val resources = mutex.withLock {
            if (activeGeneration != generation || controller !== disconnectedController) {
                return
            }
            takeActiveConnectionLocked()
        }
        cleanup(resources, primaryError = null)
        stateStore.update { current ->
            current.copy(
                isRestoring = false,
                isPlaying = false,
                player = null,
                playerError = IMediaRepository.PlayerError(
                    PlaybackException.ERROR_CODE_UNSPECIFIED,
                    CONTROLLER_UNAVAILABLE_MESSAGE
                )
            )
        }
    }

    private suspend fun failInitialization(
        completion: CompletableDeferred<Unit>,
        createdController: MediaController?,
        createdListener: PlayerListenerDelegate?,
        createdListenerJob: Job?,
        error: Throwable
    ) = withContext(NonCancellable) {
        cleanup(
            ConnectionResources(createdController, createdListener, createdListenerJob),
            error
        )
        stateStore.update { current ->
            current.copy(
                isRestoring = false,
                player = null,
                playerError = if (error is MediaControllerUnavailableException) {
                    IMediaRepository.PlayerError(
                        PlaybackException.ERROR_CODE_UNSPECIFIED,
                        error.message ?: CONTROLLER_UNAVAILABLE_MESSAGE
                    )
                } else {
                    current.playerError
                }
            )
        }
        mutex.withLock {
            if (initialization === completion) initialization = null
            if (controller === createdController) controller = null
            if (listener === createdListener) listener = null
            if (listenerJob === createdListenerJob) listenerJob = null
            if (controller == null) activeGeneration = 0L
        }
        completion.completeExceptionally(error)
    }

    private fun takeActiveConnectionLocked(): ConnectionResources {
        val resources = ConnectionResources(controller, listener, listenerJob)
        controller = null
        listener = null
        listenerJob = null
        activeGeneration = 0L
        return resources
    }

    private suspend fun cleanup(
        resources: ConnectionResources,
        primaryError: Throwable?
    ) = withContext(NonCancellable + mainDispatcher) {
        resources.listenerJob?.cancel()
        val cleanupErrors = buildList {
            if (resources.controller != null && resources.listener != null) {
                runCatching {
                    resources.controller.removeListener(resources.listener)
                }.exceptionOrNull()?.let(::add)
            }
            resources.controller?.let { activeController ->
                runCatching { activeController.release() }.exceptionOrNull()?.let(::add)
            }
        }
        cleanupErrors.forEach { cleanupError ->
            primaryError?.addSuppressed(cleanupError)
            Timber.tag(TAG).e(cleanupError, "Controller cleanup failed")
        }
    }

    private data class ConnectionResources(
        val controller: MediaController?,
        val listener: PlayerListenerDelegate?,
        val listenerJob: Job?
    )
}
