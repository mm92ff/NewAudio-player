package com.example.newaudio.data.media.controller

import androidx.media3.common.Player
import com.example.newaudio.data.audio.PlayerListenerDelegate
import com.example.newaudio.data.audio.PlaybackListenerCollaborators
import com.example.newaudio.data.audio.PlaybackPositionTracker
import com.example.newaudio.data.audio.PlaybackPreferenceWriter
import com.example.newaudio.data.audio.PlaybackSnapshotWriter
import com.example.newaudio.data.media.mapping.Media3ItemMapper
import com.example.newaudio.data.media.mapping.PlaybackErrorMapper
import com.example.newaudio.data.media.playback.PlaybackQueueState
import com.example.newaudio.data.media.playback.PlaybackStateStore
import com.example.newaudio.di.IoDispatcher
import com.example.newaudio.domain.repository.ISettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

@Singleton
class PlayerListenerDelegateFactory @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val queueState: PlaybackQueueState,
    private val settingsRepository: ISettingsRepository,
    private val itemMapper: Media3ItemMapper,
    private val errorMapper: PlaybackErrorMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    fun create(player: Player, coroutineScope: CoroutineScope): PlayerListenerDelegate {
        val snapshotWriter = PlaybackSnapshotWriter(
            settingsRepository,
            coroutineScope,
            ioDispatcher
        )
        val collaborators = PlaybackListenerCollaborators(
            snapshotWriter = snapshotWriter,
            preferenceWriter = PlaybackPreferenceWriter(
                settingsRepository,
                coroutineScope,
                ioDispatcher
            ),
            positionTracker = PlaybackPositionTracker(
                player,
                stateStore,
                queueState,
                snapshotWriter,
                coroutineScope
            ),
            errorMapper = errorMapper
        )
        return PlayerListenerDelegate(
            stateStore = stateStore,
            queueState = queueState,
            itemMapper = itemMapper,
            player = player,
            collaborators = collaborators
        )
    }
}
