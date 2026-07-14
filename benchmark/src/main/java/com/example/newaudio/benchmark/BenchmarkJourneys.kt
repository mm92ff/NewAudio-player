package com.example.newaudio.benchmark

import android.os.SystemClock
import android.os.Trace
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import kotlin.math.abs

internal class BenchmarkJourneys(
    private val scope: MacrobenchmarkScope,
    private val ui: BenchmarkDevice = BenchmarkDevice(
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    )
) {
    fun launchBrowserFromStoppedState() {
        scope.killProcess()
        scope.startActivityAndWait()
        waitForBrowser()
    }

    fun startAndWaitForBrowser() {
        scope.startActivityAndWait()
        waitForBrowser()
    }

    fun waitForBrowser() {
        // This tag is emitted from the exact boolean used by ReportDrawnWhen:
        // non-loading and, for benchmark builds, non-empty content.
        ui.waitFor(BROWSER_READY, "drawn, non-loading, non-empty browser")
        ui.waitFor(BenchmarkSelectors.browserRoot, "browser root tag")
        ui.waitForAny(
            listOf(
                BenchmarkSelectors.browserList to "browser list",
                BenchmarkSelectors.videoGallery to "video gallery"
            )
        )
    }

    fun openSettings() {
        ui.click(BenchmarkSelectors.settingsButton, "Settings action")
        ui.waitForAny(
            listOf(
                BenchmarkSelectors.settingsRoot to "Settings root tag",
                BenchmarkSelectors.text("Settings") to "Settings title"
            )
        )
    }

    fun openPlaylistManager() {
        ui.click(BenchmarkSelectors.playlistButton, "playlist manager action")
        ui.waitForAny(
            listOf(
                BenchmarkSelectors.playlistRoot to "playlist root tag",
                BenchmarkSelectors.text("Backup") to "playlist screen title"
            )
        )
    }

    fun navigateBackToBrowser() {
        ui.device.pressBack()
        waitForBrowser()
    }

    fun ensureMusicMode() {
        if (ui.device.findObject(BenchmarkSelectors.text("Music")) == null) {
            ui.click(BenchmarkSelectors.text("Video"), "Video mode toggle")
        }
        ui.waitFor(BenchmarkSelectors.browserList, "music browser list")
        ui.waitFor(BenchmarkSelectors.audioFile(BenchmarkConfig.FIRST_AUDIO), "first audio fixture")
    }

    fun ensureVideoMode() {
        if (ui.device.findObject(BenchmarkSelectors.text("Video")) == null) {
            ui.click(BenchmarkSelectors.text("Music"), "Music mode toggle")
        }
        ui.waitForAny(
            listOf(
                BenchmarkSelectors.browserList to "video browser list",
                BenchmarkSelectors.videoGallery to "video gallery"
            )
        )
        ui.waitFor(BenchmarkSelectors.videoFile(BenchmarkConfig.FIRST_VIDEO), "first video fixture")
    }

    fun scrollBrowserDownAndBack() {
        ui.flingDownAndBack()
    }

    fun scrollAudioBrowserDownAndBack() {
        ui.scrollBetweenTextAnchors(BenchmarkConfig.FIRST_AUDIO, BenchmarkConfig.LAST_AUDIO)
    }

    fun scrollVideoBrowserDownAndBack() {
        ui.scrollBetweenTextAnchors(BenchmarkConfig.FIRST_VIDEO, BenchmarkConfig.LAST_VIDEO)
    }

    fun openNestedAudioFolderAndReturn() {
        ensureMusicMode()
        ui.click(BenchmarkSelectors.folder("Albums"), "Albums fixture folder")
        ui.waitFor(BenchmarkSelectors.folder("One"), "Albums/One fixture folder")
        ui.click(BenchmarkSelectors.folder("One"), "Albums/One fixture folder")
        ui.waitFor(BenchmarkSelectors.audioFile("Audio_%_Literal.wav"), "nested audio fixture")
        ui.device.pressBack()
        ui.waitFor(BenchmarkSelectors.folder("One"), "Albums folder after nested back")
        ui.device.pressBack()
        ui.waitFor(BenchmarkSelectors.audioFile(BenchmarkConfig.FIRST_AUDIO), "audio root after folder back")
    }

    fun configureVideoGallery(columns: Int) {
        require(columns in 2..4)
        openSettings()
        ui.click(BenchmarkSelectors.settingsTabMedia, "Media settings tab")
        ui.waitFor(BenchmarkSelectors.settingsContentMedia, "Media settings content")
        val mode = ui.scrollUntilText(
            "Gallery square",
            container = BenchmarkSelectors.settingsScroll
        )
        ui.clickNodeOrAncestor(mode)
        val columnLabel = "$columns videos per row"
        val column = ui.scrollUntilText(
            columnLabel,
            container = BenchmarkSelectors.settingsScroll
        )
        ui.clickNodeOrAncestor(column)
        navigateBackToBrowser()
        ensureVideoMode()
        ui.waitFor(
            BenchmarkSelectors.videoGalleryColumns(columns),
            "$columns-column video gallery"
        )
    }

    fun openAudioPlaylist() {
        openPlaylistManager()
        ui.click(BenchmarkSelectors.text("Music"), "Music playlist tab")
        ui.click(BenchmarkSelectors.text(BenchmarkConfig.AUDIO_PLAYLIST), "audio fixture playlist")
        ui.waitFor(BenchmarkSelectors.text(BenchmarkConfig.FIRST_PLAYLIST_AUDIO_TITLE), "audio playlist content")
    }

    fun openVideoPlaylist() {
        openPlaylistManager()
        ui.click(BenchmarkSelectors.text("Video"), "Video playlist tab")
        ui.click(BenchmarkSelectors.text(BenchmarkConfig.VIDEO_PLAYLIST), "video fixture playlist")
        ui.waitFor(BenchmarkSelectors.text(BenchmarkConfig.FIRST_PLAYLIST_VIDEO_TITLE), "video playlist content")
    }

    fun startFirstAudio(): String {
        ensureMusicMode()
        ui.click(BenchmarkSelectors.audioFile(BenchmarkConfig.FIRST_AUDIO), "first audio fixture")
        ui.waitFor(BenchmarkSelectors.miniPlayer, "mini player", BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS)
        ui.waitFor(
            BenchmarkSelectors.audioPlaybackReady,
            "playing audio with known duration",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "started audio")
        return BenchmarkConfig.FIRST_AUDIO_TITLE
    }

    fun startLongTitleAudio(): String {
        ensureMusicMode()
        ui.click(BenchmarkSelectors.folder("Long Titles"), "Long Titles fixture folder")
        ui.waitFor(BenchmarkSelectors.audioFile(BenchmarkConfig.LONG_TITLE_AUDIO), "long-title audio fixture")
        ui.click(BenchmarkSelectors.audioFile(BenchmarkConfig.LONG_TITLE_AUDIO), "long-title audio fixture")
        ui.waitFor(BenchmarkSelectors.miniPlayer, "mini player", BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS)
        ui.waitFor(
            BenchmarkSelectors.audioPlaybackReady,
            "playing long-title audio with known duration",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        assertCurrentAudio(BenchmarkConfig.LONG_TITLE_AUDIO_TITLE, "started long-title audio")
        return BenchmarkConfig.LONG_TITLE_AUDIO_TITLE
    }

    fun openFullPlayer(
        title: String = BenchmarkConfig.FIRST_AUDIO_TITLE,
        expectEmbeddedAlbumArt: Boolean = true
    ) {
        val miniPlayer = ui.device.findObject(BenchmarkSelectors.miniPlayer)
        if (miniPlayer != null) {
            miniPlayer.click()
            ui.device.waitForIdle()
        }
        if (ui.device.findObject(BenchmarkSelectors.fullPlayer) == null) {
            ui.clickLowestText(title)
        }
        ui.waitForAny(
            listOf(
                BenchmarkSelectors.fullPlayer to "full player root tag",
                BenchmarkSelectors.playerSeekBar to "full player seek bar"
            ),
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        ui.waitFor(
            BenchmarkSelectors.audioPlaybackReady,
            "full player audio playback readiness",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        assertCurrentAudio(title, "full-player audio")
        ui.waitFor(
            ALBUM_ART_LOAD_COMPLETE,
            "album-art load completion (including no-art media)",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        if (expectEmbeddedAlbumArt) {
            ui.waitFor(
                BenchmarkSelectors.albumArtReady,
                "decoded embedded album art",
                BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
            )
        }
    }

    fun audioControlsJourney() {
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "audio before controls journey")
        val positionBeforeSeek = ui.readPosition(AUDIO_POSITION, "audio before seek")
        val seekBar = ui.waitFor(BenchmarkSelectors.playerSeekBar, "audio seek bar")
        ui.seek(seekBar)
        val positionAfterSeek = ui.waitForPosition(
            AUDIO_POSITION,
            "audio after seek",
            timeoutMs = BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        ) { position ->
            position >= positionBeforeSeek + MIN_SEEK_DELTA_MS &&
                abs(position - EXPECTED_PRIMARY_AUDIO_SEEK_MS) <= SEEK_TARGET_TOLERANCE_MS
        }
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "audio after seek")
        ui.click(BenchmarkSelectors.playPauseButton, "pause audio")
        ui.waitForGone(BenchmarkSelectors.audioPlaybackReady, "audio playback readiness after pause")
        val pausedPosition = ui.readPosition(AUDIO_POSITION, "paused audio")
        SystemClock.sleep(PAUSED_POSITION_SAMPLE_DELAY_MS)
        val stablePausedPosition = ui.readPosition(AUDIO_POSITION, "stable paused audio")
        if (abs(stablePausedPosition - pausedPosition) > POSITION_STABILITY_TOLERANCE_MS) {
            ui.fail("Paused audio position moved from $pausedPosition to $stablePausedPosition ms")
        }
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "paused audio")
        ui.click(BenchmarkSelectors.playPauseButton, "resume audio")
        ui.waitFor(BenchmarkSelectors.audioPlaybackReady, "audio playback readiness after resume")
        ui.waitForPosition(
            AUDIO_POSITION,
            "resumed audio progress",
            timeoutMs = BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        ) { position -> position > maxOf(positionAfterSeek, stablePausedPosition) + MIN_RESUME_DELTA_MS }
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "resumed audio")
        ui.click(BenchmarkSelectors.nextButton, "next audio")
        assertCurrentAudio(BenchmarkConfig.NEXT_AUDIO_TITLE, "next audio")
        ui.waitFor(BenchmarkSelectors.audioPlaybackReady, "next audio playback readiness")
    }

    fun pauseAudioAndWait() {
        ui.click(BenchmarkSelectors.playPauseButton, "pause audio")
        ui.waitForGone(BenchmarkSelectors.audioPlaybackReady, "paused audio playback readiness")
    }

    fun startFirstVideo() {
        ensureVideoMode()
        ui.click(BenchmarkSelectors.videoFile(BenchmarkConfig.FIRST_VIDEO), "first video fixture")
        ui.waitFor(BenchmarkSelectors.inlineVideo, "inline video", BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS)
        ui.waitFor(
            BenchmarkSelectors.videoPlaybackReady,
            "playing video after first frame",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        assertCurrentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE, "started video")
    }

    fun openVideoFullscreen() {
        ui.waitFor(BenchmarkSelectors.inlineVideo, "inline video")
        ui.clickSemantics(VIDEO_TOGGLE_FULLSCREEN, "inline fullscreen toggle")
        ui.waitFor(BenchmarkSelectors.videoFullscreen, "video fullscreen")
        ui.waitFor(
            BenchmarkSelectors.videoPlaybackReady,
            "fullscreen video playback readiness",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        assertCurrentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE, "fullscreen video")
    }

    fun revealVideoControls() {
        val fullscreen = ui.waitFor(BenchmarkSelectors.videoFullscreen, "video fullscreen")
        fullscreen.click()
        ui.waitFor(VIDEO_CONTROLS_VISIBLE, "visible video controls")
        ui.waitFor(BenchmarkSelectors.videoPlayPauseButton, "video play/pause control")
    }

    fun pinVideoControlsForStableIdle(expectMarkers: Boolean) {
        revealVideoControls()
        ui.waitFor(VIDEO_CONTROLS_PINNED, "pinned video controls")
        if (expectMarkers) {
            ui.waitFor(VIDEO_MARKERS_READY, "three-marker baseline readiness")
            ui.waitForExactObjectCount(BenchmarkSelectors.videoMarker, 3, "video marker")
        } else {
            ui.waitForExactObjectCount(BenchmarkSelectors.videoMarker, 0, "video marker")
        }
    }

    fun videoControlsJourney() {
        revealVideoControls()
        ui.waitFor(VIDEO_MARKERS_READY, "three-marker baseline readiness")
        val baselineMarkers = ui.waitForExactObjectCount(
            BenchmarkSelectors.videoMarker,
            3,
            "video marker"
        )
        if (baselineMarkers.size != 3) {
            ui.fail("Expected exactly three baseline video markers, found ${baselineMarkers.size}")
        }
        val baselineMarkerDescriptions = baselineMarkers.mapNotNull { it.contentDescription }.toSet()

        val positionBeforeSeek = ui.readPosition(
            FULLSCREEN_VIDEO_POSITION,
            "fullscreen video before seek",
            refresh = true
        )
        val seekBar = ui.waitFor(By.clazz("android.widget.SeekBar"), "video seek bar")
        ui.seek(seekBar)
        val positionAfterSeek = ui.waitForPosition(
            FULLSCREEN_VIDEO_POSITION,
            "fullscreen video after seek",
            refresh = true,
            timeoutMs = BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        ) { position ->
            abs(position - positionBeforeSeek) >= MIN_SEEK_DELTA_MS &&
                abs(position - EXPECTED_PRIMARY_VIDEO_SEEK_MS) <= SEEK_TARGET_TOLERANCE_MS
        }
        assertCurrentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE, "fullscreen video after seek")
        val markerButton = ui.waitFor(BenchmarkSelectors.addMarkerButton, "add marker control")
        ui.clickNodeOrAncestor(markerButton)
        val markers = ui.waitForExactObjectCount(
            BenchmarkSelectors.videoMarker,
            4,
            "video marker"
        )
        val addedMarker = markers.singleOrNull { marker ->
            marker.contentDescription !in baselineMarkerDescriptions
        } ?: ui.fail("Could not identify the marker added after the three-marker baseline")
        val markerDescription = addedMarker.contentDescription
            ?: ui.fail("Added video marker has no content description")
        ui.dragHorizontally(addedMarker, left = false)
        ui.waitForMarkerDescriptionChange(markerDescription)
        ui.click(By.desc("Pause video"), "pause video")
        ui.waitForGone(BenchmarkSelectors.videoPlaybackReady, "video readiness after pause")
        ui.waitFor(By.desc("Play video"), "video play control after pause")
        val pausedPosition = ui.readPosition(
            FULLSCREEN_VIDEO_POSITION,
            "paused fullscreen video",
            refresh = true
        )
        SystemClock.sleep(PAUSED_POSITION_SAMPLE_DELAY_MS)
        val stablePausedPosition = ui.readPosition(
            FULLSCREEN_VIDEO_POSITION,
            "stable paused fullscreen video",
            refresh = true
        )
        if (abs(stablePausedPosition - pausedPosition) > POSITION_STABILITY_TOLERANCE_MS) {
            ui.fail("Paused video position moved from $pausedPosition to $stablePausedPosition ms")
        }
        assertCurrentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE, "paused fullscreen video")
        ui.click(By.desc("Play video"), "resume video")
        ui.waitFor(BenchmarkSelectors.videoPlaybackReady, "video readiness after resume")
        ui.waitFor(By.desc("Pause video"), "video pause control after resume")
        ui.waitForPosition(
            FULLSCREEN_VIDEO_POSITION,
            "resumed fullscreen video progress",
            refresh = true,
            timeoutMs = BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        ) { position -> position > maxOf(positionAfterSeek, stablePausedPosition) + MIN_RESUME_DELTA_MS }
        assertCurrentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE, "resumed fullscreen video")
    }

    fun fullscreenToInline() {
        ui.waitFor(
            BenchmarkSelectors.currentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE),
            "first video before fullscreen-to-inline transition"
        )
        val positionBeforeTransition = ui.waitForPosition(
            FULLSCREEN_VIDEO_POSITION,
            "fullscreen video before inline transition",
            refresh = true,
            timeoutMs = BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        ) { position -> position >= MIN_TRANSITION_BASELINE_POSITION_MS }
        ui.waitFor(BenchmarkSelectors.videoFullscreen, "video fullscreen")
        ui.clickSemantics(VIDEO_TOGGLE_FULLSCREEN, "fullscreen inline toggle")
        ui.waitForGone(BenchmarkSelectors.videoFullscreen, "video fullscreen overlay")
        ui.waitFor(BenchmarkSelectors.inlineVideo, "inline video after fullscreen")
        ui.waitFor(
            BenchmarkSelectors.videoPlaybackReady,
            "inline video playback after fullscreen",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        ui.waitFor(
            BenchmarkSelectors.currentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE),
            "same video after fullscreen-to-inline transition"
        )
        val positionAfterTransition = ui.readPosition(
            INLINE_VIDEO_POSITION,
            "inline video after fullscreen transition",
            refresh = true,
            timeoutMs = BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        val minimumAllowed = positionBeforeTransition - POSITION_CONTINUITY_TOLERANCE_MS
        val maximumAllowed = positionBeforeTransition + MAX_TRANSITION_ADVANCE_MS
        if (positionAfterTransition !in minimumAllowed..maximumAllowed) {
            ui.fail(
                "Video position left the documented fullscreen transition window: " +
                    "$positionBeforeTransition -> $positionAfterTransition ms; " +
                    "allowed=$minimumAllowed..$maximumAllowed"
            )
        }
    }

    fun swipeToNextAndPreviousVideo() {
        val surface = ui.waitFor(BenchmarkSelectors.videoFullscreen, "fullscreen video surface")
        ui.waitFor(
            BenchmarkSelectors.currentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE),
            "first video before swipe"
        )
        ui.horizontalSwipe(surface, left = true)
        ui.waitFor(
            BenchmarkSelectors.currentVideo(BenchmarkConfig.SECOND_VIDEO_TITLE),
            "second video after next swipe",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        ui.waitFor(BenchmarkSelectors.videoPlaybackReady, "second video first frame")
        ui.horizontalSwipe(
            ui.waitFor(BenchmarkSelectors.videoFullscreen, "fullscreen video surface after next swipe"),
            left = false
        )
        ui.waitFor(
            BenchmarkSelectors.currentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE),
            "first video after previous swipe",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
        ui.waitFor(BenchmarkSelectors.videoPlaybackReady, "first video first frame after return")
    }

    fun settingsScrollDuringPlayback() {
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "audio before Settings")
        val positionBeforeSettings = ui.readPosition(AUDIO_POSITION, "audio before Settings")
        openSettings()
        ui.waitFor(BenchmarkSelectors.audioPlaybackReady, "audio playback after opening Settings")
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "audio after opening Settings")
        val positionAtSettings = ui.readPosition(AUDIO_POSITION, "audio at Settings")
        if (positionAtSettings < positionBeforeSettings) {
            ui.fail("Audio position reset while opening Settings: $positionBeforeSettings -> $positionAtSettings ms")
        }
        ui.click(BenchmarkSelectors.settingsTabMedia, "Media settings tab during playback")
        ui.waitFor(BenchmarkSelectors.settingsContentMedia, "Media settings content during playback")
        ui.click(BenchmarkSelectors.settingsTabDesign, "Design settings tab during playback")
        ui.waitFor(BenchmarkSelectors.settingsContentDesign, "Design settings content during playback")
        ui.flingDownAndBack(BenchmarkSelectors.settingsScroll)
        ui.click(BenchmarkSelectors.settingsTabSystem, "System settings tab during playback")
        ui.waitFor(BenchmarkSelectors.settingsContentSystem, "System settings content during playback")
        ui.click(BenchmarkSelectors.settingsTabGeneral, "General settings tab during playback")
        ui.waitFor(BenchmarkSelectors.settingsContentGeneral, "General settings content during playback")
        ui.waitFor(BenchmarkSelectors.audioPlaybackReady, "audio playback during Settings")
        assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "audio during Settings")
        ui.waitForPosition(
            AUDIO_POSITION,
            "audio progress during Settings",
            timeoutMs = BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        ) { position -> position > positionAtSettings + MIN_RESUME_DELTA_MS }
    }

    fun audioPlaylistScroll() {
        ui.scrollBetweenTextAnchors(
            BenchmarkConfig.FIRST_PLAYLIST_AUDIO_TITLE,
            BenchmarkConfig.LAST_PLAYLIST_AUDIO_TITLE
        )
    }

    fun videoPlaylistScroll() {
        ui.scrollBetweenTextAnchors(
            BenchmarkConfig.FIRST_PLAYLIST_VIDEO_TITLE,
            BenchmarkConfig.LAST_PLAYLIST_VIDEO_TITLE
        )
    }

    fun idleWindow(
        journey: TraceJourney,
        durationMs: Long = BenchmarkConfig.IDLE_TRACE_WINDOW_MS,
        restartFirstAudioFromBrowser: Boolean = false
    ) {
        if (restartFirstAudioFromBrowser) {
            ui.click(BenchmarkSelectors.audioFile(BenchmarkConfig.FIRST_AUDIO), "restart first audio before idle")
            ui.waitFor(
                BenchmarkSelectors.audioPlaybackReady,
                "restarted audio before ${journey.sectionName} idle",
                BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
            )
            assertCurrentAudio(BenchmarkConfig.FIRST_AUDIO_TITLE, "restarted audio before idle")
        }
        val audioExpectation = audioIdleExpectation(journey)
        val videoPositionSelector = videoIdlePositionSelector(journey)
        val expectedAudioTitle = expectedAudioTitle(journey)
        val positionBefore = audioExpectation?.let {
            checkNotNull(expectedAudioTitle)
            assertCurrentAudio(expectedAudioTitle, "audio before ${journey.sectionName} idle")
            if (it == AudioIdleExpectation.PROGRESSING) {
                ui.waitFor(BenchmarkSelectors.audioPlaybackReady, "playing audio before ${journey.sectionName}")
            } else {
                ui.waitForGone(BenchmarkSelectors.audioPlaybackReady, "playing audio before paused idle")
            }
            ui.readPosition(AUDIO_POSITION, "audio before ${journey.sectionName} idle")
        }
        val videoPositionBefore = videoPositionSelector?.let { selector ->
            assertCurrentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE, "video before ${journey.sectionName} idle")
            ui.waitFor(BenchmarkSelectors.videoPlaybackReady, "playing video before ${journey.sectionName}")
            ui.readPosition(selector, "video before ${journey.sectionName} idle", refresh = true)
        }
        measuredWindow(journey) {
            SystemClock.sleep(durationMs)
        }
        if (audioExpectation != null && positionBefore != null) {
            assertCurrentAudio(checkNotNull(expectedAudioTitle), "audio after ${journey.sectionName} idle")
            val positionAfter = ui.readPosition(
                AUDIO_POSITION,
                "audio after ${journey.sectionName} idle"
            )
            when (audioExpectation) {
                AudioIdleExpectation.PROGRESSING -> {
                    ui.waitFor(BenchmarkSelectors.audioPlaybackReady, "playing audio after ${journey.sectionName}")
                    val progress = audioProgressAcrossWindow(journey, positionBefore, positionAfter)
                    if (progress < minimumIdleProgress(durationMs)) {
                        ui.fail(
                            "Playing audio did not progress sufficiently during ${journey.sectionName}: " +
                                "$positionBefore -> $positionAfter ms (effective progress $progress ms)"
                        )
                    }
                }
                AudioIdleExpectation.STABLE -> {
                    ui.waitForGone(BenchmarkSelectors.audioPlaybackReady, "playing audio after paused idle")
                    if (abs(positionAfter - positionBefore) > POSITION_STABILITY_TOLERANCE_MS) {
                        ui.fail(
                            "Paused audio was not stable during ${journey.sectionName}: " +
                                "$positionBefore -> $positionAfter ms"
                        )
                    }
                }
            }
        }
        if (videoPositionSelector != null && videoPositionBefore != null) {
            assertCurrentVideo(BenchmarkConfig.FIRST_VIDEO_TITLE, "video after ${journey.sectionName} idle")
            ui.waitFor(BenchmarkSelectors.videoPlaybackReady, "playing video after ${journey.sectionName}")
            val videoPositionAfter = ui.readPosition(
                videoPositionSelector,
                "video after ${journey.sectionName} idle",
                refresh = true
            )
            val videoProgress = if (videoPositionAfter >= videoPositionBefore) {
                videoPositionAfter - videoPositionBefore
            } else {
                VIDEO_FIXTURE_DURATION_MS - videoPositionBefore + videoPositionAfter
            }
            if (videoProgress < minimumIdleProgress(durationMs)) {
                ui.fail(
                    "Playing video did not progress sufficiently during ${journey.sectionName}: " +
                        "$videoPositionBefore -> $videoPositionAfter ms " +
                        "(effective progress $videoProgress ms)"
                )
            }
            when (journey) {
                TraceJourney.VIDEO_MARKERS_ON -> {
                    ui.waitFor(VIDEO_CONTROLS_PINNED, "pinned marker-on controls after idle")
                    ui.waitForExactObjectCount(BenchmarkSelectors.videoMarker, 3, "video marker after marker-on idle")
                }
                TraceJourney.VIDEO_MARKERS_OFF -> {
                    ui.waitFor(VIDEO_CONTROLS_PINNED, "pinned marker-off controls after idle")
                    ui.waitForExactObjectCount(BenchmarkSelectors.videoMarker, 0, "video marker after marker-off idle")
                }
                else -> Unit
            }
        }
    }

    fun measuredWindow(journey: TraceJourney, action: BenchmarkJourneys.() -> Unit) {
        Trace.beginSection(journey.sectionName)
        try {
            action()
        } finally {
            Trace.endSection()
            // Macrobenchmark stops capture immediately after the measure block. Give the
            // synchronous end marker a small trace-only delivery window so the final
            // iteration cannot be persisted as an open slice (dur = -1).
            SystemClock.sleep(TRACE_SECTION_END_SETTLE_MS)
        }
    }

    private fun audioIdleExpectation(journey: TraceJourney): AudioIdleExpectation? = when (journey) {
        TraceJourney.BROWSER_AUDIO_IDLE,
        TraceJourney.AUDIO_MINI_PLAYER,
        TraceJourney.AUDIO_FULL_PLAYER,
        TraceJourney.AUDIO_REPEAT_OFF,
        TraceJourney.AUDIO_REPEAT_ONE,
        TraceJourney.AUDIO_MARQUEE_OFF,
        TraceJourney.AUDIO_MARQUEE_ON -> AudioIdleExpectation.PROGRESSING
        TraceJourney.AUDIO_PAUSED -> AudioIdleExpectation.STABLE
        else -> null
    }

    private fun expectedAudioTitle(journey: TraceJourney): String? = when (journey) {
        TraceJourney.AUDIO_MARQUEE_OFF,
        TraceJourney.AUDIO_MARQUEE_ON -> BenchmarkConfig.LONG_TITLE_AUDIO_TITLE
        TraceJourney.BROWSER_AUDIO_IDLE,
        TraceJourney.AUDIO_MINI_PLAYER,
        TraceJourney.AUDIO_FULL_PLAYER,
        TraceJourney.AUDIO_PAUSED,
        TraceJourney.AUDIO_REPEAT_OFF,
        TraceJourney.AUDIO_REPEAT_ONE -> BenchmarkConfig.FIRST_AUDIO_TITLE
        else -> null
    }

    private fun videoIdlePositionSelector(journey: TraceJourney) = when (journey) {
        TraceJourney.VIDEO_INLINE -> INLINE_VIDEO_POSITION
        TraceJourney.VIDEO_FULLSCREEN,
        TraceJourney.VIDEO_MARKERS_OFF,
        TraceJourney.VIDEO_MARKERS_ON -> FULLSCREEN_VIDEO_POSITION
        else -> null
    }

    private fun assertCurrentAudio(title: String, context: String) {
        ui.waitFor(
            BenchmarkSelectors.currentAudio(title),
            "$context media ID '$title'",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
    }

    private fun assertCurrentVideo(title: String, context: String) {
        ui.waitFor(
            BenchmarkSelectors.currentVideo(title),
            "$context media ID '$title'",
            BenchmarkConfig.PLAYBACK_READY_TIMEOUT_MS
        )
    }

    private fun minimumIdleProgress(durationMs: Long): Long =
        (durationMs / 3L).coerceAtLeast(MIN_RESUME_DELTA_MS)

    private fun audioProgressAcrossWindow(
        journey: TraceJourney,
        positionBefore: Long,
        positionAfter: Long
    ): Long {
        if (positionAfter >= positionBefore) return positionAfter - positionBefore
        return if (journey in REPEATING_AUDIO_JOURNEYS) {
            AUDIO_FIXTURE_DURATION_MS - positionBefore + positionAfter
        } else {
            positionAfter - positionBefore
        }
    }

    private enum class AudioIdleExpectation {
        PROGRESSING,
        STABLE
    }

    private companion object {
        val BROWSER_READY = By.res("newaudio_browser_ready")
        val AUDIO_POSITION = By.res("newaudio_audio_position")
        val INLINE_VIDEO_POSITION = By.res("newaudio_inline_video_position")
        val FULLSCREEN_VIDEO_POSITION = By.res("newaudio_fullscreen_video_position")
        val VIDEO_CONTROLS_VISIBLE = By.res("newaudio_video_controls_visible")
        val VIDEO_TOGGLE_FULLSCREEN = By.res("newaudio_video_toggle_fullscreen")
        val VIDEO_CONTROLS_PIN = By.res("newaudio_video_controls_pin")
        val VIDEO_CONTROLS_PINNED = By.res("newaudio_video_controls_pinned")
        val VIDEO_MARKERS_READY = By.res("newaudio_video_markers_ready")
        val ALBUM_ART_LOAD_COMPLETE = By.res("newaudio_album_art_load_complete")

        const val MIN_SEEK_DELTA_MS = 1_000L
        const val AUDIO_FIXTURE_DURATION_MS = 20_000L
        const val VIDEO_FIXTURE_DURATION_MS = 20_000L
        const val EXPECTED_PRIMARY_AUDIO_SEEK_MS = 13_023L
        const val EXPECTED_PRIMARY_VIDEO_SEEK_MS = 13_015L
        const val SEEK_TARGET_TOLERANCE_MS = 2_500L
        const val MIN_RESUME_DELTA_MS = 200L
        const val MIN_TRANSITION_BASELINE_POSITION_MS = 1_500L
        const val PAUSED_POSITION_SAMPLE_DELAY_MS = 500L
        const val POSITION_STABILITY_TOLERANCE_MS = 250L
        const val POSITION_CONTINUITY_TOLERANCE_MS = 500L
        const val MAX_TRANSITION_ADVANCE_MS = 5_000L
        const val TRACE_SECTION_END_SETTLE_MS = 100L
        val REPEATING_AUDIO_JOURNEYS = setOf(
            TraceJourney.AUDIO_FULL_PLAYER,
            TraceJourney.AUDIO_REPEAT_ONE,
            TraceJourney.AUDIO_MARQUEE_OFF,
            TraceJourney.AUDIO_MARQUEE_ON
        )
    }
}
