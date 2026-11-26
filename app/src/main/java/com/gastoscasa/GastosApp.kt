package com.gastoscasa

import android.app.Application
import com.gastoscasa.push.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GastosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
    }
}
