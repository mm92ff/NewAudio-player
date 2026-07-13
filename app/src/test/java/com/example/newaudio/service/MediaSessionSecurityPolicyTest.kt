package com.example.newaudio.service

import com.example.newaudio.domain.audio.EqualizerConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionSecurityPolicyTest {
    @Test
    fun `own uid and trusted system controller are allowed`() {
        assertTrue(MediaSessionSecurityPolicy.isControllerAllowed(1000, 1000, false))
        assertTrue(MediaSessionSecurityPolicy.isControllerAllowed(1000, 2000, true))
    }

    @Test
    fun `untrusted external uid is rejected`() {
        assertFalse(MediaSessionSecurityPolicy.isControllerAllowed(1000, 2000, false))
    }

    @Test
    fun `equalizer band and level must be within hardware bounds`() {
        val config = EqualizerConfig(false, 5, -1_500, 1_500, intArrayOf(), intArrayOf())

        assertTrue(MediaSessionSecurityPolicy.isBandValueValid(config, 4, 15f))
        assertFalse(MediaSessionSecurityPolicy.isBandValueValid(config, 5, 0f))
        assertFalse(MediaSessionSecurityPolicy.isBandValueValid(config, 1, Float.NaN))
        assertFalse(MediaSessionSecurityPolicy.isBandValueValid(config, 1, 15.1f))
    }

    @Test
    fun `only known preset names are accepted`() {
        assertTrue(MediaSessionSecurityPolicy.isPresetValid("normal"))
        assertFalse(MediaSessionSecurityPolicy.isPresetValid("../../custom"))
    }
}
