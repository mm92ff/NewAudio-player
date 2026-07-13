package com.example.newaudio.util

import kotlin.math.ceil

object ArtworkDecodePolicy {
    fun calculateInSampleSize(
        width: Int,
        height: Int,
        requestedWidth: Int,
        requestedHeight: Int,
        maxPixels: Long = Constants.Security.MAX_ARTWORK_PIXELS
    ): Int {
        if (width <= 0 || height <= 0 || requestedWidth <= 0 || requestedHeight <= 0) return 1

        val maxDecodedWidth = requestedWidth.toLong() * 2L
        val maxDecodedHeight = requestedHeight.toLong() * 2L
        var sampleSize = 1
        while (sampleSize < MAX_SAMPLE_SIZE) {
            val decodedWidth = ceil(width.toDouble() / sampleSize).toLong()
            val decodedHeight = ceil(height.toDouble() / sampleSize).toLong()
            val decodedPixels = decodedWidth * decodedHeight
            if (
                decodedWidth <= maxDecodedWidth &&
                decodedHeight <= maxDecodedHeight &&
                decodedPixels <= maxPixels
            ) {
                break
            }
            sampleSize *= 2
        }
        return sampleSize
    }

    private const val MAX_SAMPLE_SIZE = 1 shl 30
}
