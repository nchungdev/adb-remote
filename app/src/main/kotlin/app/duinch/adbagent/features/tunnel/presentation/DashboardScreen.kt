package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    if (state.forceShowPairing) {
        WirelessPairingScreen(
            state = state,
            onBack = { onIntent(DashboardUiIntent.SetForceShowPairing(false)) },
            onPairingIntent = { onIntent(DashboardUiIntent.OnPairingIntent(it)) }
        )
        return
    }

    state.serverApprovalCode?.let { code ->
        ServerApprovalDialog(code = code, onCancel = { onIntent(DashboardUiIntent.CancelServerApproval) })
    }

    val hasUpdateDot = updaterState.status is UpdateStatus.Available ||
        updaterState.status is UpdateStatus.ReadyToInstall

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ADB Remote",
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
                        onClick = { onIntent(DashboardUiIntent.ScanQrCode) }
                    ) {
                        Icon(
                            imageVector = AppIcons.QrCode,
                            contentDescription = "Quét mã QR Remote",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = AppIcons.MoreVertical,
                                contentDescription = "Tùy chọn khác",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Quét mã QR Remote") },
                                onClick = {
                                    menuExpanded = false
                                    onIntent(DashboardUiIntent.ScanQrCode)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.QrCode,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ghép đôi không dây") },
                                onClick = {
                                    menuExpanded = false
                                    onIntent(DashboardUiIntent.SetForceShowPairing(true))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.LinkWireless,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hướng dẫn sử dụng") },
                                onClick = {
                                    menuExpanded = false
                                    onIntent(DashboardUiIntent.ShowOnboarding)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mở Terminal") },
                                onClick = {
                                    menuExpanded = false
                                    onIntent(DashboardUiIntent.OnTermuxIntent(TermuxUiIntent.OpenConsole))
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AppIcons.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            DashboardBottomBar(
                state = state,
                hasUpdateDot = hasUpdateDot,
                onIntent = onIntent
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The bottom bar sits behind the keyboard: count only the keyboard part above it.
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                when (state.selectedTab) {
                    AppTab.CONNECT -> TunnelHomeTab(
                        state = state,
                        onIntent = onIntent,
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

    // Tooltip Guide Walkthrough Overlay
    OnboardingGuideOverlay(
        currentStep = state.activeOnboardingStep,
        onNextStep = { onIntent(DashboardUiIntent.NextOnboardingStep) },
        onDismiss = { onIntent(DashboardUiIntent.DismissOnboarding) }
    )
}
