package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.termux.presentation.TermuxConsoleSheet
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.updater.domain.UpdateStatus
import app.duinch.adbagent.features.updater.presentation.UpdaterUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdaterUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    updaterState: UpdaterUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (DashboardUiIntent) -> Unit,
    onUpdaterIntent: (UpdaterUiIntent) -> Unit
) {
    val hasUpdateDot = updaterState.status is UpdateStatus.Available ||
        updaterState.status is UpdateStatus.ReadyToInstall

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ADB Agent",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.deviceAliasInput.ifBlank { "Embedded Termux • Zero-Trust" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(DashboardUiIntent.OnTermuxIntent(TermuxUiIntent.OpenConsole)) }
                    ) {
                        Icon(
                            imageVector = AppIcons.Terminal,
                            contentDescription = "Mở Terminal nội bộ",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            val isDark = isSystemInDarkTheme()
            val unselectedNavColor = MaterialTheme.colorScheme.onSurfaceVariant

            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // Tab 1: Kết nối (Màu Xanh Ngọc Emerald)
                NavigationBarItem(
                    selected = state.selectedTab == AppTab.CONNECT,
                    onClick = { onIntent(DashboardUiIntent.SelectTab(AppTab.CONNECT)) },
                    icon = {
                        Icon(
                            imageVector = AppIcons.ShieldBolt,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text(AppTab.CONNECT.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                        selectedTextColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                        indicatorColor = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5),
                        unselectedIconColor = unselectedNavColor,
                        unselectedTextColor = unselectedNavColor
                    )
                )

                // Tab 2: Nhật ký (Màu Lam Indigo Hoàng Gia)
                NavigationBarItem(
                    selected = state.selectedTab == AppTab.HISTORY,
                    onClick = { onIntent(DashboardUiIntent.SelectTab(AppTab.HISTORY)) },
                    icon = {
                        Icon(
                            imageVector = AppIcons.Clock,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text(AppTab.HISTORY.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                        selectedTextColor = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                        indicatorColor = if (isDark) Color(0xFF312E81) else Color(0xFFEEF2FF),
                        unselectedIconColor = unselectedNavColor,
                        unselectedTextColor = unselectedNavColor
                    )
                )

                // Tab 3: Cài đặt (Màu Tím Violet Sang Trọng)
                NavigationBarItem(
                    selected = state.selectedTab == AppTab.SETTINGS,
                    onClick = { onIntent(DashboardUiIntent.SelectTab(AppTab.SETTINGS)) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (hasUpdateDot) {
                                    Badge()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.Sliders,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    label = { Text(AppTab.SETTINGS.title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDark) Color(0xFFC084FC) else Color(0xFF9333EA),
                        selectedTextColor = if (isDark) Color(0xFFC084FC) else Color(0xFF9333EA),
                        indicatorColor = if (isDark) Color(0xFF581C87) else Color(0xFFF3E8FF),
                        unselectedIconColor = unselectedNavColor,
                        unselectedTextColor = unselectedNavColor
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (state.selectedTab) {
                    AppTab.CONNECT -> TunnelHomeTab(
                        state = state,
                        updaterState = updaterState,
                        onIntent = onIntent,
                        onUpdaterIntent = onUpdaterIntent,
                        onPairingIntent = { onIntent(DashboardUiIntent.OnPairingIntent(it)) }
                    )
                    AppTab.HISTORY -> TunnelHistoryTab(
                        state = state,
                        onIntent = onIntent
                    )
                    AppTab.SETTINGS -> TunnelSettingsTab(
                        state = state,
                        updaterState = updaterState,
                        onIntent = onIntent,
                        onUpdaterIntent = onUpdaterIntent
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    TermuxConsoleSheet(
        state = state.termux,
        onIntent = { onIntent(DashboardUiIntent.OnTermuxIntent(it)) }
    )
}
