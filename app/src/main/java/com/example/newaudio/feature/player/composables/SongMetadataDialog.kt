package com.example.newaudio.feature.player.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens

@Composable
fun SongMetadataDialog(
    metadata: Map<String, String?>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.metadata_dialog_title)) },
        text = {
            SelectionContainer {
                LazyColumn {
                    items(metadata.toList()) { (key, value) ->
                        Column(modifier = Modifier.padding(vertical = Dimens.PaddingSmall)) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = value ?: stringResource(R.string.metadata_not_available),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
