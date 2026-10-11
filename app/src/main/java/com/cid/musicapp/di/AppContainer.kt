package com.cid.musicapp.di

import android.content.Context
import android.os.SystemClock
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.data.repository.MusicRepository
import com.cid.musicapp.data.repository.YoutubeRadioSource
import com.cid.musicapp.history.FileHistoryStore
import com.cid.musicapp.history.ListenRecorder
import com.cid.musicapp.history.ListeningHistory
import com.cid.musicapp.history.TasteProvider
import com.cid.musicapp.player.PlayerController
import com.cid.musicapp.radio.RadioConfig
import com.cid.musicapp.radio.RadioEngine
import com.cid.musicapp.radio.TrackIdentityFactory
import com.cid.musicapp.update.ApkInstaller
import com.cid.musicapp.update.AppUpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
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

    // scope ของงานเบื้องหลังที่อยู่ยาวตลอดอายุแอป (เขียนไฟล์ประวัติการฟัง) — ไม่ผูกกับ ViewModel/หน้าจอใด
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val appSettings = AppSettings(context)
    val musicRepository = MusicRepository(appSettings)

    // ประวัติการฟัง: เก็บเป็นไฟล์ในที่เก็บส่วนตัวของแอป ไม่ส่งออกนอกเครื่อง
    val listeningHistory = ListeningHistory(
        store = FileHistoryStore(File(context.applicationContext.filesDir, AppConstants.HISTORY_FILE_NAME)),
        scope = appScope
    )

    // Radio + รสนิยม: ใช้ identity factory/ค่าปรับแต่งชุดเดียวกันทั้งตัวคัดเพลง ตัวคำนวณรสนิยม และตัวควบคุมคิว
    private val radioConfig = RadioConfig()
    private val trackIdentities = TrackIdentityFactory(radioConfig)
    val tasteProvider = TasteProvider(listeningHistory, appSettings, trackIdentities)
    private val radioEngine = RadioEngine(
        source = YoutubeRadioSource(musicRepository),
        config = radioConfig,
        identities = trackIdentities,
        tasteProvider = { tasteProvider.current() }
    )

    private val listenRecorder = ListenRecorder(
        elapsedMillis = { SystemClock.elapsedRealtime() },
        wallMillis = { System.currentTimeMillis() }
    )

    val playerController = PlayerController(
        context, musicRepository, appSettings, radioEngine, radioConfig,
        listenRecorder, listeningHistory, tasteProvider
    )
    val appUpdateChecker = AppUpdateChecker(okHttpClient)
    val apkInstaller = ApkInstaller(context.applicationContext, okHttpClient)
}
