package com.cid.musicapp.di

import android.content.Context
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.data.repository.MusicRepository
import com.cid.musicapp.player.PlayerController
import com.cid.musicapp.update.ApkInstaller
import com.cid.musicapp.update.AppUpdateChecker
import okhttp3.OkHttpClient
import java.time.Duration

class AppContainer(context: Context) {

    /**
     * OkHttpClient ตัวเดียวแชร์กันทั้งแอป (เช็คอัปเดต + โหลด APK) — เดิมแต่ละคลาสสร้าง client ของตัวเอง
     * แบบไม่ตั้ง timeout เลย ตกไปใช้ค่า default ของ OkHttp (connect 10s / read 10s) โดยไม่ได้ตั้งใจ
     * ตั้ง timeout ชัดเจนจาก AppConstants: โหลด APK ใหญ่บนเน็ตช้าๆ อ่านทีละ chunk ต้องอดทนกว่า API call สั้นๆ
     * (timeout ของ OkHttp เป็น "เวลาสูงสุดระหว่าง byte" ไม่ใช่เวลารวมทั้งไฟล์ จึงโหลดไฟล์ยาวๆ ได้)
     */
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(AppConstants.NETWORK_CONNECT_TIMEOUT_SECONDS))
        .readTimeout(Duration.ofSeconds(AppConstants.NETWORK_READ_TIMEOUT_SECONDS))
        .build()

    val appSettings = AppSettings(context)
    val musicRepository = MusicRepository(appSettings)
    val playerController = PlayerController(context, musicRepository, appSettings)
    val appUpdateChecker = AppUpdateChecker(okHttpClient)
    val apkInstaller = ApkInstaller(context.applicationContext, okHttpClient)
}
