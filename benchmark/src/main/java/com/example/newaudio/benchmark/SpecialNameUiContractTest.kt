package com.example.newaudio.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SpecialNameUiContractTest {
    private val ui = BenchmarkDevice()

    @Before
    fun seedFixtures() {
        BenchmarkFixtures(ui).seedAll()
    }

    @Test
    fun unicodeUnderscoreAndPercentFixturesAreVisibleAndClickable() {
        val audioCases = listOf(
            SpecialFixture(listOf("Albums", "One"), "Audio_%_Literal.wav"),
            SpecialFixture(listOf("Albums", "Two"), "Audio_underscore_.wav"),
            SpecialFixture(listOf("Unicode"), "Audio_Ünicode_你好.wav")
        )
        audioCases.forEach { fixture ->
            launchFresh()
            ensureMusicMode()
            openFolders(fixture.folders)
            ui.click(BenchmarkSelectors.audioFile(fixture.filename), "special audio ${fixture.filename}")
            ui.waitFor(BenchmarkSelectors.miniPlayer, "mini player for ${fixture.filename}")
        }

        val videoCases = listOf(
            SpecialFixture(listOf("Clips", "One"), "Video_%_Literal.mp4"),
            SpecialFixture(listOf("Clips", "Two"), "Video_underscore_.mp4"),
            SpecialFixture(listOf("Unicode"), "Video_Ünicode_你好.mp4")
        )
        videoCases.forEach { fixture ->
            launchFresh()
            ensureVideoMode()
            openFolders(fixture.folders)
            ui.click(BenchmarkSelectors.videoFile(fixture.filename), "special video ${fixture.filename}")
            ui.waitFor(BenchmarkSelectors.inlineVideo, "inline video for ${fixture.filename}")
        }
    }

    private fun launchFresh() {
        ui.device.executeShellCommand("am force-stop ${BenchmarkConfig.TARGET_PACKAGE}")
        val context = InstrumentationRegistry.getInstrumentation().context
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(
                    ComponentName(
                        BenchmarkConfig.TARGET_PACKAGE,
                        "${BenchmarkConfig.TARGET_PACKAGE}.MainActivity"
                    )
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        ui.waitFor(BenchmarkSelectors.browserRoot, "browser after fresh launch")
    }

    private fun ensureMusicMode() {
        if (ui.device.findObject(BenchmarkSelectors.text("Music")) == null) {
            ui.click(BenchmarkSelectors.text("Video"), "switch to music")
        }
        ui.waitFor(BenchmarkSelectors.browserList, "music browser")
    }

    private fun ensureVideoMode() {
        if (ui.device.findObject(BenchmarkSelectors.text("Video")) == null) {
            ui.click(BenchmarkSelectors.text("Music"), "switch to video")
        }
        ui.waitForAny(
            listOf(
                BenchmarkSelectors.browserList to "video list",
                BenchmarkSelectors.videoGallery to "video gallery"
            )
        )
    }

    private fun openFolders(folders: List<String>) {
        folders.forEach { folder ->
            ui.click(BenchmarkSelectors.folder(folder), "fixture folder $folder")
        }
    }

    private data class SpecialFixture(
        val folders: List<String>,
        val filename: String
    )
}
