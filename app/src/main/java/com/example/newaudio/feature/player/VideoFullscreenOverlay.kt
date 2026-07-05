package com.example.newaudio.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.newaudio.domain.model.VideoMarker
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
@OptIn(UnstableApi::class)
fun VideoFullscreenOverlay(
    player: Player,
    onToggleFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    markersEnabled: Boolean = false,
    markers: ImmutableList<VideoMarker> = persistentListOf(),
    onAddMarker: (Long) -> Unit = {},
    onMoveMarker: (Long, Long) -> Unit = { _, _ -> },
    onDeleteMarker: (Long) -> Unit = {},
    onPlayerViewChanged: (PlayerView?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember(audioManager) {
        audioManager
            ?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            ?.takeIf { it > 0 }
            ?: 1
    }
    var requestedOrientation by remember(player) {
        mutableStateOf(player.videoSize.toRequestedOrientation())
    }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    var dragMode by remember { mutableStateOf(FullscreenDragMode.UNDECIDED) }
    var dragStartedInTopHalf by remember { mutableStateOf(true) }
    var brightnessValue by remember(activity) {
        mutableFloatStateOf(activity.currentWindowBrightnessOrDefault())
    }
    var brightnessStartValue by remember { mutableFloatStateOf(brightnessValue) }
    var volumeStartValue by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<FullscreenGestureFeedback?>(null) }
    var timelineVisible by remember { mutableStateOf(false) }
    var timelineInteractionNonce by remember { mutableIntStateOf(0) }
    var timelinePositionMs by remember(player) {
        mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
    }
    var timelineDurationMs by remember(player) {
        mutableLongStateOf(player.duration.validDurationMs())
    }
    var isSeekingTimeline by remember { mutableStateOf(false) }
    var isPlayerPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var videoResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    val swipeThresholdPx = 96f
    val directionLockThresholdPx = 24f

    BackHandler(onBack = onExitFullscreen)

    DisposableEffect(currentPlayerView) {
        val playerView = currentPlayerView
        if (playerView != null) {
            onPlayerViewChanged(playerView)
        }

        onDispose {
            if (playerView != null) {
                onPlayerViewChanged(null)
            }
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(900)
            feedback = null
        }
    }

    LaunchedEffect(timelineVisible, timelineInteractionNonce) {
        if (timelineVisible) {
            delay(3_000)
            timelineVisible = false
            isSeekingTimeline = false
        }
    }

    LaunchedEffect(timelineVisible, isSeekingTimeline, player) {
        while (timelineVisible) {
            timelineDurationMs = player.duration.validDurationMs()
            if (!isSeekingTimeline) {
                timelinePositionMs = player.currentPosition.coerceIn(0L, timelineDurationMs)
            }
            delay(250)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(newVideoSize: VideoSize) {
                val orientation = newVideoSize.toRequestedOrientation()
                if (orientation != null) {
                    requestedOrientation = orientation
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayerPlaying = isPlaying
            }
        }

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(activity, requestedOrientation) {
        val orientation = requestedOrientation
        if (activity != null && orientation != null) {
            activity.requestedOrientation = orientation
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(activity) {
        if (activity == null) {
            onDispose {}
        } else {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)

            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())

            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
    }

    DisposableEffect(activity) {
        if (activity == null) {
            onDispose {}
        } else {
            val previousBrightness = activity.window.attributes.screenBrightness

            onDispose {
                activity.setWindowBrightness(previousBrightness)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    resizeMode = videoResizeMode
                    useController = false
                    currentPlayerView = this
                }
            },
            update = { playerView ->
                playerView.resizeMode = videoResizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onToggleFullscreen) {
                    detectTapGestures(
                        onTap = {
                            timelineVisible = true
                            timelineInteractionNonce++
                        },
                        onDoubleTap = { onToggleFullscreen() }
                    )
                }
                .pointerInput(onSwipeNext, onSwipePrevious) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            totalDragX = 0f
                            totalDragY = 0f
                            dragMode = FullscreenDragMode.UNDECIDED
                            dragStartedInTopHalf = offset.y < size.height / 2f
                            brightnessStartValue = brightnessValue
                            volumeStartValue = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            if (dragMode == FullscreenDragMode.UNDECIDED &&
                                maxOf(abs(totalDragX), abs(totalDragY)) >= directionLockThresholdPx
                            ) {
                                dragMode = if (abs(totalDragX) > abs(totalDragY)) {
                                    FullscreenDragMode.HORIZONTAL
                                } else if (dragStartedInTopHalf) {
                                    FullscreenDragMode.BRIGHTNESS
                                } else {
                                    FullscreenDragMode.VOLUME
                                }
                            }

                            when (dragMode) {
                                FullscreenDragMode.BRIGHTNESS -> {
                                    val delta = (-totalDragY / size.height) * 1.25f
                                    val newBrightness = (brightnessStartValue + delta).coerceIn(
                                        MIN_WINDOW_BRIGHTNESS,
                                        MAX_WINDOW_BRIGHTNESS
                                    )
                                    brightnessValue = newBrightness
                                    activity?.setWindowBrightness(newBrightness)
                                    feedback = FullscreenGestureFeedback(
                                        type = FullscreenGestureFeedbackType.BRIGHTNESS,
                                        percent = (newBrightness * 100).roundToInt()
                                    )
                                }
                                FullscreenDragMode.VOLUME -> {
                                    val volumeDelta = ((-totalDragY / size.height) * maxVolume * 1.5f).roundToInt()
                                    val newVolume = (volumeStartValue + volumeDelta).coerceIn(0, maxVolume)
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                                    feedback = FullscreenGestureFeedback(
                                        type = FullscreenGestureFeedbackType.VOLUME,
                                        percent = ((newVolume.toFloat() / maxVolume) * 100).roundToInt()
                                    )
                                }
                                FullscreenDragMode.HORIZONTAL,
                                FullscreenDragMode.UNDECIDED -> Unit
                            }
                        },
                        onDragEnd = {
                            if (dragMode == FullscreenDragMode.HORIZONTAL || dragMode == FullscreenDragMode.UNDECIDED) {
                                when {
                                    totalDragX <= -swipeThresholdPx -> onSwipeNext()
                                    totalDragX >= swipeThresholdPx -> onSwipePrevious()
                                }
                            }
                            totalDragX = 0f
                            totalDragY = 0f
                            dragMode = FullscreenDragMode.UNDECIDED
                        },
                        onDragCancel = {
                            totalDragX = 0f
                            totalDragY = 0f
                            dragMode = FullscreenDragMode.UNDECIDED
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectFullscreenPinchResize(
                        onZoomIn = {
                            videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        },
                        onZoomOut = {
                            videoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    )
                }
        )

        feedback?.let { currentFeedback ->
            FullscreenGestureFeedbackView(
                feedback = currentFeedback,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (timelineVisible) {
                VideoTimelineOverlay(
                    positionMs = timelinePositionMs,
                    durationMs = timelineDurationMs,
                    isPlaying = isPlayerPlaying,
                    onSeekPreview = { position ->
                        isSeekingTimeline = true
                        timelinePositionMs = position
                        timelineInteractionNonce++
                    },
                onSeekFinished = {
                    player.seekTo(timelinePositionMs.coerceIn(0L, timelineDurationMs))
                        isSeekingTimeline = false
                        timelineInteractionNonce++
                    },
                    onPlayPause = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        isPlayerPlaying = player.isPlaying
                        timelineInteractionNonce++
                    },
                    markersEnabled = markersEnabled,
                    markers = markers,
                    onAddMarker = {
                        onAddMarker(timelinePositionMs.coerceIn(0L, timelineDurationMs))
                        timelineInteractionNonce++
                    },
                    onMoveMarker = { markerId, positionMs ->
                        onMoveMarker(markerId, positionMs.coerceIn(0L, timelineDurationMs))
                        timelineInteractionNonce++
                    },
                    onDeleteNearestMarker = {
                        markers.nearestTo(timelinePositionMs)?.let { marker ->
                            onDeleteMarker(marker.id)
                            timelineInteractionNonce++
                        }
                    },
                    onJumpToPreviousMarker = {
                        markers.previousMarkerPosition(timelinePositionMs)?.let { position ->
                            player.seekTo(position.coerceIn(0L, timelineDurationMs))
                            timelinePositionMs = position.coerceIn(0L, timelineDurationMs)
                            timelineInteractionNonce++
                        }
                    },
                    onJumpToNextMarker = {
                        markers.nextMarkerPosition(timelinePositionMs)?.let { position ->
                            player.seekTo(position.coerceIn(0L, timelineDurationMs))
                            timelinePositionMs = position.coerceIn(0L, timelineDurationMs)
                            timelineInteractionNonce++
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 28.dp)
            )
        }
    }
}

@Composable
private fun VideoTimelineOverlay(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onPlayPause: () -> Unit,
    markersEnabled: Boolean,
    markers: ImmutableList<VideoMarker>,
    onAddMarker: () -> Unit,
    onMoveMarker: (Long, Long) -> Unit,
    onDeleteNearestMarker: () -> Unit,
    onJumpToPreviousMarker: () -> Unit,
    onJumpToNextMarker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                )
                {
                    Slider(
                        value = positionMs.coerceIn(0L, durationMs).toFloat(),
                        onValueChange = { value ->
                            onSeekPreview(value.toLong().coerceIn(0L, durationMs))
                        },
                        onValueChangeFinished = onSeekFinished,
                        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                        enabled = durationMs > 0L,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (markersEnabled) {
                        VideoMarkerTicks(
                            markers = markers,
                            durationMs = durationMs,
                            onMoveMarker = onMoveMarker,
                            modifier = Modifier
                                .matchParentSize()
                                .padding(horizontal = 12.dp)
                        )
                    }
                }
                if (!markersEnabled) {
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            }

            if (markersEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${positionMs.formatPlaybackTime()} / ${durationMs.formatPlaybackTime()}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onJumpToPreviousMarker, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, null, tint = Color.White)
                    }
                    IconButton(onClick = onAddMarker, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.Add, null, tint = Color.White)
                    }
                    IconButton(onClick = onDeleteNearestMarker, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color.White)
                    }
                    IconButton(onClick = onJumpToNextMarker, modifier = Modifier.size(42.dp)) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, null, tint = Color.White)
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            } else {
                Text(
                    text = "${positionMs.formatPlaybackTime()} / ${durationMs.formatPlaybackTime()}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun VideoMarkerTicks(
    markers: ImmutableList<VideoMarker>,
    durationMs: Long,
    onMoveMarker: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationMs <= 0L || markers.isEmpty()) return

    BoxWithConstraints(modifier = modifier.height(48.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)

        markers.forEach { marker ->
            val fraction = (marker.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val offsetX = maxWidth * fraction - 2.dp
            var dragPositionMs by remember(marker.id, marker.positionMs) {
                mutableLongStateOf(marker.positionMs)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = offsetX)
                    .size(width = 4.dp, height = 28.dp)
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(marker.id, durationMs, widthPx) {
                        detectDragGestures(
                            onDragStart = { dragPositionMs = marker.positionMs },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaMs = ((dragAmount.x / widthPx) * durationMs).toLong()
                                dragPositionMs = (dragPositionMs + deltaMs).coerceIn(0L, durationMs)
                            },
                            onDragEnd = {
                                onMoveMarker(marker.id, dragPositionMs.coerceIn(0L, durationMs))
                            },
                            onDragCancel = {
                                dragPositionMs = marker.positionMs
                            }
                        )
                    }
            )
        }
    }
}

@Composable
private fun FullscreenGestureFeedbackView(
    feedback: FullscreenGestureFeedback,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.68f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val icon = when (feedback.type) {
                FullscreenGestureFeedbackType.BRIGHTNESS -> Icons.Default.BrightnessHigh
                FullscreenGestureFeedbackType.VOLUME -> {
                    if (feedback.percent <= 0) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    }
                }
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "${feedback.percent}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun VideoSize.toRequestedOrientation(): Int? {
    return when {
        width > height && height > 0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        height > width && width > 0 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else -> null
    }
}

private fun Activity?.currentWindowBrightnessOrDefault(): Float {
    val currentBrightness = this?.window?.attributes?.screenBrightness ?: -1f
    return if (currentBrightness >= 0f) {
        currentBrightness.coerceIn(MIN_WINDOW_BRIGHTNESS, MAX_WINDOW_BRIGHTNESS)
    } else {
        DEFAULT_WINDOW_BRIGHTNESS
    }
}

private fun Activity.setWindowBrightness(brightness: Float) {
    val attributes = window.attributes
    attributes.screenBrightness = brightness
    window.attributes = attributes
}

private fun Long.validDurationMs(): Long {
    return if (this > 0L) this else 0L
}

private fun Long.formatPlaybackTime(): String {
    val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun List<VideoMarker>.nearestTo(positionMs: Long): VideoMarker? {
    return minByOrNull { marker -> abs(marker.positionMs - positionMs) }
}

private fun List<VideoMarker>.previousMarkerPosition(positionMs: Long): Long? {
    if (isEmpty()) return null
    val sorted = sortedBy { it.positionMs }
    return sorted.lastOrNull { it.positionMs < positionMs - MARKER_JUMP_TOLERANCE_MS }?.positionMs
        ?: sorted.last().positionMs
}

private fun List<VideoMarker>.nextMarkerPosition(positionMs: Long): Long? {
    if (isEmpty()) return null
    val sorted = sortedBy { it.positionMs }
    return sorted.firstOrNull { it.positionMs > positionMs + MARKER_JUMP_TOLERANCE_MS }?.positionMs
        ?: sorted.first().positionMs
}

private suspend fun PointerInputScope.detectFullscreenPinchResize(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    awaitEachGesture {
        var startDistance: Float? = null
        var resizeApplied = false

        while (true) {
            val event = awaitPointerEvent()
            val pressedChanges = event.changes.filter { it.pressed }

            if (pressedChanges.isEmpty()) break

            if (pressedChanges.size < 2) continue

            pressedChanges.forEach { it.consume() }

            val currentDistance = distanceBetweenFirstTwoPointers(pressedChanges)
            val initialDistance = startDistance

            if (initialDistance == null) {
                startDistance = currentDistance
                continue
            }

            if (initialDistance <= 0f || resizeApplied) continue

            val scale = currentDistance / initialDistance
            when {
                scale >= PINCH_ZOOM_THRESHOLD -> {
                    onZoomIn()
                    resizeApplied = true
                }
                scale <= PINCH_FIT_THRESHOLD -> {
                    onZoomOut()
                    resizeApplied = true
                }
            }
        }
    }
}

private fun distanceBetweenFirstTwoPointers(
    changes: List<androidx.compose.ui.input.pointer.PointerInputChange>
): Float {
    val first = changes[0].position
    val second = changes[1].position
    return hypot(first.x - second.x, first.y - second.y)
}

private enum class FullscreenDragMode {
    UNDECIDED,
    HORIZONTAL,
    BRIGHTNESS,
    VOLUME
}

private enum class FullscreenGestureFeedbackType {
    BRIGHTNESS,
    VOLUME
}

private data class FullscreenGestureFeedback(
    val type: FullscreenGestureFeedbackType,
    val percent: Int
)

private const val MIN_WINDOW_BRIGHTNESS = 0.05f
private const val MAX_WINDOW_BRIGHTNESS = 1.0f
private const val DEFAULT_WINDOW_BRIGHTNESS = 0.5f
private const val PINCH_ZOOM_THRESHOLD = 1.08f
private const val PINCH_FIT_THRESHOLD = 0.92f
private const val MARKER_JUMP_TOLERANCE_MS = 500L
