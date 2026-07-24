package com.cid.musicapp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** ดาวน์โหลด APK จาก URL แล้วส่งให้ตัวติดตั้งของระบบเปิด */
class ApkInstaller(private val context: Context) {

    private val client = OkHttpClient()

    fun hasInstallPermission(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** พาไปหน้าตั้งค่า "ติดตั้งแอปที่ไม่รู้จัก" สำหรับแอปนี้โดยเฉพาะ */
    fun buildRequestPermissionIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** โหลดไฟล์ APK มาเก็บใน cache ของแอป คืน path ไฟล์ที่โหลดเสร็จ */
    suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apk-updates").apply { mkdirs() }
        val outFile = File(dir, "update.apk")

        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("โหลดไฟล์อัปเดตไม่สำเร็จ (${response.code})")
            val body = response.body ?: throw Exception("ไม่มีข้อมูลไฟล์ตอบกลับมา")
            outFile.outputStream().use { output -> body.byteStream().copyTo(output) }
        }

        outFile
    }

    /** เปิดหน้าจอติดตั้งของระบบด้วยไฟล์ APK ที่โหลดมาแล้ว */
    fun install(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
