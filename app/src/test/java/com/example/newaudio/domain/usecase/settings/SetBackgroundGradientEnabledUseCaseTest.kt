package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetBackgroundGradientEnabledUseCaseTest {

    private val repo = FakeSettingsRepository()
    private val useCase = SetBackgroundGradientEnabledUseCase(repo)

    @Test
    fun `invoke enables gradient`() = runTest {
        useCase(true)
        assertTrue(repo.userPreferences.first().backgroundGradientEnabled)
    }

    @Test
    fun `invoke disables gradient`() = runTest {
        useCase(true)
        useCase(false)
        assertFalse(repo.userPreferences.first().backgroundGradientEnabled)
    }
}
