package com.cid.musicapp.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.update.ApkInstaller
import com.cid.musicapp.update.AppUpdateChecker
import com.cid.musicapp.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class UpdateUiState(
    val availableUpdate: UpdateInfo? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
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

    init {
        viewModelScope.launch {
            if (appSettings.autoCheckUpdatesFlow.first()) {
                runCheck(showUpToDateFeedback = false)
            }
        }
    }

    /** ปุ่ม "เช็คอัปเดตตอนนี้" ที่ผู้ใช้กดเอง — เช็คเสมอไม่ว่าจะปิดเช็คอัตโนมัติไว้หรือไม่ */
    fun checkNow() {
        viewModelScope.launch { runCheck(showUpToDateFeedback = true) }
    }

    private suspend fun runCheck(showUpToDateFeedback: Boolean) {
        _uiState.value = _uiState.value.copy(isChecking = true, justConfirmedUpToDate = false)
        val update = checker.checkForUpdate(currentBuildNumber)
        _uiState.value = _uiState.value.copy(
            isChecking = false,
            availableUpdate = update,
            justConfirmedUpToDate = showUpToDateFeedback && update == null
        )
    }

    /** ผู้ใช้กด "อัปเดต" — เช็คสิทธิ์ก่อน ถ้ามีแล้วค่อยโหลด+ติดตั้ง */
    fun onUpdateClicked() {
        val update = _uiState.value.availableUpdate ?: return

        if (!installer.hasInstallPermission()) {
            _uiState.value = _uiState.value.copy(needsInstallPermission = true)
            return
        }

        downloadAndInstall(update)
    }

    /** เรียกหลังผู้ใช้กลับมาจากหน้าตั้งค่าสิทธิ์ติดตั้งแอปไม่รู้จัก แล้วลองใหม่ */
    fun onReturnedFromPermissionSettings() {
        _uiState.value = _uiState.value.copy(needsInstallPermission = false)
        val update = _uiState.value.availableUpdate ?: return
        if (installer.hasInstallPermission()) {
            downloadAndInstall(update)
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
            _uiState.value = _uiState.value.copy(isDownloading = true, errorMessage = null)
            try {
                val file = installer.download(update.downloadUrl)
                installer.install(file)
                _uiState.value = _uiState.value.copy(isDownloading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    errorMessage = "อัปเดตไม่สำเร็จ: ${e.message ?: "เกิดข้อผิดพลาด"}"
                )
            }
        }
    }

    fun requestPermissionIntent() = installer.buildRequestPermissionIntent()
}
