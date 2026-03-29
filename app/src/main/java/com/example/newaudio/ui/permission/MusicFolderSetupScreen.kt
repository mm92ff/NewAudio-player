package com.example.newaudio.ui.permission

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens

@Composable
fun MusicFolderSetupScreen(
    onFolderSelected: (String) -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            onFolderSelected(it.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.PaddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = stringResource(id = R.string.permission_folder_icon_content_description),
            modifier = Modifier.size(Dimens.IconSizeLarge),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        Text(
            text = stringResource(R.string.setup_music_folder_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        Text(
            text = stringResource(R.string.setup_music_folder_desc),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
        Text(
            text = stringResource(R.string.setup_music_folder_skip_info),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

        Button(
            onClick = { folderPickerLauncher.launch(null) },
            modifier = Modifier.fillMaxWidth(Dimens.BUTTON_WIDTH_FACTOR)
        ) {
            Text(stringResource(R.string.select_music_folder))
        }
        
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(Dimens.BUTTON_WIDTH_FACTOR)
        ) {
            Text(stringResource(R.string.skip))
        }
    }
}
