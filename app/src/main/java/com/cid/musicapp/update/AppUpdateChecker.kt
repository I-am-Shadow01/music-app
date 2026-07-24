package com.cid.musicapp.update

import com.cid.musicapp.config.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * เช็คว่ามี build ใหม่กว่าบน GitHub Releases ไหม โดยเทียบกับ BuildConfig.BUILD_NUMBER
 * (เลข build เอามาจาก GITHUB_RUN_NUMBER ตอน CI build — ดู build.yml)
 */
class AppUpdateChecker {

    private val client = OkHttpClient()

    /** คืนค่า null ถ้าไม่มีเวอร์ชันใหม่กว่า หรือเช็คไม่สำเร็จ (เช่นไม่มีเน็ต) */
    suspend fun checkForUpdate(currentBuildNumber: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(AppConstants.GITHUB_RELEASES_API_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val tagName = json.optString("tag_name")
                val remoteBuildNumber = tagName.removePrefix("build-").toIntOrNull()
                    ?: return@withContext null

                if (remoteBuildNumber <= currentBuildNumber) return@withContext null

                val assets = json.optJSONArray("assets") ?: return@withContext null
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk")) {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                }

                val finalDownloadUrl = downloadUrl ?: return@withContext null

                UpdateInfo(
                    buildNumber = remoteBuildNumber,
                    downloadUrl = finalDownloadUrl,
                    releaseUrl = json.optString("html_url")
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}
