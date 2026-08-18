package com.example.newaudio.benchmark

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import java.util.regex.Pattern

internal class GalleryScrollProbe(
    private val ui: BenchmarkDevice = BenchmarkDevice(
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    )
) {
    fun resetToStart(scenario: GalleryBenchmarkScenario) {
        val selector = BenchmarkSelectors.videoGalleryColumns(scenario.columns)
        val label = "${scenario.columns}-column gallery reset for ${scenario.journeyId}"
        ui.waitFor(selector, label)

        var visible = scrollUntilFirstVideo(
            selector = selector,
            towardEnd = false,
            label = label,
            initialVisible = visibleVideoNames()
        )

        // Item zero is the reachability spacer. If the reset landed before the
        // first media row, move forward until that row becomes visible.
        visible = scrollUntilFirstVideo(
            selector = selector,
            towardEnd = true,
            label = label,
            initialVisible = visible
        )
        if (BenchmarkConfig.FIRST_VIDEO !in visible) {
            ui.fail("${scenario.journeyId} could not reset to the first root video")
        }
    }

    fun scrollForwardAndBack(scenario: GalleryBenchmarkScenario) {
        GalleryBenchmarkScenario.validateContract()
        val resolved = GalleryBenchmarkScenario.fromMethod(scenario.methodName)
        check(resolved == scenario) { "Gallery scenario mapping changed for ${scenario.methodName}" }

        val gallery = ui.waitFor(
            BenchmarkSelectors.videoGalleryColumns(scenario.columns),
            "${scenario.columns}-column gallery for ${scenario.journeyId}"
        )
        if (!gallery.isScrollable) {
            ui.fail(
                "${scenario.journeyId} has no forward scroll range with " +
                    "${BenchmarkConfig.VIDEO_COUNT} deterministic videos"
            )
        }

        val before = visibleVideoNames()
        if (before.isEmpty()) {
            ui.fail("${scenario.journeyId} exposes no visible video-name anchors before scrolling")
        }
        ui.verticalSwipe(gallery, towardEnd = true)

        val deadline = System.currentTimeMillis() + BenchmarkConfig.DEFAULT_TIMEOUT_MS
        var after = visibleVideoNames()
        while (after == before && System.currentTimeMillis() < deadline) {
            ui.device.waitForIdle(100L)
            after = visibleVideoNames()
        }
        if (after == before) {
            ui.fail(
                "${scenario.journeyId} reported a scroll but its visible video set did not change: $before"
            )
        }

        ui.verticalSwipe(gallery, towardEnd = false)
    }

    private fun scrollUntilFirstVideo(
        selector: BySelector,
        towardEnd: Boolean,
        label: String,
        initialVisible: Set<String>
    ): Set<String> {
        var visible = initialVisible
        var stalledScrolls = 0
        repeat(MAX_RESET_SCROLLS) {
            if (BenchmarkConfig.FIRST_VIDEO in visible) return visible
            ui.verticalSwipe(selector, towardEnd = towardEnd, label = label)
            val after = visibleVideoNames()
            if (after == visible) {
                stalledScrolls++
                if (stalledScrolls >= MAX_STALLED_SCROLLS) return visible
            } else {
                visible = after
                stalledScrolls = 0
            }
        }
        return visible
    }

    private fun visibleVideoNames(): Set<String> {
        repeat(STALE_NODE_RETRIES) {
            try {
                return ui.device.findObjects(VIDEO_FILE_TEXT)
                    .filter { it.visibleBounds.width() > 0 && it.visibleBounds.height() > 0 }
                    .mapNotNull { it.text }
                    .toSortedSet()
            } catch (_: StaleObjectException) {
                ui.device.waitForIdle(STALE_NODE_RETRY_DELAY_MS)
            }
        }
        return ui.fail("Could not obtain a stable snapshot of visible gallery videos")
    }

    private companion object {
        const val MAX_RESET_SCROLLS = 12
        const val MAX_STALLED_SCROLLS = 3
        const val STALE_NODE_RETRIES = 4
        const val STALE_NODE_RETRY_DELAY_MS = 100L
        val VIDEO_FILE_TEXT = By.text(Pattern.compile("Video_.+\\.mp4"))
    }
}
