package com.example.newaudio.feature.filebrowser

import com.example.newaudio.domain.model.FileItem
import com.example.newaudio.domain.model.MediaBrowserMode
import com.example.newaudio.domain.model.Playlist
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.VideoPlaylist
import com.example.newaudio.util.UiText
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class FileBrowserUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val fileItems: ImmutableList<FileItem> = persistentListOf(),
    val currentPath: String = "",
    val rootPath: String = "",
    val pathHistory: ImmutableList<String> = persistentListOf(),
    val canNavigateBack: Boolean = false,
    val errorRes: UiText? = null,
    val activeSongPath: String? = null,
    val activeVideoPath: String? = null,
    val browserMode: MediaBrowserMode = MediaBrowserMode.MUSIC,
    val showInlineVideo: Boolean = false,
    val dialogState: DialogState = DialogState.None,
    val clipboardState: ClipboardState = ClipboardState.Empty,
    val oneHandedMode: Boolean = false,
    val playOnFolderClick: Boolean = false,
    val resumeSessionOnModeSwitch: Boolean = false,
    val repeatMode: UserPreferences.RepeatMode = UserPreferences.RepeatMode.NONE,
    val playlists: ImmutableList<Playlist> = persistentListOf(),
    val videoPlaylists: ImmutableList<VideoPlaylist> = persistentListOf(),
    val showVideoPreviewItems: Boolean = false,
    val videoDisplayMode: UserPreferences.VideoDisplayMode = UserPreferences.VideoDisplayMode.LIST,
    val videoGalleryColumns: Int = 3,
    val showVideoNamesInGallery: Boolean = false,
    // Edit Mode (Multi-Select)
    val isEditMode: Boolean = false,
    val selectedPaths: PersistentSet<String> = persistentSetOf(),
    val transparentListItems: Boolean = false
)

sealed class DialogState {
    data object None : DialogState()
    data class Delete(val file: FileItem) : DialogState()
    data class DeleteMultiple(val files: List<FileItem>) : DialogState()
    data class Rename(val file: FileItem) : DialogState()
    data class AddToPlaylist(val file: FileItem.AudioFile) : DialogState()
    data class AddToPlaylistMultiple(val files: List<FileItem.AudioFile>) : DialogState()
    data class AddToVideoPlaylist(val file: FileItem.VideoFile) : DialogState()
    data class AddToVideoPlaylistMultiple(val files: List<FileItem.VideoFile>) : DialogState()
    data object CreateFolder : DialogState()
}

sealed class ClipboardState {
    data object Empty : ClipboardState()
    data class Active(
        val files: List<FileItem>,
        val action: ClipboardAction,
        val sourceParentPath: String
    ) : ClipboardState()
}

enum class ClipboardAction {
    COPY, MOVE
}
