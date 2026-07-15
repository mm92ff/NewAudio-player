package com.example.newaudio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.newaudio.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryGradientDirectionTest {

    @Test
    fun `missing direction uses top to bottom default`() = runTest {
        val repository = SettingsRepositoryImpl(InMemoryPreferencesDataStore())

        assertEquals(
            UserPreferences.GradientDirection.TOP_TO_BOTTOM,
            repository.userPreferences.first().backgroundGradientDirection
        )
    }

    @Test
    fun `every direction survives a datastore roundtrip`() = runTest {
        val repository = SettingsRepositoryImpl(InMemoryPreferencesDataStore())

        UserPreferences.GradientDirection.entries.forEach { direction ->
            repository.setBackgroundGradientDirection(direction)
            assertEquals(direction, repository.userPreferences.first().backgroundGradientDirection)
        }
    }

    @Test
    fun `unknown persisted direction safely falls back to default`() = runTest {
        val directionKey = stringPreferencesKey("background_gradient_direction")
        val dataStore = InMemoryPreferencesDataStore(
            preferencesOf(directionKey to "FUTURE_DIRECTION")
        )
        val repository = SettingsRepositoryImpl(dataStore)

        assertEquals(
            UserPreferences.GradientDirection.TOP_TO_BOTTOM,
            repository.userPreferences.first().backgroundGradientDirection
        )
    }

    @Test
    fun `restore persists the selected direction`() = runTest {
        val repository = SettingsRepositoryImpl(InMemoryPreferencesDataStore())
        val target = UserPreferences.default().copy(
            backgroundGradientDirection =
                UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT
        )

        repository.restoreUserPreferences(target)

        assertEquals(
            UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT,
            repository.userPreferences.first().backgroundGradientDirection
        )
    }
}

private class InMemoryPreferencesDataStore(
    initialPreferences: Preferences = emptyPreferences()
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initialPreferences)
    private val updateMutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = updateMutex.withLock {
        transform(state.value).also { state.value = it }
    }
}
