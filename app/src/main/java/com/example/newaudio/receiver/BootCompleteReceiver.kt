package com.example.newaudio.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.newaudio.service.MediaPlaybackService

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON") {

            // Restart the Media Service if needed
            val serviceIntent = Intent(context, MediaPlaybackService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}