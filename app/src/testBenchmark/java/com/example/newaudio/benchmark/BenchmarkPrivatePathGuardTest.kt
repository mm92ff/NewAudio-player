package com.example.newaudio.benchmark

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BenchmarkPrivatePathGuardTest {
    @Test
    fun acceptsOnlyExpectedDirectPrivateChild() {
        val parent = Files.createTempDirectory("newaudio-path-guard").toFile()
        try {
            val expected = File(parent, "coil-preview-cache")
            assertEquals(
                expected.canonicalFile,
                BenchmarkPrivatePathGuard.requireDirectPrivateChild(
                    parent,
                    expected,
                    "coil-preview-cache"
                )
            )
            assertThrows(IllegalStateException::class.java) {
                BenchmarkPrivatePathGuard.requireDirectPrivateChild(
                    parent,
                    File(parent, "../outside"),
                    "outside"
                )
            }
            assertThrows(IllegalStateException::class.java) {
                BenchmarkPrivatePathGuard.requireDirectPrivateChild(
                    parent,
                    File(parent, "coil-preview-cache/nested"),
                    "nested"
                )
            }
        } finally {
            parent.deleteRecursively()
        }
    }
}
