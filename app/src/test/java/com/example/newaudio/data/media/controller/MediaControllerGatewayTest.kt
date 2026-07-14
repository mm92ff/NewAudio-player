package com.example.newaudio.data.media.controller

import androidx.media3.session.MediaController
import com.example.newaudio.data.audio.PlayerListenerDelegate
import com.example.newaudio.data.media.mapping.Media3PlaybackStateSynchronizer
import com.example.newaudio.data.media.playback.PlaybackStateStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaControllerGatewayTest {
    @Test
    fun `parallel initializers share one build and all await readiness`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val releaseFactory = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val controller = mockk<MediaController>(relaxed = true)
        val fixture = fixture(
            dispatcher,
            object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    calls.incrementAndGet()
                    releaseFactory.await()
                    return controller
                }
            },
            controller
        )

        val first = async { fixture.gateway.initialize() }
        val second = async { fixture.gateway.initialize() }
        runCurrent()

        assertEquals(1, calls.get())
        assertFalse(first.isCompleted)
        assertFalse(second.isCompleted)

        releaseFactory.complete(Unit)
        advanceUntilIdle()

        first.await()
        second.await()
        assertEquals(1, calls.get())
        verify(exactly = 1) { controller.addListener(fixture.listener) }
    }

    @Test
    fun `cancelling first caller does not cancel shared initialization`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val releaseFactory = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val controller = mockk<MediaController>(relaxed = true)
        val fixture = fixture(
            dispatcher,
            object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    calls.incrementAndGet()
                    releaseFactory.await()
                    return controller
                }
            },
            controller
        )
        val first = launch { fixture.gateway.initialize() }
        runCurrent()

        first.cancelAndJoin()
        val second = async { fixture.gateway.initialize() }
        runCurrent()

        assertEquals(1, calls.get())
        assertFalse(second.isCompleted)

        releaseFactory.complete(Unit)
        advanceUntilIdle()

        second.await()
        verify(exactly = 1) { controller.addListener(fixture.listener) }
    }

    @Test
    fun `failed initialization can be retried and releases partial controller`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstController = mockk<MediaController>(relaxed = true)
        val secondController = mockk<MediaController>(relaxed = true)
        val listenerFactory = mockk<PlayerListenerDelegateFactory>()
        val listener = mockk<PlayerListenerDelegate>(relaxed = true)
        every { listenerFactory.create(any(), any()) } returns listener
        val synchronizer = mockk<Media3PlaybackStateSynchronizer>()
        coEvery { synchronizer.synchronize(firstController) } throws IllegalStateException("sync")
        coEvery { synchronizer.synchronize(secondController) } returns false
        var call = 0
        val gateway = MediaControllerGateway(
            controllerFactory = object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    return if (call++ == 0) firstController else secondController
                }
            },
            listenerFactory = listenerFactory,
            stateSynchronizer = synchronizer,
            stateStore = PlaybackStateStore(),
            mainDispatcher = dispatcher
        )

        try {
            gateway.initialize()
            fail("first initialization should fail")
        } catch (_: IllegalStateException) {
            // Expected.
        }
        gateway.initialize()

        assertEquals(2, call)
        verify { firstController.removeListener(listener) }
        verify { firstController.release() }
        verify(exactly = 1) { secondController.addListener(listener) }
    }

    @Test
    fun `listener registration failure releases controller and is reported`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = mockk<MediaController>(relaxed = true)
        val fixture = fixture(
            dispatcher,
            object : MediaControllerFactory {
                override suspend fun create(): MediaController = controller
            },
            controller
        )
        every { controller.addListener(fixture.listener) } throws IllegalStateException("listener")

        try {
            fixture.gateway.initialize()
            fail("listener failure should be reported")
        } catch (error: IllegalStateException) {
            assertEquals("listener", error.message)
        }

        verify { controller.removeListener(fixture.listener) }
        verify { controller.release() }
    }

    @Test
    fun `controller access propagates coroutine cancellation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = MediaControllerGateway(
            controllerFactory = object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    throw CancellationException("cancelled")
                }
            },
            listenerFactory = mockk(relaxed = true),
            stateSynchronizer = mockk(relaxed = true),
            stateStore = PlaybackStateStore(),
            mainDispatcher = dispatcher
        )

        try {
            gateway.requireController { it }
            fail("CancellationException should be propagated")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }
    }

    @Test
    fun `optional access only suppresses known controller unavailability`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val stateStore = PlaybackStateStore()
        val gateway = MediaControllerGateway(
            controllerFactory = object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    throw MediaControllerUnavailableException(
                        cause = IllegalStateException("service offline")
                    )
                }
            },
            listenerFactory = mockk(relaxed = true),
            stateSynchronizer = mockk(relaxed = true),
            stateStore = stateStore,
            mainDispatcher = dispatcher
        )

        val result = gateway.withControllerOrNull { "not called" }

        assertNull(result)
        assertNotNull(stateStore.value.playerError)
        assertEquals(
            "Playback service is temporarily unavailable",
            stateStore.value.playerError?.message
        )
    }

    @Test
    fun `optional access propagates unexpected listener setup failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = mockk<MediaController>(relaxed = true)
        val fixture = fixture(
            dispatcher,
            object : MediaControllerFactory {
                override suspend fun create(): MediaController = controller
            },
            controller
        )
        every { controller.addListener(fixture.listener) } throws
            IllegalArgumentException("programming error")

        try {
            fixture.gateway.withControllerOrNull { Unit }
            fail("Unexpected setup errors must propagate")
        } catch (error: IllegalArgumentException) {
            assertEquals("programming error", error.message)
        }
    }

    @Test
    fun `optional access propagates unexpected factory failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val gateway = MediaControllerGateway(
            controllerFactory = object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    throw IllegalStateException("factory bug")
                }
            },
            listenerFactory = mockk(relaxed = true),
            stateSynchronizer = mockk(relaxed = true),
            stateStore = PlaybackStateStore(),
            mainDispatcher = dispatcher
        )

        try {
            gateway.withControllerOrNull { Unit }
            fail("Unexpected factory failures must propagate")
        } catch (error: IllegalStateException) {
            assertEquals("factory bug", error.message)
        }
    }

    @Test
    fun `optional access never suppresses an operation failure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = mockk<MediaController>(relaxed = true)
        val fixture = fixture(
            dispatcher,
            object : MediaControllerFactory {
                override suspend fun create(): MediaController = controller
            },
            controller
        )

        try {
            fixture.gateway.withControllerOrNull {
                throw MediaControllerUnavailableException(
                    cause = IllegalStateException("operation failed")
                )
            }
            fail("Operation errors must propagate even if they share the acquisition type")
        } catch (error: MediaControllerUnavailableException) {
            assertEquals(
                "operation failed",
                generateSequence(error as Throwable) { it.cause }.last().message
            )
        }
    }

    @Test
    fun `disconnect clears active controller and next access reconnects once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstController = mockk<MediaController>(relaxed = true)
        val secondController = mockk<MediaController>(relaxed = true)
        val factory = DisconnectableFactory(listOf(firstController, secondController))
        val listenerFactory = mockk<PlayerListenerDelegateFactory>()
        val firstListener = mockk<PlayerListenerDelegate>(relaxed = true)
        val secondListener = mockk<PlayerListenerDelegate>(relaxed = true)
        every { listenerFactory.create(firstController, any()) } returns firstListener
        every { listenerFactory.create(secondController, any()) } returns secondListener
        val synchronizer = mockk<Media3PlaybackStateSynchronizer>()
        coEvery { synchronizer.synchronize(any()) } returns false
        val stateStore = PlaybackStateStore()
        val gateway = MediaControllerGateway(
            factory,
            listenerFactory,
            synchronizer,
            stateStore,
            dispatcher
        )

        assertEquals(firstController, gateway.requireController { it })
        factory.disconnect(0)
        advanceUntilIdle()

        assertNull(stateStore.value.player)
        assertEquals(secondController, gateway.requireController { it })
        assertEquals(2, factory.createCalls)
        verify(exactly = 1) { firstController.removeListener(firstListener) }
        verify(exactly = 1) { firstController.release() }
    }

    @Test
    fun `stale disconnect cannot clear a newer generation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstController = mockk<MediaController>(relaxed = true)
        val secondController = mockk<MediaController>(relaxed = true)
        val factory = DisconnectableFactory(listOf(firstController, secondController))
        val listenerFactory = mockk<PlayerListenerDelegateFactory>()
        every { listenerFactory.create(any(), any()) } returns mockk(relaxed = true)
        val synchronizer = mockk<Media3PlaybackStateSynchronizer>()
        coEvery { synchronizer.synchronize(any()) } returns false
        val gateway = MediaControllerGateway(
            factory,
            listenerFactory,
            synchronizer,
            PlaybackStateStore(),
            dispatcher
        )

        gateway.initialize()
        factory.disconnect(0)
        advanceUntilIdle()
        gateway.initialize()
        factory.disconnect(0)
        advanceUntilIdle()

        assertEquals(secondController, gateway.requireController { it })
        assertEquals(2, factory.createCalls)
        verify(exactly = 0) { secondController.release() }
    }

    @Test
    fun `explicit release is idempotent and allows a later reconnect`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val firstController = mockk<MediaController>(relaxed = true)
        val secondController = mockk<MediaController>(relaxed = true)
        val factory = DisconnectableFactory(listOf(firstController, secondController))
        val listenerFactory = mockk<PlayerListenerDelegateFactory>()
        every { listenerFactory.create(any(), any()) } returns mockk(relaxed = true)
        val synchronizer = mockk<Media3PlaybackStateSynchronizer>()
        coEvery { synchronizer.synchronize(any()) } returns false
        val gateway = MediaControllerGateway(
            factory,
            listenerFactory,
            synchronizer,
            PlaybackStateStore(),
            dispatcher
        )

        gateway.initialize()
        gateway.release()
        gateway.release()
        assertEquals(secondController, gateway.requireController { it })

        verify(exactly = 1) { firstController.release() }
        assertEquals(2, factory.createCalls)
    }

    @Test
    fun `release during initialization waits and tears down the built generation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val releaseFactory = CompletableDeferred<Unit>()
        val controller = mockk<MediaController>(relaxed = true)
        val fixture = fixture(
            dispatcher,
            object : MediaControllerFactory {
                override suspend fun create(): MediaController {
                    releaseFactory.await()
                    return controller
                }
            },
            controller
        )
        val initialize = async { fixture.gateway.initialize() }
        runCurrent()
        val release = async { fixture.gateway.release() }
        runCurrent()

        assertFalse(release.isCompleted)
        releaseFactory.complete(Unit)
        advanceUntilIdle()

        initialize.await()
        release.await()
        verify(exactly = 1) { controller.release() }
        assertNull(fixture.stateStore.value.player)
    }

    private fun fixture(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        factory: MediaControllerFactory,
        controller: MediaController
    ): Fixture {
        val listenerFactory = mockk<PlayerListenerDelegateFactory>()
        val listener = mockk<PlayerListenerDelegate>(relaxed = true)
        every { listenerFactory.create(controller, any()) } returns listener
        val synchronizer = mockk<Media3PlaybackStateSynchronizer>()
        coEvery { synchronizer.synchronize(controller) } returns false
        val stateStore = PlaybackStateStore()
        return Fixture(
            MediaControllerGateway(
                controllerFactory = factory,
                listenerFactory = listenerFactory,
                stateSynchronizer = synchronizer,
                stateStore = stateStore,
                mainDispatcher = dispatcher
            ),
            listener,
            stateStore
        )
    }

    private data class Fixture(
        val gateway: MediaControllerGateway,
        val listener: PlayerListenerDelegate,
        val stateStore: PlaybackStateStore
    )

    private class DisconnectableFactory(
        private val controllers: List<MediaController>
    ) : MediaControllerFactory {
        private val callbacks = mutableListOf<(MediaController) -> Unit>()
        var createCalls = 0
            private set

        override suspend fun create(): MediaController {
            error("Gateway must request the disconnect-aware factory overload")
        }

        override suspend fun create(
            onDisconnected: (MediaController) -> Unit
        ): MediaController {
            val controller = controllers[createCalls++]
            callbacks += onDisconnected
            return controller
        }

        fun disconnect(index: Int) {
            callbacks[index](controllers[index])
        }
    }
}
