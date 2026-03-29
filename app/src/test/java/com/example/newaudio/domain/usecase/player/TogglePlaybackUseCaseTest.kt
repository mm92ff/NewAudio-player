package com.example.newaudio.domain.usecase.player

import com.example.newaudio.fake.FakeMediaRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TogglePlaybackUseCaseTest {

    private val repo = FakeMediaRepository()
    private val useCase = TogglePlaybackUseCase(repo)

    @Test
    fun `invoke delegates to repository togglePlayback`() = runTest {
        useCase()
        assertEquals(1, repo.togglePlaybackCalled)
    }

    @Test
    fun `invoke called twice toggles twice`() = runTest {
        useCase()
        useCase()
        assertEquals(2, repo.togglePlaybackCalled)
    }
}
