package com.example.newaudio.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDecodePolicyTest {
    @Test
    fun `small image is decoded without sampling`() {
        assertEquals(1, ArtworkDecodePolicy.calculateInSampleSize(512, 512, 512, 512))
    }

    @Test
    fun `extremely wide artwork is bounded by target dimensions`() {
        val sample = ArtworkDecodePolicy.calculateInSampleSize(
            width = 100_000,
            height = 100,
            requestedWidth = 512,
            requestedHeight = 512
        )

        assertTrue(sample >= 128)
        assertTrue((100_000L + sample - 1) / sample <= 1_024L)
    }

    @Test
    fun `pixel limit is enforced independently of target dimensions`() {
        val sample = ArtworkDecodePolicy.calculateInSampleSize(
            width = 8_000,
            height = 8_000,
            requestedWidth = 8_000,
            requestedHeight = 8_000,
            maxPixels = 4_000_000
        )

        val decodedWidth = (8_000L + sample - 1) / sample
        val decodedHeight = (8_000L + sample - 1) / sample
        assertTrue(decodedWidth * decodedHeight <= 4_000_000L)
    }
}
