package com.cid.musicapp.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.update.ApkInstaller
import com.cid.musicapp.update.AppUpdateChecker
import com.cid.musicapp.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class UpdateUiState(
    val availableUpdate: UpdateInfo? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val readyToInstall: Boolean = false, // โหลดไว้แล้ว รอบนี้กด "ติดตั้ง" ได้เลยไม่ต้องโหลดซ้ำ
    val needsInstallPermission: Boolean = false,
    val justConfirmedUpToDate: Boolean = false,
    val errorMessage: String? = null
)

class UpdateViewModel(
    private val currentBuildNumber: Int,
    private val checker: AppUpdateChecker,
    private val installer: ApkInstaller,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState

    // ไฟล์ที่โหลดไว้แล้วในเซสชันนี้ + build number ที่มันตรงกับ — กันโหลดซ้ำถ้ากดอัปเดตอีกรอบ
    private var downloadedFile: File? = null
    private var downloadedForBuildNumber: Int? = null

    init {
        viewModelScope.launch {
            val autoCheckEnabled = appSettings.autoCheckUpdatesFlow.first()
            val lastCheck = appSettings.lastAutoCheckTimestampFlow.first()
            val now = System.currentTimeMillis()
            val dueForCheck = now - lastCheck >= AppConstants.AUTO_UPDATE_CHECK_MIN_INTERVAL_MILLIS

            if (autoCheckEnabled && dueForCheck) {
                appSettings.setLastAutoCheckTimestamp(now)
                runCheck(showUpToDateFeedback = false, respectSkip = true)
            }
        }
    }

    /** ปุ่ม "เช็คอัปเดตตอนนี้" ที่ผู้ใช้กดเอง — เช็คเสมอ ไม่สนว่าปิดเช็คอัตโนมัติหรือเพิ่งข้ามไปก็ตาม */
    fun checkNow() {
        viewModelScope.launch {
            appSettings.setLastAutoCheckTimestamp(System.currentTimeMillis())
            runCheck(showUpToDateFeedback = true, respectSkip = false)
        }
    }

    private suspend fun runCheck(showUpToDateFeedback: Boolean, respectSkip: Boolean) {
        _uiState.value = _uiState.value.copy(isChecking = true, justConfirmedUpToDate = false)
        val update = checker.checkForUpdate(currentBuildNumber)

        val skippedBuild = if (respectSkip) appSettings.skippedUpdateBuildFlow.first() else 0
        val shouldShow = update != null && update.buildNumber != skippedBuild

        _uiState.value = _uiState.value.copy(
            isChecking = false,
            availableUpdate = if (shouldShow) update else null,
            justConfirmedUpToDate = showUpToDateFeedback && update == null
        )
    }

    /** ผู้ใช้กด "ปิด/ข้ามเวอร์ชันนี้" — ไม่เตือนอีกจนกว่าจะมี build ใหม่กว่านี้ */
    fun skipThisUpdate() {
        val update = _uiState.value.availableUpdate ?: return
        viewModelScope.launch { appSettings.setSkippedUpdateBuild(update.buildNumber) }
        _uiState.value = _uiState.value.copy(availableUpdate = null)
    }

    /** ผู้ใช้กด "อัปเดต" — เช็คสิทธิ์ก่อน ถ้ามีแล้วค่อยโหลด+ติดตั้ง (หรือติดตั้งเลยถ้าโหลดไว้แล้วในเซสชันนี้) */
    fun onUpdateClicked() {
        val update = _uiState.value.availableUpdate ?: return

        if (!installer.hasInstallPermission()) {
            _uiState.value = _uiState.value.copy(needsInstallPermission = true)
            return
        }

        val cachedFile = downloadedFile
        if (cachedFile != null && downloadedForBuildNumber == update.buildNumber && cachedFile.exists()) {
            installer.install(cachedFile)
            return
        }

        downloadAndInstall(update)
    }

    /** เรียกหลังผู้ใช้กลับมาจากหน้าตั้งค่าสิทธิ์ติดตั้งแอปไม่รู้จัก แล้วลองใหม่ */
    fun onReturnedFromPermissionSettings() {
        _uiState.value = _uiState.value.copy(needsInstallPermission = false)
        if (installer.hasInstallPermission()) {
            onUpdateClicked()
        }
    }

    fun dismissPermissionPrompt() {
        _uiState.value = _uiState.value.copy(needsInstallPermission = false)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun downloadAndInstall(update: UpdateInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                downloadProgressPercent = 0,
                errorMessage = null
            )
            try {
                val file = installer.download(update.downloadUrl) { percent ->
                    _uiState.value = _uiState.value.copy(downloadProgressPercent = percent)
                }
                downloadedFile = file
                downloadedForBuildNumber = update.buildNumber

                _uiState.value = _uiState.value.copy(isDownloading = false, downloadProgressPercent = null)
                installer.install(file)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    downloadProgressPercent = null,
                    errorMessage = "อัปเดตไม่สำเร็จ: ${e.message ?: "เกิดข้อผิดพลาด"}"
                )
            }
        }
    }

    fun requestPermissionIntent() = installer.buildRequestPermissionIntent()
}
