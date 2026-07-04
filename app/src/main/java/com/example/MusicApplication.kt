package com.example

import android.app.Application
import android.content.Context
import android.os.Build

class MusicApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        if (base != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            super.attachBaseContext(base.createAttributionContext("music_playback"))
        } else {
            super.attachBaseContext(base)
        }
    }
}
