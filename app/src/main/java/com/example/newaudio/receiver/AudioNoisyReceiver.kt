package com.example.newaudio.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.newaudio.service.MediaPlaybackService
import com.example.newaudio.util.Constants

class AudioNoisyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            // Headphones were unplugged, pause playback
            val serviceIntent = Intent(context, MediaPlaybackService::class.java)
            serviceIntent.action = Constants.Playback.ACTION_PLAY_PAUSE
            context.startService(serviceIntent)
        }
    }
}
