package com.example.newaudio.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.newaudio.ui.NewAudioTestTags
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class VideoDragMode {
    UNDECIDED,
    HORIZONTAL,
    BRIGHTNESS,
    VOLUME
}

internal enum class VideoSwipeAction {
    NONE,
    NEXT,
    PREVIOUS
}

internal enum class VideoGestureFeedbackType {
    BRIGHTNESS,
    VOLUME
}

internal data class VideoGestureFeedback(
    val type: VideoGestureFeedbackType,
    val percent: Int
)

internal interface VideoGestureControl {
    fun readWindowBrightness(): Float

    fun writeWindowBrightness(brightness: Float)

    fun readMediaVolume(): Int

    fun readMaxMediaVolume(): Int

    fun writeMediaVolume(volume: Int)
}

internal fun resolveVideoDragMode(
    currentMode: VideoDragMode,
    startX: Float,
    surfaceWidth: Float,
    totalDragX: Float,
    totalDragY: Float,
    directionLockThresholdPx: Float = VIDEO_DIRECTION_LOCK_THRESHOLD_PX
): VideoDragMode {
    if (currentMode != VideoDragMode.UNDECIDED) return currentMode
    if (surfaceWidth <= 0f) return VideoDragMode.UNDECIDED
    if (maxOf(abs(totalDragX), abs(totalDragY)) < directionLockThresholdPx) {
        return VideoDragMode.UNDECIDED
    }

    return when {
        abs(totalDragX) > abs(totalDragY) -> VideoDragMode.HORIZONTAL
        startX < surfaceWidth / 2f -> VideoDragMode.BRIGHTNESS
        else -> VideoDragMode.VOLUME
    }
}

internal fun calculateVideoBrightness(
    startBrightness: Float,
    totalDragY: Float,
    surfaceHeight: Float,
    sensitivity: Float = VIDEO_BRIGHTNESS_SENSITIVITY
): Float {
    val safeStart = startBrightness.coerceIn(MIN_WINDOW_BRIGHTNESS, MAX_WINDOW_BRIGHTNESS)
    if (surfaceHeight <= 0f) return safeStart

    val delta = (-totalDragY / surfaceHeight) * sensitivity
    return (safeStart + delta).coerceIn(MIN_WINDOW_BRIGHTNESS, MAX_WINDOW_BRIGHTNESS)
}

internal fun calculateVideoVolume(
    startVolume: Int,
    totalDragY: Float,
    surfaceHeight: Float,
    maxVolume: Int,
    sensitivity: Float = VIDEO_VOLUME_SENSITIVITY
): Int {
    if (maxVolume <= 0) return 0
    val safeStart = startVolume.coerceIn(0, maxVolume)
    if (surfaceHeight <= 0f) return safeStart

    val delta = ((-totalDragY / surfaceHeight) * maxVolume * sensitivity).roundToInt()
    return (safeStart + delta).coerceIn(0, maxVolume)
}

internal fun resolveVideoSwipeAction(
    totalDragX: Float,
    swipeThresholdPx: Float = VIDEO_SWIPE_THRESHOLD_PX
): VideoSwipeAction = when {
    totalDragX <= -swipeThresholdPx -> VideoSwipeAction.NEXT
    totalDragX >= swipeThresholdPx -> VideoSwipeAction.PREVIOUS
    else -> VideoSwipeAction.NONE
}

internal fun normalizeWindowBrightness(rawBrightness: Float): Float =
    if (rawBrightness >= 0f) {
        rawBrightness.coerceIn(MIN_WINDOW_BRIGHTNESS, MAX_WINDOW_BRIGHTNESS)
    } else {
        DEFAULT_WINDOW_BRIGHTNESS
    }

@Composable
internal fun VideoGestureInputSurface(
    onDoubleTap: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    controlOverride: VideoGestureControl? = null
) {
    val platformControl = rememberPlatformVideoGestureControl()
    val control = controlOverride ?: platformControl
    val previousWindowBrightness = remember(control) { control.readWindowBrightness() }
    var brightnessValue by remember(control) {
        mutableFloatStateOf(normalizeWindowBrightness(previousWindowBrightness))
    }
    var brightnessStartValue by remember { mutableFloatStateOf(brightnessValue) }
    var volumeStartValue by remember { mutableIntStateOf(0) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragMode by remember { mutableStateOf(VideoDragMode.UNDECIDED) }
    var feedback by remember { mutableStateOf<VideoGestureFeedback?>(null) }

    DisposableEffect(control, previousWindowBrightness) {
        onDispose {
            control.writeWindowBrightness(previousWindowBrightness)
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(VIDEO_GESTURE_FEEDBACK_DURATION_MS)
            feedback = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTag)
            .pointerInput(onTap, onDoubleTap) {
                detectTapGestures(
                    // detectDragGestures reports its start only after touch slop.
                    // Capture the real down position so crossing the center before
                    // that point cannot change the gesture's left/right ownership.
                    onPress = { offset -> dragStartX = offset.x },
                    onTap = { onTap?.invoke() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
            .pointerInput(onSwipeNext, onSwipePrevious, control) {
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                        dragMode = VideoDragMode.UNDECIDED
                        brightnessValue = normalizeWindowBrightness(control.readWindowBrightness())
                        brightnessStartValue = brightnessValue
                        volumeStartValue = control.readMediaVolume()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                        dragMode = resolveVideoDragMode(
                            currentMode = dragMode,
                            startX = dragStartX,
                            surfaceWidth = size.width.toFloat(),
                            totalDragX = totalDragX,
                            totalDragY = totalDragY
                        )

                        when (dragMode) {
                            VideoDragMode.BRIGHTNESS -> {
                                val newBrightness = calculateVideoBrightness(
                                    startBrightness = brightnessStartValue,
                                    totalDragY = totalDragY,
                                    surfaceHeight = size.height.toFloat()
                                )
                                brightnessValue = newBrightness
                                control.writeWindowBrightness(newBrightness)
                                feedback = VideoGestureFeedback(
                                    type = VideoGestureFeedbackType.BRIGHTNESS,
                                    percent = (newBrightness * 100).roundToInt()
                                )
                            }

                            VideoDragMode.VOLUME -> {
                                val maxVolume = control.readMaxMediaVolume().coerceAtLeast(0)
                                val newVolume = calculateVideoVolume(
                                    startVolume = volumeStartValue,
                                    totalDragY = totalDragY,
                                    surfaceHeight = size.height.toFloat(),
                                    maxVolume = maxVolume
                                )
                                control.writeMediaVolume(newVolume)
                                feedback = VideoGestureFeedback(
                                    type = VideoGestureFeedbackType.VOLUME,
                                    percent = if (maxVolume > 0) {
                                        ((newVolume.toFloat() / maxVolume) * 100).roundToInt()
                                    } else {
                                        0
                                    }
                                )
                            }

                            VideoDragMode.HORIZONTAL,
                            VideoDragMode.UNDECIDED -> Unit
                        }
                    },
                    onDragEnd = {
                        if (dragMode == VideoDragMode.HORIZONTAL || dragMode == VideoDragMode.UNDECIDED) {
                            when (resolveVideoSwipeAction(totalDragX)) {
                                VideoSwipeAction.NEXT -> onSwipeNext()
                                VideoSwipeAction.PREVIOUS -> onSwipePrevious()
                                VideoSwipeAction.NONE -> Unit
                            }
                        }
                        totalDragX = 0f
                        totalDragY = 0f
                        dragMode = VideoDragMode.UNDECIDED
                    },
                    onDragCancel = {
                        totalDragX = 0f
                        totalDragY = 0f
                        dragMode = VideoDragMode.UNDECIDED
                        feedback = null
                    }
                )
            }
    ) {
        feedback?.let { currentFeedback ->
            VideoGestureFeedbackView(
                feedback = currentFeedback,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun rememberPlatformVideoGestureControl(): VideoGestureControl {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    return remember(activity, audioManager) {
        object : VideoGestureControl {
            override fun readWindowBrightness(): Float =
                activity?.window?.attributes?.screenBrightness ?: -1f

            override fun writeWindowBrightness(brightness: Float) {
                activity?.setWindowBrightness(brightness)
            }

            override fun readMediaVolume(): Int =
                audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0

            override fun readMaxMediaVolume(): Int =
                audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

            override fun writeMediaVolume(volume: Int) {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            }
        }
    }
}

@Composable
internal fun VideoGestureFeedbackView(
    feedback: VideoGestureFeedback,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.testTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK)) {
        Surface(
            modifier = Modifier.testTag(
                when (feedback.type) {
                    VideoGestureFeedbackType.BRIGHTNESS ->
                        NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_BRIGHTNESS
                    VideoGestureFeedbackType.VOLUME ->
                        NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_VOLUME
                }
            ),
            color = Color.Black.copy(alpha = 0.68f),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val (icon, iconTestTag) = when (feedback.type) {
                    VideoGestureFeedbackType.BRIGHTNESS ->
                        Icons.Default.BrightnessHigh to
                            NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_ICON_BRIGHTNESS
                    VideoGestureFeedbackType.VOLUME -> {
                        if (feedback.percent <= 0) {
                            Icons.AutoMirrored.Filled.VolumeOff to
                                NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_ICON_MUTED
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp to
                                NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_ICON_VOLUME
                        }
                    }
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(iconTestTag)
                )
                Text(
                    text = "${feedback.percent}%",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_PERCENT)
                )
            }
        }
    }
}

internal fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Activity.setWindowBrightness(brightness: Float) {
    val attributes = window.attributes
    attributes.screenBrightness = brightness
    window.attributes = attributes
}

internal const val VIDEO_DIRECTION_LOCK_THRESHOLD_PX = 24f
internal const val VIDEO_SWIPE_THRESHOLD_PX = 96f
internal const val VIDEO_BRIGHTNESS_SENSITIVITY = 1.25f
internal const val VIDEO_VOLUME_SENSITIVITY = 1.5f
internal const val MIN_WINDOW_BRIGHTNESS = 0.05f
internal const val MAX_WINDOW_BRIGHTNESS = 1.0f
internal const val DEFAULT_WINDOW_BRIGHTNESS = 0.5f
internal const val VIDEO_GESTURE_FEEDBACK_DURATION_MS = 900L
