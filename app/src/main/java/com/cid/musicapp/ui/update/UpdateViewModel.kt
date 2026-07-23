package com.cid.musicapp.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cid.musicapp.update.ApkInstaller
import com.cid.musicapp.update.AppUpdateChecker
import com.cid.musicapp.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UpdateUiState(
    val availableUpdate: UpdateInfo? = null,
    val isDownloading: Boolean = false,
    val needsInstallPermission: Boolean = false,
    val errorMessage: String? = null
)

class UpdateViewModel(
    private val currentBuildNumber: Int,
    private val checker: AppUpdateChecker,
    private val installer: ApkInstaller
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState

    init {
        viewModelScope.launch {
            val update = checker.checkForUpdate(currentBuildNumber)
            if (update != null) {
                _uiState.value = _uiState.value.copy(availableUpdate = update)
            }
        }
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
