package com.example.newaudio.feature.filebrowser

import com.example.newaudio.domain.model.Song

/**
 * Definiert die Events, die vom FileBrowserViewModel an die UI gesendet werden können.
 * Die Verwendung von 'sealed interface' ist eine moderne Konvention und robust.
 */
sealed interface FileBrowserEvent {
    data class PlayPlaylist(val songs: List<Song>, val startIndex: Int) : FileBrowserEvent
}
