package com.example.newaudio.domain.usecase.player

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IMediaRepository
import com.example.newaudio.fake.FakeMediaRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleRepeatModeUseCaseTest {

    private val repo = FakeMediaRepository()
    private val useCase = ToggleRepeatModeUseCase(repo)

    private fun setRepeatMode(mode: Int) {
        repo.setState(IMediaRepository.PlaybackState(repeatMode = mode))
    }

    @Test
    fun `NONE cycles to ALL`() = runTest {
        setRepeatMode(0) // NONE
        useCase()
        assertEquals(UserPreferences.RepeatMode.ALL, repo.setRepeatModeCalled)
    }

    @Test
    fun `ALL cycles to ONE`() = runTest {
        setRepeatMode(2) // ALL
        useCase()
        assertEquals(UserPreferences.RepeatMode.ONE, repo.setRepeatModeCalled)
    }

    @Test
    fun `ONE cycles to NONE`() = runTest {
        setRepeatMode(1) // ONE
        useCase()
        assertEquals(UserPreferences.RepeatMode.NONE, repo.setRepeatModeCalled)
    }

    @Test
    fun `unknown repeat code defaults to NONE then cycles to ALL`() = runTest {
        setRepeatMode(99) // unknown → treated as NONE
        useCase()
        assertEquals(UserPreferences.RepeatMode.ALL, repo.setRepeatModeCalled)
    }

    @Test
    fun `full cycle returns to original mode`() = runTest {
        setRepeatMode(0) // NONE
        useCase() // → ALL
        useCase() // → ONE
        useCase() // → NONE
        assertEquals(UserPreferences.RepeatMode.NONE, repo.setRepeatModeCalled)
    }
}
