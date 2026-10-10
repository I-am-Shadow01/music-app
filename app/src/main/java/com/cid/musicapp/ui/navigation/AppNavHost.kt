package com.cid.musicapp.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch
import com.cid.musicapp.R
import com.cid.musicapp.di.AppContainer
import com.cid.musicapp.ui.favorites.FavoritesScreen
import com.cid.musicapp.ui.favorites.FavoritesViewModel
import com.cid.musicapp.ui.player.MiniPlayerBar
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

    // แสดง error เป็น Snackbar ครั้งเดียวต่อ error หนึ่งอัน แล้วเคลียร์ทิ้ง — มีปุ่ม "ลองใหม่" ให้กด
    // เล่นเพลงเดิมซ้ำอีกรอบได้ทันที ไม่ต้องกด next/เลือกเพลงใหม่เอง (เช่นตอนเน็ตหลุดชั่วขณะ)
    val retryActionLabel = stringResource(R.string.error_retry)
    LaunchedEffect(playbackState.errorMessage) {
        val message = playbackState.errorMessage
        if (message != null) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = retryActionLabel
            )
            container.playerController.dismissError()
            if (result == SnackbarResult.ActionPerformed) {
                container.playerController.retryPlayback()
            }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                            // ตามค่าตั้งค่า Radio: เปิด = เล่นเพลงที่กดแล้วเติมเพลงแนะนำต่อ (ไม่ใช้ผลค้นหาเป็นคิว)
                            container.playerController.playFromSearchResults(tracks, index)
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
                        },
                        onPlayAll = { tracks ->
                            if (tracks.isNotEmpty()) {
                                container.playerController.playQueue(tracks, 0)
                                openPlayer()
                            }
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
