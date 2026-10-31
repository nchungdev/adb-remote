package app.duinch.adbagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.duinch.adbagent.core.designsystem.AdbAgentTheme
import app.duinch.adbagent.features.tunnel.data.TunnelForegroundService
import app.duinch.adbagent.features.tunnel.presentation.AppTab
import app.duinch.adbagent.features.tunnel.presentation.DashboardScreen
import app.duinch.adbagent.features.tunnel.presentation.DashboardUiEffect
import app.duinch.adbagent.features.tunnel.presentation.DashboardUiIntent
import app.duinch.adbagent.features.tunnel.presentation.DashboardViewModel
import app.duinch.adbagent.features.updater.presentation.UpdaterUiEffect
import app.duinch.adbagent.features.updater.presentation.UpdaterUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdaterViewModel

class MainActivity : ComponentActivity() {

    private var onUnknownSourcesReturned: (() -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* User grant/deny handled gracefully by ForegroundService */ }

    private val unknownSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onUnknownSourcesReturned?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val appContainer = (application as AdbAgentApp).container
        if (appContainer.tunnelConfigRepository.getConfig().runInBackground) {
            TunnelForegroundService.start(this)
        }

        setContent {
            AdbAgentTheme {
                val dashboardVm: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(appContainer)
                )
                val updaterVm: UpdaterViewModel = viewModel(
                    factory = UpdaterViewModel.factory(appContainer)
                )
                val uiState by dashboardVm.uiState.collectAsStateWithLifecycle()
                val updaterState by updaterVm.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                onUnknownSourcesReturned = {
                    if (packageManager.canRequestPackageInstalls()) {
                        updaterVm.onIntent(UpdaterUiIntent.InstallDownloadedApk)
                    }
                }

                BackHandler(enabled = uiState.selectedTab != AppTab.CONNECT) {
                    dashboardVm.onIntent(DashboardUiIntent.SelectTab(AppTab.CONNECT))
                }

                LaunchedEffect(uiState.cfAuthCookie) {
                    if (uiState.cfAuthCookie.contains("CF_Authorization=")) {
                        updaterVm.onIntent(UpdaterUiIntent.CheckForUpdate)
                    }
                }

                LaunchedEffect(dashboardVm) {
                    dashboardVm.effects.collect { effect ->
                        when (effect) {
                            is DashboardUiEffect.ShowMessage ->
                                snackbarHostState.showSnackbar(effect.text)
                            DashboardUiEffect.LaunchDeveloperSettings ->
                                openDeveloperSettings()
                        }
                    }
                }

                LaunchedEffect(updaterVm) {
                    updaterVm.effects.collect { effect ->
                        when (effect) {
                            is UpdaterUiEffect.ShowMessage ->
                                snackbarHostState.showSnackbar(effect.text)
                            UpdaterUiEffect.OpenInstallUnknownAppsSettings ->
                                openInstallUnknownAppsSettings()
                            UpdaterUiEffect.RequestCloudflareLogin ->
                                dashboardVm.onIntent(DashboardUiIntent.OpenCloudflareLogin)
                        }
                    }
                }

                DashboardScreen(
                    state = uiState,
                    updaterState = updaterState,
                    snackbarHostState = snackbarHostState,
                    onIntent = dashboardVm::onIntent,
                    onUpdaterIntent = updaterVm::onIntent
                )
            }
        }
    }

    private fun openInstallUnknownAppsSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName")
        )
        runCatching { unknownSourcesLauncher.launch(intent) }
    }

    private fun openDeveloperSettings() {
        val wirelessIntent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
        runCatching { startActivity(wirelessIntent) }
            .onFailure {
                runCatching { startActivity(devIntent) }
                    .onFailure { runCatching { startActivity(fallbackIntent) } }
            }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
