package com.cid.musicapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.cid.musicapp.config.AppConstants
import com.cid.musicapp.R
import com.cid.musicapp.di.AppContainer
import com.cid.musicapp.player.PlaybackUiState
import com.cid.musicapp.ui.favorites.FavoritesScreen
import com.cid.musicapp.ui.favorites.FavoritesViewModel
import com.cid.musicapp.ui.player.PlayerScreen
import com.cid.musicapp.ui.player.PlayerViewModel
import com.cid.musicapp.ui.search.SearchScreen
import com.cid.musicapp.ui.search.SearchViewModel
import com.cid.musicapp.ui.settings.SettingsScreen
import com.cid.musicapp.ui.settings.SettingsViewModel
import com.cid.musicapp.ui.update.UpdateBanner
import com.cid.musicapp.ui.update.UpdateViewModel

private const val ROUTE_SEARCH = "search"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_SETTINGS = "settings"

private data class BottomTab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val playbackState by container.playerController.state.collectAsStateWithLifecycle()

    val updateViewModel: UpdateViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                UpdateViewModel(
                    currentBuildNumber = com.cid.musicapp.BuildConfig.BUILD_NUMBER,
                    checker = container.appUpdateChecker,
                    installer = container.apkInstaller,
                    appSettings = container.appSettings
                )
            }
        }
    )

    val tabs = listOf(
        BottomTab(ROUTE_SEARCH, R.string.nav_search, Icons.Default.Search),
        BottomTab(ROUTE_FAVORITES, R.string.nav_favorites, Icons.Default.Favorite),
        BottomTab(ROUTE_SETTINGS, R.string.nav_settings, Icons.Default.Settings)
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // route ปัจจุบัน ใช้ตัดสินใจว่าควรซ่อน mini player bar + แถบแท็บด้านล่างไหม
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    // สลับไปแท็บหลักด้านล่าง (ค้นหา/ตั้งค่า) เท่านั้น — ใช้ popUpTo+restoreState กันกดสลับแท็บไปมาแล้ว
    // back stack พอกจนกด back ไม่ออก (คงตำแหน่งที่เลื่อนไว้ของแต่ละแท็บไว้ให้ด้วย)
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // เปิดหน้า "กำลังเล่น" แบบ push ธรรมดา (ไม่ใช่แท็บ) — กด back หรือปุ่มพับที่หัวจอแล้วกลับมาแท็บเดิมที่ค้างไว้เป๊ะ
    fun openPlayer() {
        navController.navigate(ROUTE_PLAYER) { launchSingleTop = true }
    }

    // แสดง error เป็น Snackbar ครั้งเดียวต่อ error หนึ่งอัน แล้วเคลียร์ทิ้ง
    LaunchedEffect(playbackState.errorMessage) {
        val message = playbackState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            container.playerController.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // ซ่อน mini player bar + แถบแท็บตอนอยู่ในหน้า "กำลังเล่น" เต็มจอ กันโชว์ปุ่มเล่น/หยุดซ้ำซ้อน
            // สองชุดพร้อมกัน (อันหนึ่งในหน้าเต็มจอ อีกอันในแถบเล็กด้านล่าง) และเปิดพื้นที่จอให้เต็มที่
            if (currentRoute != ROUTE_PLAYER) {
                Column {
                    if (playbackState.currentTitle != null) {
                        MiniPlayerBar(
                            state = playbackState,
                            onTogglePlayPause = { container.playerController.togglePlayPause() },
                            onNext = { container.playerController.next() },
                            onPrevious = { container.playerController.previous() },
                            onDismiss = { container.playerController.stopAndDismiss() },
                            onClick = { openPlayer() }
                        )
                    }

                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            UpdateBanner(viewModel = updateViewModel)

            NavHost(
                navController = navController,
                startDestination = ROUTE_SEARCH,
                modifier = Modifier.weight(1f)
            ) {
                composable(ROUTE_SEARCH) {
                    val viewModel: SearchViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { SearchViewModel(container.musicRepository, container.appSettings) }
                        }
                    )
                    val addedToQueueMessage = stringResource(R.string.search_added_to_queue)
                    val playsNextMessage = stringResource(R.string.search_plays_next)

                    SearchScreen(
                        viewModel = viewModel,
                        onTrackSelected = { tracks, index ->
                            container.playerController.playQueue(tracks, index)
                            openPlayer()
                        },
                        onAddToQueue = { track ->
                            container.playerController.addToQueue(track)
                            coroutineScope.launch { snackbarHostState.showSnackbar(addedToQueueMessage) }
                        },
                        onPlayNext = { track ->
                            container.playerController.playNext(track)
                            coroutineScope.launch { snackbarHostState.showSnackbar(playsNextMessage) }
                        }
                    )
                }

                composable(ROUTE_FAVORITES) {
                    val viewModel: FavoritesViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { FavoritesViewModel(container.appSettings) }
                        }
                    )
                    FavoritesScreen(
                        viewModel = viewModel,
                        onTrackSelected = { tracks, index ->
                            container.playerController.playQueue(tracks, index)
                            openPlayer()
                        }
                    )
                }

                composable(ROUTE_PLAYER) {
                    val viewModel: PlayerViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { PlayerViewModel(container.playerController, container.appSettings) }
                        }
                    )
                    PlayerScreen(
                        viewModel = viewModel,
                        onCollapse = { navController.popBackStack() }
                    )
                }

                composable(ROUTE_SETTINGS) {
                    val viewModel: SettingsViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                SettingsViewModel(container.appSettings, container.musicRepository)
                            }
                        }
                    )
                    SettingsScreen(
                        viewModel = viewModel,
                        updateViewModel = updateViewModel,
                        playbackState = playbackState
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
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
