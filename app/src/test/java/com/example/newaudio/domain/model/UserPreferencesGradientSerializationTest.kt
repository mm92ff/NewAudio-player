package com.example.newaudio.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesGradientSerializationTest {

    @Test
    fun `legacy preferences without direction use top to bottom`() {
        val encoded = Json.encodeToString(UserPreferences.default())
        val fields = Json.parseToJsonElement(encoded).jsonObject
        val legacyJson = JsonObject(fields - "backgroundGradientDirection").toString()

        val restored = Json.decodeFromString<UserPreferences>(legacyJson)

        assertEquals(
            UserPreferences.GradientDirection.TOP_TO_BOTTOM,
            restored.backgroundGradientDirection
        )
    }

    @Test
    fun `every direction survives serialization roundtrip`() {
        UserPreferences.GradientDirection.entries.forEach { direction ->
            val original = UserPreferences.default().copy(
                backgroundGradientDirection = direction
            )

            val restored = Json.decodeFromString<UserPreferences>(
                Json.encodeToString(original)
            )

            assertEquals(direction, restored.backgroundGradientDirection)
        }
    }
}
