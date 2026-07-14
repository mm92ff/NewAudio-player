package com.example.newaudio.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserReadinessTest {
    @Test
    fun delayedFixtureLoadingCannotReportReadyBeforeContentArrives() {
        assertFalse(
            isBrowserContentReady(
                isLoading = true,
                itemCount = 30,
                requireFixtureContent = true,
                browserRootPositioned = true,
                firstInteractiveContentPositioned = true
            )
        )
        assertFalse(
            isBrowserContentReady(
                isLoading = false,
                itemCount = 0,
                requireFixtureContent = true,
                browserRootPositioned = true,
                firstInteractiveContentPositioned = false
            )
        )
        assertFalse(
            isBrowserContentReady(
                isLoading = false,
                itemCount = 30,
                requireFixtureContent = true,
                browserRootPositioned = false,
                firstInteractiveContentPositioned = true
            )
        )
        assertFalse(
            isBrowserContentReady(
                isLoading = false,
                itemCount = 30,
                requireFixtureContent = true,
                browserRootPositioned = true,
                firstInteractiveContentPositioned = false
            )
        )
        assertTrue(
            isBrowserContentReady(
                isLoading = false,
                itemCount = 30,
                requireFixtureContent = true,
                browserRootPositioned = true,
                firstInteractiveContentPositioned = true
            )
        )
    }

    @Test
    fun productionEmptyStateCanStillCompleteDrawing() {
        assertTrue(
            isBrowserContentReady(
                isLoading = false,
                itemCount = 0,
                requireFixtureContent = false,
                browserRootPositioned = true,
                firstInteractiveContentPositioned = false
            )
        )
    }
}
