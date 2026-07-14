package com.example.newaudio.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import java.util.regex.Pattern

internal object BenchmarkSelectors {
    val appRoot: BySelector = tag("newaudio_app_root")
    val browserRoot: BySelector = tag("newaudio_browser_root")
    val browserList: BySelector = tag("newaudio_browser_list")
    val videoGallery: BySelector = By.res(Pattern.compile("newaudio_browser_gallery_[234]"))
    val miniPlayer: BySelector = tag("newaudio_mini_player")
    val fullPlayer: BySelector = tag("newaudio_full_player")
    val settingsRoot: BySelector = tag("newaudio_settings")
    val settingsScroll: BySelector = tag("newaudio_settings_scroll")
    val settingsTabGeneral: BySelector = tag("newaudio_settings_tab_general")
    val settingsTabMedia: BySelector = tag("newaudio_settings_tab_media")
    val settingsTabDesign: BySelector = tag("newaudio_settings_tab_design")
    val settingsTabSystem: BySelector = tag("newaudio_settings_tab_system")
    val settingsContentGeneral: BySelector = tag("newaudio_settings_content_general")
    val settingsContentMedia: BySelector = tag("newaudio_settings_content_media")
    val settingsContentDesign: BySelector = tag("newaudio_settings_content_design")
    val settingsContentSystem: BySelector = tag("newaudio_settings_content_system")
    val playlistRoot: BySelector = tag("newaudio_playlist")
    val inlineVideo: BySelector = tag("newaudio_inline_video")
    val videoFullscreen: BySelector = tag("newaudio_video_fullscreen")
    val audioPlaybackReady: BySelector = tag("newaudio_audio_playback_ready")
    val videoPlaybackReady: BySelector = tag("newaudio_video_playback_ready")
    val albumArtReady: BySelector = tag("newaudio_album_art_ready")

    val settingsButton: BySelector = By.desc("Settings")
    val playlistButton: BySelector = By.desc("Open playlist manager")
    val backButton: BySelector = By.desc("Back")
    val playPauseButton: BySelector = By.desc("Play/Pause")
    val nextButton: BySelector = By.desc("Next")
    val playerSeekBar: BySelector = By.desc("Playback position")
    val videoPlayPauseButton: BySelector = By.desc(Pattern.compile("Play video|Pause video"))
    val addMarkerButton: BySelector = By.desc("Add marker")
    val videoMarker: BySelector = By.desc(Pattern.compile("Marker at .+"))
    val scrollable: BySelector = By.scrollable(true)

    fun text(value: String): BySelector = By.text(value)
    fun text(pattern: Pattern): BySelector = By.text(pattern)
    fun description(value: String): BySelector = By.desc(value)
    fun audioFile(name: String): BySelector = text(name)
    fun videoFile(name: String): BySelector = text(name)
    fun videoGalleryColumns(columns: Int): BySelector = tag("newaudio_browser_gallery_$columns")
    fun currentVideo(title: String): BySelector = tag("newaudio_video_current_$title")
    fun currentAudio(title: String): BySelector = tag("newaudio_audio_current_$title")
    fun folder(name: String): BySelector = By.desc("Folder: $name")

    // Compose exposes testTag verbatim as viewIdResourceName when
    // testTagsAsResourceId is enabled; it is not an Android R.id resource.
    private fun tag(id: String): BySelector = By.res(id)
}
