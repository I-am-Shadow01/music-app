package com.cid.musicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cid.musicapp.R
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.player.PlaybackUiState

/**
 * แถบเพลงเล็กด้านล่างจอ (โชว์เฉพาะตอนมีเพลงเล่นอยู่และไม่ได้อยู่หน้ากำลังเล่นเต็มจอ)
 * — แยกไฟล์มาจาก AppNavHost เดิม เพื่อให้ AppNavHost เหลือหน้าที่ navigation อย่างเดียว
 *
 * ท่าทางสัมผัส: แตะ = เปิดหน้ากำลังเล่น, ปัดซ้าย = เพลงถัดไป, ปัดขวา = เพลงก่อนหน้า,
 * ปุ่มปิด = หยุดเล่นและเคลียร์คิว
 */
@Composable
fun MiniPlayerBar(
    state: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val shape = RoundedCornerShape(16.dp)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { AppConstants.MINI_PLAYER_SWIPE_THRESHOLD_DP.dp.toPx() }
    var dragAccumulatorPx by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(bottom = 6.dp)
            .shadow(elevation = 6.dp, shape = shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                // ปัดซ้าย = เพลงถัดไป, ปัดขวา = เพลงก่อนหน้า — เหมือนแอปเพลงทั่วไป
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAccumulatorPx <= -swipeThresholdPx -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNext()
                            }
                            dragAccumulatorPx >= swipeThresholdPx -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPrevious()
                            }
                        }
                        dragAccumulatorPx = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulatorPx += dragAmount
                    }
                )
            }
            .clickable(onClick = onClick)
    ) {
        // แถบบางๆ บอกความคืบหน้าเพลงปัจจุบัน (เหมือน Spotify) — ให้เห็นเหลืออีกนานแค่ไหนโดยไม่ต้องเข้าไปหน้า Player
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(state.currentThumbnailUrl)
                    .crossfade(AppConstants.IMAGE_CROSSFADE_MILLIS)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.currentTitle ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    state.currentArtist ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            if (state.isResolving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.mini_player_dismiss)
                )
            }
        }
    }
}
