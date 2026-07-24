package com.cid.musicapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import com.cid.musicapp.R
import com.cid.musicapp.di.AppContainer
import com.cid.musicapp.player.PlaybackUiState
import com.cid.musicapp.ui.player.PlayerScreen
import com.cid.musicapp.ui.player.PlayerViewModel
import com.cid.musicapp.ui.search.SearchScreen
import com.cid.musicapp.ui.search.SearchViewModel
import com.cid.musicapp.ui.settings.SettingsScreen
import com.cid.musicapp.ui.settings.SettingsViewModel
import com.cid.musicapp.ui.update.UpdateBanner
import com.cid.musicapp.ui.update.UpdateViewModel

private const val ROUTE_SEARCH = "search"
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
        BottomTab(ROUTE_PLAYER, R.string.nav_player, Icons.Default.PlayArrow),
        BottomTab(ROUTE_SETTINGS, R.string.nav_settings, Icons.Default.Settings)
    )

    val snackbarHostState = remember { SnackbarHostState() }

    // นำทางไปแท็บหนึ่งๆ ด้วยกฎเดียวกันเสมอ ไม่ว่าจะกดจาก bottom nav, กดเพลงในผลค้นหา, หรือกด mini-player
    // (เดิมกดเพลง/mini-player ใช้ navigate() เปล่าๆ ส่วน bottom nav ใช้ popUpTo+restoreState — ผสมกัน
    // ทำให้ back stack ปนกันจนกดแท็บ "ค้นหา" ตอนอยู่หน้า Player แล้วไม่ไปไหน ต้องกด back เอาเอง)
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
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
            Column {
                if (playbackState.currentTitle != null) {
                    MiniPlayerBar(
                        state = playbackState,
                        onTogglePlayPause = { container.playerController.togglePlayPause() },
                        onClick = { navigateToTab(ROUTE_PLAYER) }
                    )
                }

                NavigationBar {
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = backStackEntry?.destination

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
                            initializer { SearchViewModel(container.musicRepository) }
                        }
                    )
                    SearchScreen(
                        viewModel = viewModel,
                        onTrackSelected = { tracks, index ->
                            container.playerController.playQueue(tracks, index)
                            navigateToTab(ROUTE_PLAYER)
                        }
                    )
                }

                composable(ROUTE_PLAYER) {
                    val viewModel: PlayerViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer { PlayerViewModel(container.playerController) }
                        }
                    )
                    PlayerScreen(viewModel = viewModel)
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = state.currentThumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                state.currentTitle ?: "",
                style = MaterialTheme.typography.bodyMedium,
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
    }
}
