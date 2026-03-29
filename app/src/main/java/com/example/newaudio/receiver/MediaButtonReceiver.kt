package com.example.newaudio.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.example.newaudio.service.MediaPlaybackService
import com.example.newaudio.util.Constants.Playback

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (event?.action == KeyEvent.ACTION_DOWN) {
                val serviceIntent = Intent(context, MediaPlaybackService::class.java)
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        serviceIntent.action = Playback.ACTION_PLAY_PAUSE
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        serviceIntent.action = Playback.ACTION_NEXT
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        serviceIntent.action = Playback.ACTION_PREVIOUS
                    }
                    else -> return
                }
                context?.startService(serviceIntent)
            }
        }
    }
}
