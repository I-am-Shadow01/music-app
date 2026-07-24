package com.cid.musicapp.ui.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cid.musicapp.R

/** แถบแจ้งเตือนเวลามีเวอร์ชันใหม่ พร้อมปุ่มกดอัปเดต/ปิดในตัว แสดงเฉพาะตอนมีอัปเดตจริงๆ */
@Composable
fun UpdateBanner(viewModel: UpdateViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReturnedFromPermissionSettings()
    }

    if (uiState.needsInstallPermission) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionPrompt() },
            title = { Text(stringResource(R.string.update_permission_needed)) },
            confirmButton = {
                TextButton(onClick = { permissionLauncher.launch(viewModel.requestPermissionIntent()) }) {
                    Text(stringResource(R.string.update_permission_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionPrompt() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (uiState.availableUpdate != null) {
        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.update_available),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!uiState.isDownloading) {
                        IconButton(
                            onClick = { viewModel.skipThisUpdate() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.update_skip)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(onClick = { viewModel.onUpdateClicked() }) {
                            Text(stringResource(R.string.update_button))
                        }
                    }
                }

                if (uiState.isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val percent = uiState.downloadProgressPercent
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.update_downloading_percent, percent),
                            style = MaterialTheme.typography.labelSmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                uiState.errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { viewModel.dismissError() }
                    )
                }
            }
        }
    }
}
