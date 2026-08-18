package com.example.newaudio.benchmark

import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

internal class BenchmarkDevice(
    val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
) {
    fun prepareSystem() {
        device.executeShellCommand("settings put global window_animation_scale 0")
        device.executeShellCommand("settings put global transition_animation_scale 0")
        device.executeShellCommand("settings put global animator_duration_scale 0")
        device.executeShellCommand("settings put secure immersive_mode_confirmations confirmed")

        dismissLauncherAnrDialog()

        if (Build.VERSION.SDK_INT >= 33) {
            grant("android.permission.READ_MEDIA_AUDIO")
            grant("android.permission.READ_MEDIA_VIDEO")
            grant("android.permission.POST_NOTIFICATIONS")
        } else {
            grant("android.permission.READ_EXTERNAL_STORAGE")
        }
    }

    fun waitFor(
        selector: BySelector,
        label: String,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ): UiObject2 = device.findObject(selector)
        ?: device.wait(Until.findObject(selector), timeoutMs)
        ?: device.findObject(selector)
        ?: fail("Timed out waiting for $label")

    fun waitForAny(
        selectors: List<Pair<BySelector, String>>,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ): UiObject2 {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            selectors.forEach { (selector, _) ->
                device.findObject(selector)?.let { return it }
            }
            device.waitForIdle(250L)
        }
        fail("Timed out waiting for ${selectors.joinToString { it.second }}")
    }

    fun waitForGone(
        selector: BySelector,
        label: String,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ) {
        if (device.findObject(selector) == null) return
        val gone = device.wait(Until.gone(selector), timeoutMs)
        if (gone != true && device.findObject(selector) != null) {
            fail("Timed out waiting for $label to disappear")
        }
    }

    fun click(selector: BySelector, label: String, timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS) {
        clickNodeOrAncestor(waitFor(selector, label, timeoutMs))
        device.waitForIdle()
    }

    fun clickNodeOrAncestor(node: UiObject2) {
        var candidate: UiObject2? = node
        while (candidate != null && !candidate.isClickable) candidate = candidate.parent
        (candidate ?: node).click()
    }

    /**
     * Invokes a Compose semantics action through accessibility instead of translating it
     * to a screen-coordinate tap. This is required for benchmark-only probes layered over
     * AndroidView/PlayerView surfaces, whose platform view can otherwise consume the tap.
     */
    fun clickSemantics(
        selector: BySelector,
        label: String,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ) {
        val node = waitFor(selector, label, timeoutMs)
        if (!performAccessibilityClick(node.resourceName, node.contentDescription)) {
            fail("Could not invoke $label semantics action")
        }
        device.waitForIdle()
    }

    fun clickLowestText(text: String) {
        val node = device.findObjects(By.text(text))
            .filter { it.visibleBounds.width() > 0 && it.visibleBounds.height() > 0 }
            .maxByOrNull { it.visibleBounds.centerY() }
            ?: fail("No visible text '$text'")
        clickNodeOrAncestor(node)
        device.waitForIdle()
    }

    fun flingDownAndBack(container: BySelector = BenchmarkSelectors.scrollable) {
        val before = visibleTextInside(container, "scrollable container before downward swipe")
        verticalSwipe(container, towardEnd = true, label = "scrollable container for downward swipe")
        val after = visibleTextInside(container, "scrollable container after downward swipe")
        if (before == after) {
            fail("The selected container content did not change after a downward swipe")
        }
        verticalSwipe(
            container = container,
            towardEnd = false,
            label = "scrollable container before upward swipe"
        )
    }

    fun scrollUntilText(
        text: String,
        direction: Direction = Direction.DOWN,
        maxSwipes: Int = 12,
        container: BySelector = BenchmarkSelectors.scrollable
    ): UiObject2 {
        device.findObject(By.text(text))?.let { return it }
        val scrollable = waitFor(container, "scrollable container for '$text'")
        repeat(maxSwipes) {
            scrollable.scroll(direction, 0.8f)
            device.waitForIdle()
            device.findObject(By.text(text))?.let { return it }
        }
        return fail("Could not reveal text '$text' after $maxSwipes scrolls")
    }

    fun scrollBetweenTextAnchors(first: String, last: String) {
        waitFor(By.text(first), "first scroll anchor '$first'")
        scrollUntilText(last, Direction.DOWN)
        scrollUntilText(first, Direction.UP)
    }

    fun doubleTap(node: UiObject2) {
        val bounds = node.visibleBounds
        val x = bounds.centerX()
        val y = bounds.centerY()
        device.click(x, y)
        // waitForIdle() can return immediately on a quiet hierarchy, producing
        // taps closer than Android's minimum double-tap interval. This delay is
        // gesture cadence, not a readiness wait.
        SystemClock.sleep(100L)
        device.click(x, y)
        device.waitForIdle()
    }

    fun horizontalSwipe(node: UiObject2, left: Boolean) {
        val bounds = node.visibleBounds
        val startX = if (left) bounds.right * 4 / 5 else bounds.left + bounds.width() / 5
        val endX = if (left) bounds.left + bounds.width() / 5 else bounds.right * 4 / 5
        device.swipe(startX, bounds.centerY(), endX, bounds.centerY(), 20)
        device.waitForIdle()
    }

    fun verticalSwipe(node: UiObject2, towardEnd: Boolean) {
        val bounds = node.visibleBounds
        val startY = if (towardEnd) bounds.top + bounds.height() * 4 / 5 else bounds.top + bounds.height() / 5
        val endY = if (towardEnd) bounds.top + bounds.height() / 5 else bounds.top + bounds.height() * 4 / 5
        device.swipe(bounds.centerX(), startY, bounds.centerX(), endY, 24)
        device.waitForIdle()
    }

    fun verticalSwipe(
        container: BySelector,
        towardEnd: Boolean,
        label: String
    ) {
        repeat(STALE_NODE_RETRIES) {
            val bounds = runCatching { waitFor(container, label).visibleBounds }.getOrNull()
            if (bounds != null) {
                val startY = if (towardEnd) {
                    bounds.top + bounds.height() * 4 / 5
                } else {
                    bounds.top + bounds.height() / 5
                }
                val endY = if (towardEnd) {
                    bounds.top + bounds.height() / 5
                } else {
                    bounds.top + bounds.height() * 4 / 5
                }
                device.swipe(bounds.centerX(), startY, bounds.centerX(), endY, 24)
                device.waitForIdle()
                return
            }
            device.waitForIdle(100L)
        }
        fail("Could not obtain a stable $label")
    }

    fun seek(node: UiObject2, fraction: Float = 0.65f) {
        val bounds = node.visibleBounds
        val endX = bounds.left + (bounds.width() * fraction.coerceIn(0.1f, 0.9f)).toInt()
        device.swipe(bounds.left + bounds.width() / 4, bounds.centerY(), endX, bounds.centerY(), 15)
        device.waitForIdle()
    }

    fun waitForObjectCount(
        selector: BySelector,
        minimum: Int,
        label: String,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ): List<UiObject2> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val matches = device.findObjects(selector)
            if (matches.size >= minimum) return matches
            device.waitForIdle(100L)
        }
        return fail("Timed out waiting for at least $minimum $label nodes")
    }

    fun waitForExactObjectCount(
        selector: BySelector,
        expected: Int,
        label: String,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ): List<UiObject2> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastCount = -1
        while (System.currentTimeMillis() < deadline) {
            val matches = device.findObjects(selector)
            lastCount = matches.size
            if (lastCount == expected) return matches
            SystemClock.sleep(100L)
        }
        return fail("Timed out waiting for exactly $expected $label nodes; last count was $lastCount")
    }

    fun readPosition(
        selector: BySelector,
        label: String,
        refresh: Boolean = false,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ): Long {
        val node = waitFor(selector, label, timeoutMs)
        if (refresh) {
            if (!performAccessibilityClick(node.resourceName, node.contentDescription)) {
                fail("Could not refresh $label through its semantics action")
            }
            device.waitForIdle()
        }
        val refreshed = device.findObject(selector) ?: node
        return parsePosition(refreshed, label)
    }

    fun waitForPosition(
        selector: BySelector,
        label: String,
        refresh: Boolean = false,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS,
        predicate: (Long) -> Boolean
    ): Long {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastPosition: Long? = null
        while (System.currentTimeMillis() < deadline) {
            val node = device.findObject(selector)
            if (node != null) {
                if (refresh) {
                    if (!performAccessibilityClick(node.resourceName, node.contentDescription)) {
                        fail("Could not refresh $label through its semantics action")
                    }
                    device.waitForIdle()
                }
                val refreshed = device.findObject(selector) ?: node
                lastPosition = runCatching { parsePosition(refreshed, label) }.getOrNull()
                if (lastPosition != null && predicate(lastPosition)) return lastPosition
            }
            SystemClock.sleep(100L)
        }
        return fail("Timed out waiting for $label position predicate; last position was $lastPosition")
    }

    fun dragHorizontally(node: UiObject2, left: Boolean) {
        val bounds = node.visibleBounds
        val distance = (device.displayWidth * 0.12f).toInt().coerceAtLeast(48)
        val startX = bounds.centerX()
        val endX = if (left) startX - distance else startX + distance
        device.swipe(startX, bounds.centerY(), endX, bounds.centerY(), 20)
        device.waitForIdle()
    }

    fun waitForMarkerDescriptionChange(
        previousDescription: String,
        timeoutMs: Long = BenchmarkConfig.DEFAULT_TIMEOUT_MS
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val descriptions = device.findObjects(BenchmarkSelectors.videoMarker)
                .mapNotNull { it.contentDescription }
            val changed = descriptions.isNotEmpty() && previousDescription !in descriptions
            if (changed) return
            device.waitForIdle(100L)
        }
        fail("Video marker position did not change after drag")
    }

    fun fail(reason: String): Nothing {
        val context = InstrumentationRegistry.getInstrumentation().context
        val stamp = System.currentTimeMillis()
        val arguments = InstrumentationRegistry.getArguments()
        val testSelector = arguments.getString("class").orEmpty()
        val journeyId = currentJourneyId.takeIf(String::isNotBlank)
            ?: journeyIdFor(testSelector)
        val iteration = currentIteration.coerceAtLeast(1)
        val utcTimestamp = Instant.ofEpochMilli(stamp).toString()
        val fileTimestamp = utcTimestamp.replace(Regex("[^0-9A-Za-z]"), "")
        val filePrefix = "failure-${sanitize(journeyId)}-iter-${iteration.toString().padStart(2, '0')}-$fileTimestamp"
        val requestedOutputDir = sequenceOf(
            arguments.getString("additionalTestOutputDir"),
            arguments.getString("androidx.benchmark.output.dir")
        )
            .filterNotNull()
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            ?.let(::File)
        val fallbackOutputDir = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "benchmark-failures"
        )
        val outputDir = requestedOutputDir
            ?.takeIf { ensureWritableDirectory(it, stamp) }
            ?: fallbackOutputDir.apply { mkdirs() }
        val screenshot = File(outputDir, "$filePrefix-screenshot.png")
        val hierarchy = File(outputDir, "$filePrefix-window.xml")
        val logcat = File(outputDir, "$filePrefix-logcat.txt")
        val failure = File(outputDir, "$filePrefix-failure.json")
        val artifactErrors = mutableListOf<String>()
        runCatching {
            check(device.takeScreenshot(screenshot)) { "UiDevice.takeScreenshot returned false" }
        }.onFailure { artifactErrors += "screenshot:${it.message}" }
        runCatching { device.dumpWindowHierarchy(hierarchy) }
            .onFailure { artifactErrors += "hierarchy:${it.message}" }
        runCatching { logcat.writeText(relevantLogcat()) }
            .onFailure { artifactErrors += "logcat:${it.message}" }
        runCatching {
            failure.writeText(
                org.json.JSONObject()
                    .put("schemaVersion", 1)
                    .put("timestampEpochMs", stamp)
                    .put("timestampUtc", utcTimestamp)
                    .put("journeyId", journeyId)
                    .put("iteration", iteration)
                    .put("reason", reason)
                    .put("testClass", testSelector)
                    .put("currentPackage", device.currentPackageName)
                    .put("screenshot", screenshot.name)
                    .put("hierarchy", hierarchy.name)
                    .put("logcat", logcat.name)
                    .put("artifactErrors", org.json.JSONArray(artifactErrors))
                    .toString(2)
            )
        }.onFailure { artifactErrors += "failure-json:${it.message}" }
        val visibleText = device.findObjects(By.text(Pattern.compile(".+")))
            .mapNotNull { it.text }
            .distinct()
            .joinToString(" | ")
        error(
            "$reason. Visible text: $visibleText. " +
                "Screenshot: ${screenshot.absolutePath}; hierarchy: ${hierarchy.absolutePath}; " +
                "logcat: ${logcat.absolutePath}; failure metadata: ${failure.absolutePath}"
        )
    }

    private fun parsePosition(node: UiObject2, label: String): Long {
        val description = node.contentDescription
            ?: fail("$label position node has no content description")
        return POSITION_DESCRIPTION_PATTERN.matcher(description)
            .takeIf { it.matches() }
            ?.group(1)
            ?.toLongOrNull()
            ?: fail("$label position description '$description' has no millisecond value")
    }

    private fun performAccessibilityClick(
        resourceName: String?,
        contentDescription: String?
    ): Boolean {
        if (resourceName.isNullOrBlank() && contentDescription.isNullOrBlank()) return false
        val root = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .rootInActiveWindow
            ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val matchesResourceName = !resourceName.isNullOrBlank() &&
                current.viewIdResourceName == resourceName
            val matchesContentDescription = !contentDescription.isNullOrBlank() &&
                current.contentDescription?.toString() == contentDescription
            val matches = matchesResourceName || matchesContentDescription
            if (matches) {
                var candidate: AccessibilityNodeInfo? = current
                while (candidate != null && !candidate.isClickable) {
                    candidate = candidate.parent
                }
                if (candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    return true
                }
            }
            repeat(current.childCount) { index ->
                current.getChild(index)?.let(queue::addLast)
            }
        }
        return false
    }

    private fun visibleTextInside(container: BySelector, label: String): Set<String> {
        repeat(STALE_NODE_RETRIES) {
            val snapshot = runCatching {
                val bounds = waitFor(container, label).visibleBounds
                device.findObjects(By.text(Pattern.compile(".+")))
                    .mapNotNull { node ->
                        runCatching {
                            val nodeBounds = node.visibleBounds
                            node.text?.takeIf {
                                it.isNotBlank() &&
                                    bounds.contains(nodeBounds.centerX(), nodeBounds.centerY())
                            }
                        }.getOrNull()
                    }
                    .toSet()
            }.getOrNull()
            if (snapshot != null) return snapshot
            device.waitForIdle(100L)
        }
        return fail("Could not read a stable text snapshot from $label")
    }

    private fun ensureWritableDirectory(directory: File, stamp: Long): Boolean = runCatching {
        if (!directory.exists() && !directory.mkdirs()) return@runCatching false
        val probe = File(directory, ".newaudio-write-probe-$stamp")
        probe.writeText("benchmark failure output probe")
        probe.delete()
        true
    }.getOrDefault(false)

    private fun grant(permission: String) {
        device.executeShellCommand("pm grant ${BenchmarkConfig.TARGET_PACKAGE} $permission")
    }

    private fun dismissLauncherAnrDialog() {
        if (device.findObject(By.text(LAUNCHER_ANR_TITLE_PATTERN)) == null) return
        val closeButton = device.findObject(By.res("android:id/aerr_close"))
        if (closeButton != null) {
            closeButton.click()
            device.waitForIdle()
            return
        }

        // Fallback for platform variants that expose the launcher ANR without the
        // standard close-button resource. This path only runs while that dialog exists.
        BACKGROUND_LAUNCHER_PACKAGES.forEach { packageName ->
            if (device.executeShellCommand("pm path $packageName").contains("package:")) {
                device.executeShellCommand("am force-stop $packageName")
            }
        }
        device.waitForIdle()
    }

    private fun relevantLogcat(): String = buildString {
        val packages = listOf(BenchmarkConfig.TARGET_PACKAGE, BenchmarkConfig.BENCHMARK_PACKAGE)
        packages.forEach { packageName ->
            val pids = device.executeShellCommand("pidof $packageName")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.matches(Regex("\\d+")) }
            if (pids.isEmpty()) {
                appendLine("# No live pid for $packageName")
            } else {
                pids.forEach { pid ->
                    appendLine("# $packageName pid=$pid")
                    appendLine(device.executeShellCommand("logcat -d --pid=$pid -t 250"))
                }
            }
        }
        appendLine("# Package-matched ActivityManager/AndroidRuntime events")
        val systemEvents = device.executeShellCommand(
            "logcat -d -t 500 ActivityManager:E AndroidRuntime:E *:S"
        )
        systemEvents.lineSequence()
            .filter { line -> packages.any(line::contains) }
            .forEach(::appendLine)
    }

    private fun journeyIdFor(testSelector: String): String {
        val method = testSelector.substringAfter('#', missingDelimiterValue = "unknown")
        return JOURNEY_IDS[method] ?: method.ifBlank { "unknown" }
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .trim('-')
        .ifBlank { "unknown" }

    companion object {
        private const val STALE_NODE_RETRIES = 4
        private val BACKGROUND_LAUNCHER_PACKAGES = listOf(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3"
        )
        private val LAUNCHER_ANR_TITLE_PATTERN = Pattern.compile(
            "^(Quickstep|Pixel Launcher) isn't responding$"
        )

        fun beginIteration(journeyId: String = "unknown"): Int {
            currentJourneyId = journeyId.ifBlank { "unknown" }
            return JOURNEY_ITERATIONS
                .computeIfAbsent(currentJourneyId) { AtomicInteger(0) }
                .incrementAndGet()
                .also { currentIteration = it }
        }

        fun beginIteration(journey: TraceJourney): Int = beginIteration(
            journey.sectionName
                .substringAfter(':')
                .replace(Regex("^([A-Z]{2})([0-9]{2})"), "$1-$2")
                .replace('_', '-')
        )

        val POSITION_DESCRIPTION_PATTERN: Pattern = Pattern.compile(".*:(\\d+)$")
        val JOURNEY_ITERATIONS = ConcurrentHashMap<String, AtomicInteger>()
        @Volatile
        var currentIteration: Int = 0
        @Volatile
        var currentJourneyId: String = ""
        val JOURNEY_IDS = mapOf(
            "st01ColdStartupToBrowserReady" to "ST-01",
            "st02WarmStartupToBrowserReady" to "ST-02",
            "nv01BrowserSettingsBrowser" to "NV-01",
            "nv02BrowserPlaylistBrowser" to "NV-02",
            "diagnosticFailureArtifactProbe" to "DIAG-FAILURE",
            "br01AudioListScroll" to "BR-01",
            "br02AudioListIdleWithMiniPlayer" to "BR-02",
            "br03VideoListScroll" to "BR-03",
            "br04VideoGalleryTwoColumns" to "BR-04-GRID-2-COLD",
            "br04VideoGalleryThreeColumns" to "BR-04-GRID-3-COLD",
            "br04VideoGalleryFourColumns" to "BR-04-GRID-4-COLD",
            "br04VideoGalleryTwoColumnsWarm" to "BR-04-GRID-2-WARM",
            "br04VideoGalleryThreeColumnsWarm" to "BR-04-GRID-3-WARM",
            "br04VideoGalleryFourColumnsWarm" to "BR-04-GRID-4-WARM",
            "br05NestedFolderColdCache" to "BR-05",
            "br06NestedFolderWarmCache" to "BR-06",
            "au01MiniPlayerIdle" to "AU-01",
            "au02FullPlayerIdle" to "AU-02",
            "au03SeekPauseResumeNext" to "AU-03",
            "au04SettingsScrollDuringPlayback" to "AU-04",
            "au05PausedControlIdle" to "AU-05",
            "au06MiniPlayerRepeatOffIdle" to "AU-06",
            "au07MiniPlayerRepeatOneIdle" to "AU-07",
            "au08LongTitleMarqueeOffIdle" to "AU-08",
            "au09LongTitleMarqueeOnIdle" to "AU-09",
            "vi01InlineVideoIdle" to "VI-01",
            "vi02FullscreenIdle" to "VI-02",
            "vi03ControlsSeekAndMarker" to "VI-03",
            "vi04FullscreenInlineTransition" to "VI-04",
            "vi05SwipeNextPrevious" to "VI-05",
            "vi06FullscreenIdleMarkersOff" to "VI-06-MARKERS-OFF",
            "vi06FullscreenIdleMarkersOn" to "VI-06-MARKERS-ON"
        )
    }
}
