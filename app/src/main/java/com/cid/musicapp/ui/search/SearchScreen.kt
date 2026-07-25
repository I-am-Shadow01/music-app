package com.cid.musicapp.ui.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cid.musicapp.R
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.data.repository.Track
import com.cid.musicapp.ui.util.formatDurationSeconds
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onTrackSelected: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // ใกล้เลื่อนถึงท้ายลิสต์แล้ว (เหลืออีกไม่กี่รายการ) → โหลดหน้าถัดไปล่วงหน้าให้เลย (infinite scroll)
    LaunchedEffect(listState, uiState.results.size) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= uiState.results.size - AppConstants.SEARCH_LOAD_MORE_THRESHOLD_ITEMS
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            singleLine = true,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear_query))
                        }
                    }
                    IconButton(onClick = { viewModel.search() }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }
            },
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { viewModel.search() }
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.errorMessage ?: stringResource(R.string.search_error))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.search_retry_button))
                        }
                    }
                }
            }

            uiState.query.isBlank() && uiState.results.isEmpty() -> {
                if (uiState.recentSearches.isNotEmpty()) {
                    RecentSearchesList(
                        recentSearches = uiState.recentSearches,
                        onSelect = viewModel::onRecentSearchSelected,
                        onRemove = viewModel::removeRecentSearch,
                        onClearAll = viewModel::clearRecentSearches
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.search_empty_state))
                    }
                }
            }

            uiState.results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results))
                }
            }

            else -> {
                LazyColumn(state = listState) {
                    itemsIndexed(uiState.results, key = { _, track -> track.id }) { index, track ->
                        TrackRow(
                            track = track,
                            onClick = { onTrackSelected(uiState.results, index) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) }
                        )
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearchesList(
    recentSearches: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.search_recent_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClearAll) {
                Text(stringResource(R.string.search_recent_clear_all))
            }
        }

        LazyColumn {
            items(recentSearches, key = { it }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(query) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(query, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { onRemove(query) }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_recent_remove))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.thumbnailUrl)
                    .crossfade(AppConstants.IMAGE_CROSSFADE_MILLIS)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium)
            }
            track.durationSeconds?.let { seconds ->
                Text(
                    formatDurationSeconds(seconds),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // กดค้าง track เพื่อเลือกว่าจะเล่นเลย / เล่นต่อจากนี้ / เพิ่มเข้าคิว (ไม่ทำให้คิวที่เล่นอยู่หายไปทันที)
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_play_now)) },
                onClick = {
                    menuExpanded = false
                    onClick()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_play_next)) },
                onClick = {
                    menuExpanded = false
                    onPlayNext()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_add_to_queue)) },
                onClick = {
                    menuExpanded = false
                    onAddToQueue()
                }
            )
        }
    }
}
