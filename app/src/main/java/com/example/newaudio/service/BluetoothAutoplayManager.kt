package com.example.newaudio.service

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.newaudio.domain.repository.ISettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages Bluetooth connection events to trigger auto-play or auto-pause logic.
 */
class BluetoothAutoplayManager(
    private val context: Context,
    private val player: Player,
    private val settingsRepository: ISettingsRepository,
    private val scope: CoroutineScope
) {

    // Cached setting to allow synchronous checks (e.g. in onTaskRemoved)
    var isAutoplayEnabled = false
        private set
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            handleBluetoothAction(action, intent)
        }
    }

    fun start() {
        // Monitor settings to keep the cached flag updated
        scope.launch {
            settingsRepository.userPreferences.collect {
                isAutoplayEnabled = it.isAutoPlayOnBluetooth
                if (isAutoplayEnabled) ensureReceiverRegistered()
            }
        }
    }

    fun stop() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        } catch (e: IllegalArgumentException) {
            // Receiver might not be registered or already unregistered
            Timber.w(e, "Failed to unregister Bluetooth receiver")
        }
    }

    private fun ensureReceiverRegistered() {
        if (receiverRegistered || !hasBluetoothConnectPermission()) return
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        } catch (error: SecurityException) {
            Timber.e(error, "Bluetooth receiver registration was denied")
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    private fun handleBluetoothAction(action: String, intent: Intent) {
        scope.launch {
            // Double-check the setting from source of truth before acting
            val settings = settingsRepository.userPreferences.first()
            if (!settings.isAutoPlayOnBluetooth) return@launch

            when (action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    // Only act when the A2DP audio profile is fully connected —
                    // not on ACL_CONNECTED which fires before audio routing is ready.
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED
                    )
                    if (state == BluetoothProfile.STATE_CONNECTED && !player.isPlaying) {
                        if (player.mediaItemCount > 0) {
                            player.play()
                        } else {
                            // Player is empty (e.g. Service restarted), try to restore last played song
                            restoreLastPlayedSongAndPlay()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            }
        }
    }

    private suspend fun restoreLastPlayedSongAndPlay() {
        val lastPlayed = settingsRepository.getLastPlayedSong() ?: return
        val song = lastPlayed.song

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(song.contentUri.ifBlank { song.path }))
            .setMediaId(song.path)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(if (song.albumArtPath != null) Uri.parse(song.albumArtPath) else null)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        if (lastPlayed.position > 0) {
            player.seekTo(lastPlayed.position)
        }
        player.play()
    }
}
