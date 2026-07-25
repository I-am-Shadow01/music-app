package com.cid.musicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cid.musicapp.R
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.player.RepeatMode
import com.cid.musicapp.player.UpcomingItem
import com.cid.musicapp.ui.util.formatDurationMillis
import com.cid.musicapp.ui.util.formatDurationSeconds

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onCollapse: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.currentTitle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.player_no_track))
        }
        return
    }

    // ตำแหน่ง slider ระหว่างลากนิ้ว ใช้ค่านี้แทนตำแหน่งจริงชั่วคราว กันกระตุกตอนลาก
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }

    // พื้นหลังไล่สีอ่อนๆ จากสี accent ของธีมที่หัวจอ จางลงมาเป็นพื้นปกติ — ให้จอ "กำลังเล่น" มีมิติ
    // ไม่แบนราบเหมือนหน้าอื่น (แทนที่จะดึงสีจากปกอัลบั้มจริงด้วย Palette ซึ่งต้องเพิ่ม dependency ใหม่)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.player_collapse))
            }
            Text(
                stringResource(R.string.player_now_playing_header),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp)) // ถ่วงน้ำหนักให้หัวข้อกึ่งกลางจริงๆ เทียบกับปุ่มซ้าย
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(state.currentThumbnailUrl)
                        .crossfade(AppConstants.IMAGE_CROSSFADE_MILLIS)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp), clip = false)
                        .clip(RoundedCornerShape(20.dp))
                )
                if (state.isResolving) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                state.currentTitle ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                state.currentArtist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = dragPositionMs ?: state.positionMs.toFloat(),
                valueRange = 0f..(state.durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { dragPositionMs = it },
                onValueChangeFinished = {
                    dragPositionMs?.let { viewModel.seekTo(it.toLong()) }
                    dragPositionMs = null
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDurationMillis((dragPositionMs ?: state.positionMs.toFloat()).toLong()),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(formatDurationMillis(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (state.isShuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        }
                    )
                }

                IconButton(onClick = { viewModel.previous() }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.player_previous),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // ปุ่มเล่น/หยุดหลักเด่นกว่าปุ่มอื่นชัดเจน แบบ YouTube Music/Spotify
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) {
                            stringResource(R.string.player_pause)
                        } else {
                            stringResource(R.string.player_play)
                        },
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.next() }, enabled = state.hasNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_next),
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                    Icon(
                        imageVector = if (state.repeatMode == RepeatMode.ONE) {
                            Icons.Default.RepeatOne
                        } else {
                            Icons.Default.Repeat
                        },
                        contentDescription = stringResource(R.string.player_repeat),
                        tint = if (state.repeatMode != RepeatMode.OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        }
                    )
                }
            }

            if (state.upcoming.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.player_up_next),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(state.upcoming, key = { it.orderPosition }) { item ->
                        UpcomingRow(item = item, onClick = { viewModel.playAtOrderPosition(item.orderPosition) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.track.thumbnailUrl)
                .crossfade(AppConstants.IMAGE_CROSSFADE_MILLIS)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(item.track.artist, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        item.track.durationSeconds?.let { seconds ->
            Text(formatDurationSeconds(seconds), style = MaterialTheme.typography.labelSmall)
        }
    }
}
