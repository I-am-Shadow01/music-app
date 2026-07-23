package com.cid.musicapp.di

import android.content.Context
import com.cid.musicapp.data.repository.MusicRepository
import com.cid.musicapp.player.PlayerController
import com.cid.musicapp.update.ApkInstaller
import com.cid.musicapp.update.AppUpdateChecker

class AppContainer(context: Context) {
    val musicRepository = MusicRepository()
    val playerController = PlayerController(context, musicRepository)
    val appUpdateChecker = AppUpdateChecker()
    val apkInstaller = ApkInstaller(context.applicationContext)
}
