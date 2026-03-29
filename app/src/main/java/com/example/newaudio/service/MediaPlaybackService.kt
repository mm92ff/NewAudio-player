package com.example.newaudio.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.newaudio.MainActivity
import com.example.newaudio.domain.audio.AudioEffectController
import com.example.newaudio.domain.repository.ISettingsRepository
import com.example.newaudio.util.Constants.Playback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MediaPlaybackService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var effectController: AudioEffectController

    @Inject
    lateinit var settingsRepository: ISettingsRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private lateinit var bluetoothAutoplayManager: BluetoothAutoplayManager
    private lateinit var notificationProvider: NewAudioNotificationProvider

    override fun onCreate() {
        super.onCreate()
        
        notificationProvider = NewAudioNotificationProvider(this) 
        setMediaNotificationProvider(notificationProvider)

        initializeSession()
        
        bluetoothAutoplayManager = BluetoothAutoplayManager(
            context = this,
            player = player,
            settingsRepository = settingsRepository,
            scope = serviceScope
        )
        bluetoothAutoplayManager.start()
    }

    private fun initializeSession() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                val sessionCommands = connectionResult.availableSessionCommands
                    .buildUpon()
                    .add(SessionCommand(Playback.ACTION_SET_EQ_ENABLED, Bundle.EMPTY))
                    .add(SessionCommand(Playback.ACTION_SET_EQ_BAND, Bundle.EMPTY))
                    .add(SessionCommand(Playback.ACTION_GET_EQ_CONFIG, Bundle.EMPTY))
                    .add(SessionCommand(Playback.ACTION_SET_EQ_PRESET, Bundle.EMPTY))
                    .build()
                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    connectionResult.availablePlayerCommands
                )
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                return when (customCommand.customAction) {
                    Playback.ACTION_SET_EQ_ENABLED -> {
                        effectController.setEnabled(args.getBoolean(Playback.EXTRA_EQ_ENABLED))
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    Playback.ACTION_SET_EQ_BAND -> {
                        val bandId = args.getInt(Playback.EXTRA_BAND_ID)
                        val level = args.getFloat(Playback.EXTRA_BAND_LEVEL)
                        effectController.setBandLevel(bandId, level)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    Playback.ACTION_SET_EQ_PRESET -> {
                        val name = args.getString(Playback.EXTRA_EQ_PRESET_NAME) ?: ""
                        val success = effectController.setPreset(name)
                        val code = if (success) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE
                        Futures.immediateFuture(SessionResult(code))
                    }
                    Playback.ACTION_GET_EQ_CONFIG -> handleGetEqConfig()
                    else -> Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                }
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(callback)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    effectController.initialize(audioSessionId)
                }
            }
        })

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            effectController.initialize(player.audioSessionId)
        }
    }

    private fun handleGetEqConfig(): ListenableFuture<SessionResult> {
        val config = effectController.getConfig()
            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_INVALID_STATE))

        val resultBundle = Bundle().apply {
            putBoolean(Playback.EXTRA_EQ_ENABLED, config.isEnabled)
            putInt(Playback.EXTRA_NUM_BANDS, config.numBands)
            putInt(Playback.EXTRA_BAND_LEVEL_RANGE_MIN, config.minLevel)
            putInt(Playback.EXTRA_BAND_LEVEL_RANGE_MAX, config.maxLevel)
            putIntArray(Playback.EXTRA_CENTER_FREQS, config.centerFreqs)
            putIntArray(Playback.EXTRA_CURRENT_LEVELS, config.currentLevels)
            putString(Playback.EXTRA_EQ_PRESET_NAME, config.currentPresetName)
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, resultBundle))
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.isPlaying) {
            if (!bluetoothAutoplayManager.isAutoplayEnabled) {
                stopSelf()
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        bluetoothAutoplayManager.stop()
        notificationProvider.release()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        effectController.release()
        super.onDestroy()
    }
}
