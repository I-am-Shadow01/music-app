package com.cid.musicapp

import android.app.Application
import com.cid.musicapp.di.AppContainer

class MusicApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
