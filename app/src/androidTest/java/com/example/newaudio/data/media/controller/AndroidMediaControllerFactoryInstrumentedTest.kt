package com.example.newaudio.data.media.controller

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMediaControllerFactoryInstrumentedTest {
    @Test
    fun controllerConnectsToRealPlaybackService() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = withContext(Dispatchers.Main) {
            AndroidMediaControllerFactory(context).create()
        }

        try {
            assertTrue(controller.isConnected)
        } finally {
            withContext(Dispatchers.Main) { controller.release() }
        }
    }
}
