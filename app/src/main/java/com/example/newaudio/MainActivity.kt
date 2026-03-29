package com.example.newaudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newaudio.feature.settings.SettingsViewModel
import com.example.newaudio.ui.MainAppScreen
import com.example.newaudio.ui.permission.PermissionAndSetupManager
import com.example.newaudio.ui.theme.NewAudioTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val userPreferences by settingsViewModel.settingsState.collectAsStateWithLifecycle()

            NewAudioTheme(userPreferences = userPreferences) {
                PermissionAndSetupManager {
                    MainAppScreen()
                }
            }
        }
    }
}