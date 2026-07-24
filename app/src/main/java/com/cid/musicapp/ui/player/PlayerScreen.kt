package com.cid.musicapp.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cid.musicapp.R
import com.cid.musicapp.player.RepeatMode
import com.cid.musicapp.player.UpcomingItem
import com.cid.musicapp.ui.util.formatDurationMillis
import com.cid.musicapp.ui.util.formatDurationSeconds

@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.currentTitle == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.player_no_track))
        }
        return
    }

    // ตำแหน่ง slider ระหว่างลากนิ้ว ใช้ค่านี้แทนตำแหน่งจริงชั่วคราว กันกระตุกตอนลาก
    var dragPositionMs by remember { mutableStateOf<Float?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = state.currentThumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(220.dp)
                )
                if (state.isResolving) {
                    CircularProgressIndicator()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(state.currentTitle ?: "", style = MaterialTheme.typography.titleLarge)
            Text(state.currentArtist ?: "", style = MaterialTheme.typography.bodyMedium)

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

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
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

                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) {
                            stringResource(R.string.player_pause)
                        } else {
                            stringResource(R.string.player_play)
                        },
                        modifier = Modifier.size(56.dp)
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
        }

        if (state.upcoming.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.upcoming, key = { it.orderPosition }) { item ->
                    UpcomingRow(item = item, onClick = { viewModel.playAtOrderPosition(item.orderPosition) })
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
            model = item.track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(44.dp)
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
