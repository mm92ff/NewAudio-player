package com.example.newaudio.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newaudio.R
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.model.LogLevel
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.repository.IErrorRepository
import com.example.newaudio.domain.repository.IPlaylistBackupRepository
import com.example.newaudio.domain.repository.ImportFailure
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import com.example.newaudio.domain.usecase.file.SetMusicFolderUseCase
import com.example.newaudio.domain.usecase.file.SetVideoFolderUseCase
import com.example.newaudio.domain.usecase.settings.ResetDatabaseUseCase
import com.example.newaudio.domain.usecase.settings.SetAutoPlayOnBluetoothUseCase
import com.example.newaudio.domain.usecase.settings.SetFullScreenPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetMiniPlayerProgressBarHeightUseCase
import com.example.newaudio.domain.usecase.settings.SetOneHandedModeUseCase
import com.example.newaudio.domain.usecase.settings.SetPlayOnFolderClickUseCase
import com.example.newaudio.domain.usecase.settings.SetPrimaryColorUseCase
import com.example.newaudio.domain.usecase.settings.SetResumeSessionOnModeSwitchUseCase
import com.example.newaudio.domain.usecase.settings.SetShowVideoNamesInGalleryUseCase
import com.example.newaudio.domain.usecase.settings.SetShowVideoPreviewItemsUseCase
import com.example.newaudio.domain.usecase.settings.SetVideoDisplayModeUseCase
import com.example.newaudio.domain.usecase.settings.SetVideoGalleryColumnsUseCase
import com.example.newaudio.domain.usecase.settings.SetVideoMarkersEnabledUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundTintFractionUseCase
import com.example.newaudio.domain.usecase.settings.SetBackgroundGradientDirectionUseCase
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
import kotlinx.coroutines.CancellationException
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
    private val setVideoFolderUseCase: SetVideoFolderUseCase,
    private val setMiniPlayerProgressBarHeightUseCase: SetMiniPlayerProgressBarHeightUseCase,
    private val setFullScreenPlayerProgressBarHeightUseCase: SetFullScreenPlayerProgressBarHeightUseCase,
    private val setAutoPlayOnBluetoothUseCase: SetAutoPlayOnBluetoothUseCase,
    private val setOneHandedModeUseCase: SetOneHandedModeUseCase,
    private val setUseMarqueeUseCase: SetUseMarqueeUseCase,
    private val setShowHiddenFilesUseCase: SetShowHiddenFilesUseCase,
    private val setPlayOnFolderClickUseCase: SetPlayOnFolderClickUseCase,
    private val setResumeSessionOnModeSwitchUseCase: SetResumeSessionOnModeSwitchUseCase,
    private val setShowVideoPreviewItemsUseCase: SetShowVideoPreviewItemsUseCase,
    private val setShowVideoNamesInGalleryUseCase: SetShowVideoNamesInGalleryUseCase,
    private val setVideoMarkersEnabledUseCase: SetVideoMarkersEnabledUseCase,
    private val setVideoDisplayModeUseCase: SetVideoDisplayModeUseCase,
    private val setVideoGalleryColumnsUseCase: SetVideoGalleryColumnsUseCase,
    private val setShowFolderSongCountUseCase: SetShowFolderSongCountUseCase,
    private val setBackgroundTintFractionUseCase: SetBackgroundTintFractionUseCase,
    private val setBackgroundGradientEnabledUseCase: SetBackgroundGradientEnabledUseCase,
    private val setBackgroundGradientDirectionUseCase: SetBackgroundGradientDirectionUseCase,
    private val setTransparentListItemsUseCase: SetTransparentListItemsUseCase,
    private val setSettingsCardTransparentUseCase: SetSettingsCardTransparentUseCase,
    private val setSettingsCardBorderWidthUseCase: SetSettingsCardBorderWidthUseCase,
    private val setSettingsCardBorderColorUseCase: SetSettingsCardBorderColorUseCase,
    private val restoreUserPreferencesUseCase: RestoreUserPreferencesUseCase,
    private val resetDatabaseUseCase: ResetDatabaseUseCase,
    private val errorRepository: IErrorRepository,
    private val playlistBackupRepository: IPlaylistBackupRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val gradientDirectionUpdates =
        Channel<UserPreferences.GradientDirection>(Channel.CONFLATED)

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
        viewModelScope.launch(ioDispatcher) {
            for (direction in gradientDirectionUpdates) {
                executeSafely {
                    setBackgroundGradientDirectionUseCase(direction)
                }
            }
        }
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

    fun onVideoFolderChange(path: String) = safeLaunch {
        setVideoFolderUseCase(path)
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

    fun onBluetoothPermissionDenied() {
        _events.trySend(
            SettingsEvent.ShowMessage(UiText.StringResource(R.string.bluetooth_permission_denied))
        )
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

    fun onResumeSessionOnModeSwitchChange(isEnabled: Boolean) = safeLaunch {
        setResumeSessionOnModeSwitchUseCase(isEnabled)
    }

    fun onShowVideoPreviewItemsChange(isEnabled: Boolean) = safeLaunch {
        setShowVideoPreviewItemsUseCase(isEnabled)
    }

    fun onShowVideoNamesInGalleryChange(isEnabled: Boolean) = safeLaunch {
        setShowVideoNamesInGalleryUseCase(isEnabled)
    }

    fun onVideoMarkersEnabledChange(isEnabled: Boolean) = safeLaunch {
        setVideoMarkersEnabledUseCase(isEnabled)
    }

    fun onVideoDisplayModeChange(mode: UserPreferences.VideoDisplayMode) = safeLaunch {
        setVideoDisplayModeUseCase(mode)
    }

    fun onVideoGalleryColumnsChange(columns: Int) = safeLaunch {
        setVideoGalleryColumnsUseCase(columns)
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

    fun onBackgroundGradientDirectionChange(direction: UserPreferences.GradientDirection) {
        gradientDirectionUpdates.trySend(direction)
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

    suspend fun exportPlaylistsSuspend(filePath: String, notifyResult: Boolean = true): Boolean {
        return withContext(ioDispatcher) {
            try {
                val success = playlistBackupRepository.exportPlaylists(filePath, settingsState.value)

                if (notifyResult) {
                    if (success) {
                        _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.export_success)))
                    } else {
                        _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
                    }
                }
                success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error during export"
                Timber.tag(TAG).e(e, "Export failed")
                errorRepository.log(LogLevel.ERROR, TAG, errorMessage, e)
                if (notifyResult) {
                    _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
                }
                false
            }
        }
    }

    fun onCopyFailed() {
        _events.trySend(SettingsEvent.ShowMessage(UiText.StringResource(R.string.copy_failed)))
    }

    fun onExportPlaylists(filePath: String) = safeLaunch {
        val success = playlistBackupRepository.exportPlaylists(filePath, settingsState.value)
        if (success) {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.export_success)))
        } else {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
        }
    }

    private suspend fun performImport(filePath: String) {
        val result = playlistBackupRepository.importPlaylists(filePath)

        if (!result.isSuccess) {
            val message = when (result.failure) {
                ImportFailure.TOO_LARGE, ImportFailure.LIMIT_EXCEEDED -> R.string.import_failed_too_large
                ImportFailure.INVALID_FORMAT, ImportFailure.UNSUPPORTED_VERSION -> R.string.import_failed_invalid
                else -> R.string.import_failed_io
            }
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(message)))
            return
        }

        val settingsRestoreFailed = result.restoredPreferences?.let { preferences ->
            try {
                restoreUserPreferencesUseCase(preferences)
                false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag(TAG).e(error, "Playlists imported, but settings restore failed")
                errorRepository.log(
                    LogLevel.ERROR,
                    TAG,
                    "Playlist import succeeded but settings restore failed: ${error.message}",
                    error
                )
                true
            }
        } ?: false

        if (settingsRestoreFailed) {
            _events.send(
                SettingsEvent.ShowMessage(
                    UiText.PluralResource(
                        R.plurals.import_completed_settings_failed,
                        result.playlistsImported,
                        result.playlistsImported
                    )
                )
            )
            return
        }

        if (result.songsNotFound > 0) {
            _events.send(SettingsEvent.ShowMessage(UiText.PluralResource(
                R.plurals.import_completed_with_missing,
                result.playlistsImported,
                result.playlistsImported,
                result.songsFound,
                result.songsNotFound
            )))
        } else {
            _events.send(SettingsEvent.ShowMessage(UiText.PluralResource(
                R.plurals.import_success,
                result.playlistsImported,
                result.playlistsImported
            )))
        }

        Timber.d("Import Result: Found=${result.songsFound}, Fixed=${result.songsFixed}, Missing=${result.songsNotFound}")
    }

    fun onImportPlaylists(filePath: String) = safeLaunch {
        performImport(filePath)
    }

    fun onExportPlaylistsToUri(destinationUri: Uri) = safeLaunch {
        val success = playlistBackupRepository.exportPlaylists(
            destinationUri.toString(),
            settingsState.value
        )
        if (success) {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.export_success)))
        } else {
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
        }
    }

    fun onImportPlaylistsFromUri(sourceUri: Uri) = safeLaunch {
        performImport(sourceUri.toString())
    }

    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            executeSafely(block)
        }
    }

    private suspend fun executeSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error in Settings"
            Timber.tag(TAG).e(e, "Update failed")
            errorRepository.log(LogLevel.ERROR, TAG, errorMessage, e)
            _events.send(SettingsEvent.ShowMessage(UiText.StringResource(R.string.unknown_error)))
        }
    }
}
