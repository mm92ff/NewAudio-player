package com.example.newaudio.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newaudio.R
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IErrorRepository
import com.example.newaudio.domain.repository.IPlaylistRepository
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import java.io.File
import com.example.newaudio.domain.usecase.file.SetMusicFolderUseCase
import com.example.newaudio.domain.usecase.settings.ResetDatabaseUseCase
import com.example.newaudio.domain.usecase.settings.SetAutoPlayOnBluetoothUseCase
import com.example.newaudio.domain.usecase.settings.SetFullScreenPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetMiniPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetOneHandedModeUseCase
import com.example.newaudio.domain.usecase.settings.SetPlayOnFolderClickUseCase
import com.example.newaudio.domain.usecase.settings.SetPrimaryColorUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundTintFractionUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundGradientEnabledUseCase
import com.example.newaudio.domain.usecase.settings.SetTransparentListItemsUseCase
import com.example.newaudio.domain.usecase.settings.SetSettingsCardTransparentUseCase
import com.example.newaudio.domain.usecase.settings.SetSettingsCardBorderWidthUseCase
import com.example.newaudio.domain.usecase.settings.SetSettingsCardBorderColorUseCase
import com.example.newaudio.domain.usecase.settings.RestoreUserPreferencesUseCase
import com.example.newaudio.domain.usecase.settings.SetShowFolderSongCountUseCase
import com.example.newaudio.domain.usecase.settings.SetShowHiddenFilesUseCase
import com.example.newaudio.domain.usecase.settings.SetThemeUseCase
import com.example.newaudio.domain.usecase.settings.SetUseMarqueeUseCase
import com.example.newaudio.util.Constants
import com.example.newaudio.util.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    getUserSettingsUseCase: GetUserSettingsUseCase,
    private val setThemeUseCase: SetThemeUseCase,
    private val setPrimaryColorUseCase: SetPrimaryColorUseCase,
    private val setMusicFolderUseCase: SetMusicFolderUseCase,
    private val setMiniPlayerProgressBarHeightUseCase: SetMiniPlayerProgressBarHeightUseCase,
    private val setFullScreenPlayerProgressBarHeightUseCase: SetFullScreenPlayerProgressBarHeightUseCase,
    private val setAutoPlayOnBluetoothUseCase: SetAutoPlayOnBluetoothUseCase,
    private val setOneHandedModeUseCase: SetOneHandedModeUseCase,
    private val setUseMarqueeUseCase: SetUseMarqueeUseCase,
    private val setShowHiddenFilesUseCase: SetShowHiddenFilesUseCase,
    private val setPlayOnFolderClickUseCase: SetPlayOnFolderClickUseCase,
    private val setShowFolderSongCountUseCase: SetShowFolderSongCountUseCase,
    private val setBackgroundTintFractionUseCase: SetBackgroundTintFractionUseCase,
    private val setBackgroundGradientEnabledUseCase: SetBackgroundGradientEnabledUseCase,
    private val setTransparentListItemsUseCase: SetTransparentListItemsUseCase,
    private val setSettingsCardTransparentUseCase: SetSettingsCardTransparentUseCase,
    private val setSettingsCardBorderWidthUseCase: SetSettingsCardBorderWidthUseCase,
    private val setSettingsCardBorderColorUseCase: SetSettingsCardBorderColorUseCase,
    private val restoreUserPreferencesUseCase: RestoreUserPreferencesUseCase,
    private val resetDatabaseUseCase: ResetDatabaseUseCase,
    private val errorRepository: IErrorRepository,
    private val playlistRepository: IPlaylistRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _showResetDialog = MutableStateFlow(false)
    val showResetDialog = _showResetDialog.asStateFlow()

    val settingsState: StateFlow<UserPreferences> = getUserSettingsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(Constants.STATE_FLOW_SHARING_TIMEOUT_MS),
            initialValue = UserPreferences.default()
        )

    init {
        errorRepository.log(LogLevel.INFO, TAG, "SettingsViewModel initialized")
    }

    fun onThemeChange(theme: UserPreferences.Theme) = safeLaunch {
        setThemeUseCase(theme)
    }

    fun onPrimaryColorChange(color: String) = safeLaunch {
        setPrimaryColorUseCase(color)
    }

    fun onMusicFolderChange(path: String) = safeLaunch {
        setMusicFolderUseCase(path)
    }

    fun onMiniPlayerProgressBarHeightChange(height: Float) = safeLaunch {
        setMiniPlayerProgressBarHeightUseCase(height)
    }

    fun onFullScreenPlayerProgressBarHeightChange(height: Float) = safeLaunch {
        setFullScreenPlayerProgressBarHeightUseCase(height)
    }

    fun onAutoPlayOnBluetoothChange(isEnabled: Boolean) = safeLaunch {
        setAutoPlayOnBluetoothUseCase(isEnabled)
    }

    fun onOneHandedModeChange(isEnabled: Boolean) = safeLaunch {
        setOneHandedModeUseCase(isEnabled)
    }

    fun onUseMarqueeChange(isEnabled: Boolean) = safeLaunch {
        setUseMarqueeUseCase(isEnabled)
    }

    fun onShowHiddenFilesChange(isEnabled: Boolean) = safeLaunch {
        setShowHiddenFilesUseCase(isEnabled)
    }

    fun onPlayOnFolderClickChange(isEnabled: Boolean) = safeLaunch {
        setPlayOnFolderClickUseCase(isEnabled)
    }

    fun onShowFolderSongCountChange(isEnabled: Boolean) = safeLaunch {
        setShowFolderSongCountUseCase(isEnabled)
    }

    fun onBackgroundTintFractionChange(fraction: Float) = safeLaunch {
        setBackgroundTintFractionUseCase(fraction)
    }

    fun onBackgroundGradientEnabledChange(enabled: Boolean) = safeLaunch {
        setBackgroundGradientEnabledUseCase(enabled)
    }

    fun onTransparentListItemsChange(enabled: Boolean) = safeLaunch {
        setTransparentListItemsUseCase(enabled)
    }

    fun onSettingsCardTransparentChange(enabled: Boolean) = safeLaunch {
        setSettingsCardTransparentUseCase(enabled)
    }

    fun onSettingsCardBorderWidthChange(widthDp: Float) = safeLaunch {
        setSettingsCardBorderWidthUseCase(widthDp)
    }

    fun onSettingsCardBorderColorChange(color: String) = safeLaunch {
        setSettingsCardBorderColorUseCase(color)
    }

    fun onResetDatabaseClicked() {
        _showResetDialog.value = true
    }

    fun onDismissResetDialog() {
        _showResetDialog.value = false
    }

    fun onConfirmResetDatabase() = safeLaunch {
        _showResetDialog.value = false
        resetDatabaseUseCase()
    }

    suspend fun exportPlaylistsSuspend(filePath: String): Boolean {
        return withContext(ioDispatcher) {
            try {
                val pathForRepo = if (filePath.startsWith("/") && !filePath.startsWith("file://")) {
                    "file://$filePath"
                } else {
                    filePath
                }

                val success = playlistRepository.exportPlaylists(pathForRepo, settingsState.value)

                if (success) {
                    _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.export_success)))
                } else {
                    _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
                }
                success
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error during export"
                Timber.tag(TAG).e(e, "Export failed")
                errorRepository.log(LogLevel.ERROR, TAG, errorMessage, e)
                _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
                false
            }
        }
    }

    fun onCopyFailed() {
        _events.trySend(SettingsEvent.ShowMessage(UiText.StringResource(R.string.copy_failed)))
    }

    fun onExportPlaylists(filePath: String) = safeLaunch {
        val success = playlistRepository.exportPlaylists(filePath, settingsState.value)
        if (success) {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.export_success)))
        } else {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
        }
    }

    fun onImportPlaylists(filePath: String) = safeLaunch {
        val pathForRepo = if (filePath.startsWith("/") && !filePath.startsWith("file://")) {
            "file://$filePath"
        } else {
            filePath
        }

        val result = playlistRepository.importPlaylists(pathForRepo)

        result.restoredPreferences?.let { restoreUserPreferencesUseCase(it) }

        if (result.songsNotFound > 0) {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(
                R.string.import_completed_with_missing,
                result.playlistsImported,
                result.songsFound,
                result.songsNotFound
            )))
        } else {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(
                R.string.import_success,
                result.playlistsImported
            )))
        }

        Timber.d("Import Result: Found=${result.songsFound}, Fixed=${result.songsFixed}, Missing=${result.songsNotFound}")
    }

    fun onExportPlaylistsToUri(destinationUri: Uri, context: Context) = safeLaunch {
        // Create temporary file for export
        val tempFile = File(context.cacheDir, "export_temp.json")

        try {
            // First export to temporary file
            val exportSuccess = exportPlaylistsSuspend(tempFile.absolutePath)

            if (exportSuccess) {
                // Copy from temp file to user-selected destination
                val copySuccess = withContext(ioDispatcher) {
                    try {
                        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { output ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to copy export file to destination")
                        errorRepository.log(LogLevel.ERROR, TAG, "Export copy failed: ${e.message}", e)
                        false
                    } finally {
                        // Clean up temporary file
                        if (tempFile.exists()) {
                            tempFile.delete()
                        }
                    }
                }

                if (!copySuccess) {
                    _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.copy_failed)))
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Export operation failed")
            errorRepository.log(LogLevel.ERROR, TAG, "Export failed: ${e.message}", e)
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
            // Clean up temp file on error
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun onImportPlaylistsFromUri(sourceUri: Uri, context: Context) = safeLaunch {
        // Create temporary file for import
        val tempFile = File(context.cacheDir, "import_temp.json")

        try {
            withContext(ioDispatcher) {
                // Copy from source URI to temp file
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Import from temporary file (this already handles the file:// prefix)
            onImportPlaylists(tempFile.absolutePath)

            // Clean up temporary file
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Import operation failed")
            errorRepository.log(LogLevel.ERROR, TAG, "Import failed: ${e.message}", e)
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
            // Clean up temp file on error
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            try {
                block()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error in Settings"
                Timber.tag(TAG).e(e, "Update failed")
                errorRepository.log(LogLevel.ERROR, TAG, errorMessage, e)
                _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
            }
        }
    }
}
