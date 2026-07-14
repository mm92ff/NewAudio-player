package com.example.newaudio.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.newaudio.BuildConfig

/**
 * An on-demand benchmark probe that reads a Player position without adding a polling
 * recomposition loop to the measured UI. The semantics action refreshes exactly once
 * when UI automation requests a sample.
 */
@Composable
fun BenchmarkPlaybackPositionProbe(
    tag: String,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.BENCHMARK) return

    val latestPositionProvider by rememberUpdatedState(positionProvider)
    var sampledPosition by remember {
        mutableLongStateOf(latestPositionProvider().coerceAtLeast(0L))
    }

    Box(
        modifier = modifier
            .size(24.dp)
            .testTag(tag)
            .semantics {
                contentDescription = "$tag:${sampledPosition.coerceAtLeast(0L)}"
                onClick(label = "Sample playback position") {
                    sampledPosition = latestPositionProvider().coerceAtLeast(0L)
                    true
                }
            }
    )
}

/** A benchmark-only, on-demand entry point to the same action used by the production UI. */
@Composable
fun BenchmarkActionProbe(
    tag: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.BENCHMARK) return

    val latestAction by rememberUpdatedState(onAction)
    Box(
        modifier = modifier
            .size(1.dp)
            .testTag(tag)
            .semantics {
                onClick(label = tag) {
                    latestAction()
                    true
                }
            }
    )
}
