package com.example.newaudio.feature.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.R
import com.example.newaudio.domain.model.LogEntry
import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.ui.theme.Dimens
import com.example.newaudio.ui.theme.LogLevelError
import com.example.newaudio.ui.theme.LogLevelInfo
import com.example.newaudio.ui.theme.LogLevelWarn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ConsoleOverlay(
    onClose: () -> Unit,
    viewModel: ConsoleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .offset { offset }
                    .widthIn(max = Dimens.Console_MaxWidth)
                    .heightIn(max = Dimens.Console_MaxHeight)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset = IntOffset(
                                x = (offset.x + dragAmount.x).roundToInt(),
                                y = (offset.y + dragAmount.y).roundToInt()
                            )
                        }
                    }
                    .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium)
            ) {
                Header(
                    onClear = viewModel::onClearConsole,
                    onCopy = {
                        val clipboardLabel = context.getString(R.string.console_clipboard_label)
                        val toastText = context.getString(R.string.console_logs_copied_toast)
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(clipboardLabel, viewModel.getLogsForClipboard())
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onClose = onClose,
                    activeFilters = uiState.activeFilters,
                    onFilterToggle = viewModel::onFilterToggle
                )
                LogList(logs = uiState.logs)
            }
        }
    }
}

@Composable
private fun Header(
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    activeFilters: Set<LogLevel>,
    onFilterToggle: (LogLevel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = Dimens.Console_HeaderCornerRadius, topEnd = Dimens.Console_HeaderCornerRadius)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.PaddingMedium, vertical = Dimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.console_title), style = MaterialTheme.typography.titleMedium)
            Row {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.console_copy_logs_description))
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.console_clear_description))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.console_close_description))
                }
            }
        }
        FilterControls(activeFilters = activeFilters, onFilterToggle = onFilterToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterControls(activeFilters: Set<LogLevel>, onFilterToggle: (LogLevel) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.PaddingSmall, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingSmall)
    ) {
        LogLevel.entries.forEach { level ->
            FilterChip(
                selected = level in activeFilters,
                onClick = { onFilterToggle(level) },
                label = { Text(level.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(Dimens.Console_LogIndicatorSize)
                            .background(level.toColor(), CircleShape)
                    )
                }
            )
        }
    }
}

@Composable
private fun LogList(logs: List<LogEntry>) {
    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.PaddingMedium),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.console_no_logs), style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(modifier = Modifier.padding(Dimens.PaddingSmall), reverseLayout = true) {
            items(logs, key = { it.timestamp.toString() + it.message.hashCode() }) { log ->
                LogItem(log)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
private fun LogItem(log: LogEntry) {
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timestamp = remember(log.timestamp) { dateFormatter.format(Date(log.timestamp)) }

    Row(modifier = Modifier.padding(vertical = Dimens.PaddingSmall, horizontal = Dimens.PaddingSmall)) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .background(log.level.toColor(), CircleShape)
        )
        Spacer(Modifier.width(Dimens.Console_LogItemSpacing))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Dimens.PaddingSmall))
                Text(
                    text = "[${log.tag}]",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold.takeIf { log.level == LogLevel.ERROR }
            )
            log.throwableString?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun LogLevel.toColor(): Color {
    return when (this) {
        LogLevel.INFO -> LogLevelInfo
        LogLevel.WARN -> LogLevelWarn
        LogLevel.ERROR -> LogLevelError
    }
}
