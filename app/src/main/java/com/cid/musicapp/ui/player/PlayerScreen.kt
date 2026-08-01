package com.cid.musicapp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cid.musicapp.R
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.player.PlaybackMode
import com.cid.musicapp.player.RepeatMode
import com.cid.musicapp.player.UpcomingItem
import com.cid.musicapp.ui.util.formatDurationMillis
import com.cid.musicapp.ui.util.formatDurationSeconds
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onCollapse: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

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
            SleepTimerButton(
                remainingMs = state.sleepTimerRemainingMs,
                onSetTimer = { minutes -> viewModel.setSleepTimer(minutes * 60_000L) },
                onCancelTimer = { viewModel.cancelSleepTimer() }
            )
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val nextMode = if (state.playbackMode == PlaybackMode.AUDIO) PlaybackMode.VIDEO else PlaybackMode.AUDIO
                viewModel.setPlaybackMode(nextMode)
            }) {
                Icon(
                    imageVector = if (state.playbackMode == PlaybackMode.AUDIO) Icons.Default.Videocam else Icons.Default.MusicNote,
                    contentDescription = if (state.playbackMode == PlaybackMode.AUDIO) {
                        stringResource(R.string.player_switch_to_video)
                    } else {
                        stringResource(R.string.player_switch_to_audio)
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (state.playbackMode == PlaybackMode.VIDEO) {
                Box(contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                // ใช้ปุ่มควบคุมชุดเดียวกับที่วาดเองด้านล่างของจอนี้ ไม่ใช้ controller ของ PlayerView ซ้ำ
                                useController = false
                                player = viewModel.rawPlayer()
                            }
                        },
                        update = { view -> view.player = viewModel.rawPlayer() },
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .aspectRatio(16f / 9f)
                            .shadow(elevation = 16.dp, shape = RoundedCornerShape(20.dp), clip = false)
                            .clip(RoundedCornerShape(20.dp))
                    )
                    if (state.isResolving) {
                        CircularProgressIndicator()
                    }
                }
            } else {
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

                Spacer(modifier = Modifier.height(12.dp))

                WaveformVisualizer(
                    audioSessionId = state.audioSessionId,
                    isPlaying = state.isPlaying,
                    modifier = Modifier.fillMaxWidth(0.82f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.currentTitle ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val isFavorite = state.currentTrackId != null && state.currentTrackId in favoriteIds
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleFavoriteCurrent()
                }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.player_favorite_remove else R.string.player_favorite_add
                        ),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
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
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleShuffle()
                }) {
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

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.previous()
                }) {
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
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        },
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

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.next()
                }, enabled = state.hasNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_next),
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.cycleRepeatMode()
                }) {
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

            Spacer(modifier = Modifier.height(4.dp))

            // แถวควบคุมรอง — seek ไว/ถอย 10 วิ และวนความเร็วเล่นเพลง (ไม่เด่นเท่าแถวหลักด้านบนตั้งใจ)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.seekBy(-AppConstants.SEEK_STEP_MILLIS)
                }) {
                    Icon(Icons.Default.Replay10, contentDescription = stringResource(R.string.player_seek_backward))
                }

                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.setPlaybackSpeed(nextPlaybackSpeed(state.playbackSpeed))
                }) {
                    Text(
                        "${formatSpeed(state.playbackSpeed)}x",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.seekBy(AppConstants.SEEK_STEP_MILLIS)
                }) {
                    Icon(Icons.Default.Forward10, contentDescription = stringResource(R.string.player_seek_forward))
                }
            }

            if (state.upcoming.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.player_up_next),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(state.upcoming, key = { it.orderPosition }) { item ->
                        UpcomingRow(
                            item = item,
                            onClick = { viewModel.playAtOrderPosition(item.orderPosition) },
                            onRemove = { viewModel.removeFromQueue(item.orderPosition) },
                            onMoveBy = { direction ->
                                viewModel.moveQueueItem(item.orderPosition, item.orderPosition + direction)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** ปุ่มตั้งเวลาปิดเพลงอัตโนมัติ (sleep timer) — กดเปิดเมนูเลือกเวลา, ทึบขึ้นมาเป็นสีธีมเมื่อกำลังนับถอยหลังอยู่ */
@Composable
private fun SleepTimerButton(
    remainingMs: Long?,
    onSetTimer: (minutes: Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val isActive = remainingMs != null

    Box {
        IconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuExpanded = true
        }) {
            Icon(
                imageVector = if (isActive) Icons.Default.Timer else Icons.Default.TimerOff,
                contentDescription = stringResource(R.string.player_sleep_timer),
                tint = if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (isActive) {
                remainingMs?.let {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.player_sleep_timer_remaining, formatDurationMillis(it))) },
                        onClick = {},
                        enabled = false
                    )
                    HorizontalDivider()
                }
            }
            AppConstants.SLEEP_TIMER_PRESET_MINUTES.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_sleep_timer_minutes, minutes)) },
                    onClick = {
                        menuExpanded = false
                        onSetTimer(minutes)
                    }
                )
            }
            if (isActive) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.player_sleep_timer_off)) },
                    onClick = {
                        menuExpanded = false
                        onCancelTimer()
                    }
                )
            }
        }
    }
}

/** วนไปยังความเร็วถัดไปใน AppConstants.PLAYBACK_SPEED_PRESETS (วนกลับตัวแรกเมื่อถึงตัวสุดท้าย) */
private fun nextPlaybackSpeed(current: Float): Float {
    val presets = AppConstants.PLAYBACK_SPEED_PRESETS
    val currentIndex = presets.indexOfFirst { abs(it - current) < 0.01f }.let { if (it == -1) 0 else it }
    return presets[(currentIndex + 1) % presets.size]
}

/** ตัดเลขทศนิยมท้ายที่ไม่จำเป็นออก เช่น 1.0 -> "1", 1.25 -> "1.25" */
private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) speed.toLong().toString() else speed.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpcomingRow(
    item: UpcomingItem,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveBy: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // ระยะสะสมของการลากแนวตั้งก่อนถือว่าเป็น "ขยับหนึ่งตำแหน่ง" — ประมาณความสูงหนึ่งแถวพอดี
    val dragThresholdPx = with(density) { 56.dp.toPx() }
    var dragAccumulatorPx by remember(item.orderPosition) { mutableFloatStateOf(0f) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onRemove()
            }
            // คืน false เสมอ กันไม่ให้แถวหายไปจาก composition ก่อนที่ LazyColumn จะอัปเดตลิสต์จริงตาม state ใหม่
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.player_remove_from_queue),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.player_reorder_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .pointerInput(item.orderPosition) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulatorPx += dragAmount.y
                                when {
                                    dragAccumulatorPx > dragThresholdPx -> {
                                        onMoveBy(1)
                                        dragAccumulatorPx = 0f
                                    }
                                    dragAccumulatorPx < -dragThresholdPx -> {
                                        onMoveBy(-1)
                                        dragAccumulatorPx = 0f
                                    }
                                }
                            }
                        )
                    }
            )
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
}
