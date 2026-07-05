package com.example.newaudio.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.newaudio.domain.usecase.file.SetMusicFolderUseCase
import com.example.newaudio.domain.usecase.file.SetVideoFolderUseCase
import com.example.newaudio.domain.usecase.settings.GetUserSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PermissionUiState {
    data object Loading : PermissionUiState
    data class Success(
        val isMusicFolderSet: Boolean,
        val isVideoFolderSet: Boolean
    ) : PermissionUiState
    data class Error(val message: String) : PermissionUiState
}

@HiltViewModel
class PermissionViewModel @Inject constructor(
    getUserSettingsUseCase: GetUserSettingsUseCase,
    private val setMusicFolderUseCase: SetMusicFolderUseCase,
    private val setVideoFolderUseCase: SetVideoFolderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<PermissionUiState>(PermissionUiState.Loading)
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getUserSettingsUseCase()
                .map {
                    PermissionUiState.Success(
                        isMusicFolderSet = it.musicFolderPath.isNotBlank(),
                        isVideoFolderSet = it.videoFolderPath.isNotBlank()
                    )
                }
                .collect { newState ->
                    _uiState.value = newState
                }
        }
    }

    fun onMusicFolderSelected(path: String) {
        viewModelScope.launch {
            _uiState.value = PermissionUiState.Loading
            try {
                setMusicFolderUseCase(path)
            } catch (e: Exception) {
                _uiState.value = PermissionUiState.Error("Failed to set music folder: ${e.message}")
            }
        }
    }

    fun onVideoFolderSelected(path: String) {
        viewModelScope.launch {
            _uiState.value = PermissionUiState.Loading
            try {
                setVideoFolderUseCase(path)
            } catch (e: Exception) {
                _uiState.value = PermissionUiState.Error("Failed to set video folder: ${e.message}")
            }
        }
    }
}
