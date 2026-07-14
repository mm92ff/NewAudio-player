package com.example.newaudio.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTabTest {
    @Test
    fun `tab order and automation identifiers remain stable`() {
        assertEquals(
            listOf(
                SettingsTab.GENERAL,
                SettingsTab.MEDIA,
                SettingsTab.DESIGN,
                SettingsTab.SYSTEM
            ),
            SettingsTab.entries
        )
        assertEquals(4, SettingsTab.entries.map { it.tabTestTag }.distinct().size)
        assertEquals(4, SettingsTab.entries.map { it.contentTestTag }.distinct().size)
    }
}
