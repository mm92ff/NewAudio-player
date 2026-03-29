package com.example.newaudio.domain.usecase.settings

import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.fake.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SetThemeUseCaseTest {

    private val repo = FakeSettingsRepository()
    private val useCase = SetThemeUseCase(repo)

    @Test
    fun `invoke sets DARK theme`() = runTest {
        useCase(UserPreferences.Theme.DARK)
        assertEquals(UserPreferences.Theme.DARK, repo.setThemeCalled)
    }

    @Test
    fun `invoke sets LIGHT theme`() = runTest {
        useCase(UserPreferences.Theme.LIGHT)
        assertEquals(UserPreferences.Theme.LIGHT, repo.setThemeCalled)
    }

    @Test
    fun `invoke sets SYSTEM theme`() = runTest {
        useCase(UserPreferences.Theme.SYSTEM)
        assertEquals(UserPreferences.Theme.SYSTEM, repo.setThemeCalled)
    }
}
