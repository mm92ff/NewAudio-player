package com.example.newaudio.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileHashUtils {
    /**
     * Calculates a "fast hash" for a file.
     * Uses file size + the first 8 KB for maximum performance with high accuracy.
     */
    fun calculateFastHash(filePath: String): String? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) return null

        return try {
            val digest = MessageDigest.getInstance("MD5")
            val size = file.length()
            
            // 1. Include file size in the hash
            digest.update(size.toString().toByteArray())

            // 2. Read the first 8 KB
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                val bytesRead = fis.read(buffer)
                if (bytesRead > 0) {
                    digest.update(buffer, 0, bytesRead)
                }
            }

            // Convert to hex string
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
