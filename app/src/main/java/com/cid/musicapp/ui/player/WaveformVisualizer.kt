package com.cid.musicapp.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cid.musicapp.config.AppConstants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * คลื่นเสียงแบบ dynamic ที่ขยับตามเสียงที่กำลังเล่นจริง (ไม่ใช่ animation หลอกๆ)
 * ใช้ android.media.audiofx.Visualizer ผูกกับ audioSessionId ของ ExoPlayer ปัจจุบัน
 *
 * ต้องมีสิทธิ์ RECORD_AUDIO — ขอแบบมีบริบทตรงนี้เอง (ครั้งแรกที่เห็นคลื่นเสียง ไม่ใช่ตอนเปิดแอป)
 * และขอแค่ครั้งเดียวต่อการเปิดแอปหนึ่งรอบ ไม่ถามซ้ำทุกครั้งที่กลับมาหน้านี้ ถ้าผู้ใช้ไม่ให้สิทธิ์
 * หรืออุปกรณ์ไม่รองรับ จะไม่วาดอะไรเลยแทนที่จะ crash (เพลงยังเล่นได้ปกติ)
 */
@Composable
fun WaveformVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current

    var amplitudes by remember { mutableStateOf(FloatArray(AppConstants.WAVEFORM_BAR_COUNT)) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission && !WaveformPermissionState.hasRequestedThisSession) {
            WaveformPermissionState.hasRequestedThisSession = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // สร้าง Visualizer ใหม่ทุกครั้งที่ audioSessionId เปลี่ยน (เช่น สลับเพลง) — session id เดิมใช้ต่อไม่ได้
    DisposableEffect(audioSessionId, hasPermission) {
        var visualizer: Visualizer? = null

        if (hasPermission && audioSessionId != 0) {
            visualizer = runCatching {
                Visualizer(audioSessionId).apply {
                    val captureSize = AppConstants.WAVEFORM_CAPTURE_SIZE
                        .coerceIn(Visualizer.getCaptureSizeRange()[0], Visualizer.getCaptureSizeRange()[1])
                    setCaptureSize(captureSize)

                    val captureRateMilliHz = min(
                        AppConstants.WAVEFORM_CAPTURE_RATE_HZ * 1000,
                        Visualizer.getMaxCaptureRate()
                    )

                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer?,
                                waveform: ByteArray?,
                                samplingRate: Int
                            ) {
                                if (waveform != null) {
                                    amplitudes = downsampleToBars(waveform, AppConstants.WAVEFORM_BAR_COUNT)
                                }
                            }

                            override fun onFftDataCapture(
                                visualizer: Visualizer?,
                                fft: ByteArray?,
                                samplingRate: Int
                            ) = Unit
                        },
                        captureRateMilliHz,
                        /* waveform = */ true,
                        /* fft = */ false
                    )

                    enabled = true
                }
            }.getOrNull()
        }

        onDispose {
            runCatching {
                visualizer?.enabled = false
                visualizer?.release()
            }
        }
    }

    // ไม่มี session ให้ผูก หรือไม่ได้เล่นเพลงอยู่ — เคลียร์คลื่นให้แบนแทนค้างรูปเก่า
    // (ต้องอยู่ใน LaunchedEffect ไม่ใช่เซ็ต state ตรงๆ กลาง composition เพราะ FloatArray เทียบด้วย reference
    // ทำให้ค่าใหม่ไม่เท่าค่าเก่าเสมอ ถ้าเซ็ตตรงๆ จะวน recompose ไม่รู้จบ)
    LaunchedEffect(isPlaying, audioSessionId, hasPermission) {
        if (!isPlaying || audioSessionId == 0 || !hasPermission) {
            amplitudes = FloatArray(AppConstants.WAVEFORM_BAR_COUNT)
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val barCount = amplitudes.size
        if (barCount == 0) return@Canvas

        val gap = size.width / (barCount * 2f)
        val barWidth = gap.coerceAtLeast(1f)
        val centerY = size.height / 2f

        amplitudes.forEachIndexed { index, amplitude ->
            val barHeight = max(size.height * 0.08f, size.height * amplitude.coerceIn(0f, 1f))
            val x = gap + index * (barWidth + gap) * 2f
            drawLine(
                color = barColor,
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/** จำว่าเคยขอสิทธิ์ RECORD_AUDIO ไปแล้วในรอบเปิดแอปนี้ — กันถามซ้ำทุกครั้งที่เข้า/ออกหน้ากำลังเล่น
 * (ไม่ persist ข้ามการเปิดแอปใหม่ ตั้งใจ — ถ้าผู้ใช้ปิดแอปแล้วเปิดใหม่ ระบบเองก็จัดการเรื่อง
 * "อย่าถามอีก" ให้แล้วถ้าผู้ใช้เคยกดปฏิเสธแบบถาวรไปก่อนหน้า) */
private object WaveformPermissionState {
    var hasRequestedThisSession = false
}

/** ย่อ waveform (unsigned 8-bit, กึ่งกลางที่ 128) ให้เหลือ [barCount] แท่ง โดยเฉลี่ยแอมพลิจูดสัมบูรณ์ในแต่ละช่วง */
private fun downsampleToBars(waveform: ByteArray, barCount: Int): FloatArray {
    if (waveform.isEmpty()) return FloatArray(barCount)

    val samplesPerBar = max(1, waveform.size / barCount)
    return FloatArray(barCount) { barIndex ->
        val start = barIndex * samplesPerBar
        val end = min(start + samplesPerBar, waveform.size)
        if (start >= end) return@FloatArray 0f

        var sum = 0f
        for (i in start until end) {
            sum += abs((waveform[i].toInt() and 0xFF) - 128) / 128f
        }
        (sum / (end - start)).coerceIn(0f, 1f)
    }
}
