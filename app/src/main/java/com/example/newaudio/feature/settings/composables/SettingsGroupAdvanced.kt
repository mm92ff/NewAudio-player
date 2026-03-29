package com.example.newaudio.feature.settings.composables

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.newaudio.R
import com.example.newaudio.service.MediaPlaybackService
import com.example.newaudio.ui.theme.Dimens
import kotlin.system.exitProcess

@Composable
fun DeveloperOptions(
    onShowConsole: () -> Unit,
    onResetDatabase: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.developer_options),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        SettingsCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onShowConsole)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.PaddingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing)
            ) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = stringResource(R.string.settings_show_error_console_icon),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(text = stringResource(R.string.show_error_console), style = MaterialTheme.typography.bodyLarge)
            }
        }
        
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        
        SettingsCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onResetDatabase),
            containerColor = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.PaddingMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.settings_reset_database_icon),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.reset_database), 
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun KillAppSetting(context: Context) {
    SettingsFilledTonalButton(
        onClick = {
            val intent = Intent(context, MediaPlaybackService::class.java)
            context.stopService(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.kill_app))
    }
}
