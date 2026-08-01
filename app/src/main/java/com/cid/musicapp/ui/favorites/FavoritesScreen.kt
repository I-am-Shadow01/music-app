package com.cid.musicapp.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onTrackSelected: (List<Track>, Int) -> Unit
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.favorites_empty_state))
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(favorites, key = { it.id }) { track ->
            val index = favorites.indexOf(track)
            FavoriteRow(
                track = track,
                onClick = { onTrackSelected(favorites, index) },
                onRemove = { viewModel.removeFavorite(track.id) }
            )
        }
    }
}

@Composable
private fun FavoriteRow(track: Track, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(track.thumbnailUrl)
                .crossfade(AppConstants.IMAGE_CROSSFADE_MILLIS)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium)
        }
        track.durationSeconds?.let { seconds ->
            Text(formatDurationSeconds(seconds), style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.width(8.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = stringResource(R.string.favorites_remove),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
