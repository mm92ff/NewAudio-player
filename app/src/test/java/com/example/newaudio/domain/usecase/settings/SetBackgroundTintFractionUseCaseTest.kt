package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SetBackgroundTintFractionUseCaseTest {

    private val repo = FakeSettingsRepository()
    private val useCase = SetBackgroundTintFractionUseCase(repo)

    @Test
    fun `invoke sets tint fraction to zero`() = runTest {
        useCase(0f)
        assertEquals(0f, repo.userPreferences.first().backgroundTintFraction)
    }

    @Test
    fun `invoke sets tint fraction to max`() = runTest {
        useCase(0.20f)
        assertEquals(0.20f, repo.userPreferences.first().backgroundTintFraction)
    }

    @Test
    fun `invoke sets arbitrary tint fraction`() = runTest {
        useCase(0.10f)
        assertEquals(0.10f, repo.userPreferences.first().backgroundTintFraction)
    }
}
