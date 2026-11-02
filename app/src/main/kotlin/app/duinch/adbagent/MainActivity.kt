package app.duinch.adbagent

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
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

    private val deleteScreenshotLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* User confirmed or dismissed system delete dialog */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleScreenshotDeletionIntent(intent)

        val appContainer = (application as AdbAgentApp).container
        if (appContainer.tunnelConfigRepository.getConfig().runInBackground) {
            TunnelForegroundService.start(this)
        }

        setContent {
            val dashboardVm: DashboardViewModel = viewModel(
                factory = DashboardViewModel.factory(appContainer)
            )
            val updaterVm: UpdaterViewModel = viewModel(
                factory = UpdaterViewModel.factory(appContainer)
            )
            val uiState by dashboardVm.uiState.collectAsStateWithLifecycle()
            val updaterState by updaterVm.uiState.collectAsStateWithLifecycle()
            val isDark = when (uiState.themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            AdbAgentTheme(darkTheme = isDark) {
                val snackbarHostState = remember { SnackbarHostState() }

                onUnknownSourcesReturned = {
                    if (packageManager.canRequestPackageInstalls()) {
                        updaterVm.onIntent(UpdaterUiIntent.InstallDownloadedApk)
                    }
                }

                BackHandler(enabled = uiState.forceShowPairing || uiState.selectedTab != AppTab.CONNECT) {
                    if (uiState.forceShowPairing) {
                        dashboardVm.onIntent(DashboardUiIntent.SetForceShowPairing(false))
                    } else {
                        dashboardVm.onIntent(DashboardUiIntent.SelectTab(AppTab.CONNECT))
                    }
                }

                DisposableEffect(dashboardVm) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action == ACTION_REMOTE_TUNNEL_STATE) {
                                val url = intent.getStringExtra("url").orEmpty()
                                val room = intent.getStringExtra("room").orEmpty()
                                val active = intent.getBooleanExtra("active", false)
                                dashboardVm.onIntent(
                                    DashboardUiIntent.OnServerRemoteTunnelPushed(
                                        url = url,
                                        roomCode = room,
                                        active = active
                                    )
                                )
                            }
                        }
                    }
                    val filter = IntentFilter(ACTION_REMOTE_TUNNEL_STATE)
                    ContextCompat.registerReceiver(
                        this@MainActivity,
                        receiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                    onDispose {
                        runCatching { unregisterReceiver(receiver) }
                    }
                }


                LaunchedEffect(dashboardVm) {
                    dashboardVm.effects.collect { effect ->
                        when (effect) {
                            is DashboardUiEffect.ShowMessage ->
                                snackbarHostState.showSnackbar(effect.text)
                            DashboardUiEffect.LaunchDeveloperSettings ->
                                openDeveloperSettings()
                            DashboardUiEffect.LaunchQrScanner ->
                                openQrScanner(dashboardVm)
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

    private fun openQrScanner(dashboardVm: DashboardViewModel) {
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(this, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val raw = barcode.rawValue.orEmpty()
                val parsed = app.duinch.remotetunnel.parser.RemoteQrPayloadParser.parse(raw)
                if (parsed != null) {
                    val room = parsed.roomCode.orEmpty()
                    val targetServer = parsed.lan?.takeIf { it.isNotBlank() } ?: parsed.url
                    dashboardVm.onIntent(
                        DashboardUiIntent.ApplyQrRemoteConfig(
                            roomCode = room,
                            serverUrl = targetServer
                        )
                    )
                } else if (raw.isNotBlank()) {
                    dashboardVm.onIntent(
                        DashboardUiIntent.ApplyQrRemoteConfig(
                            roomCode = raw.trim(),
                            serverUrl = null
                        )
                    )
                }
            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleScreenshotDeletionIntent(intent)
    }

    private fun handleScreenshotDeletionIntent(intent: Intent?) {
        val uriString = intent?.getStringExtra(EXTRA_DELETE_SCREENSHOT_URI) ?: return
        intent.removeExtra(EXTRA_DELETE_SCREENSHOT_URI)
        val uri = Uri.parse(uriString) ?: return
        requestScreenshotDeletion(uri)
    }

    private fun requestScreenshotDeletion(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            runCatching { deleteScreenshotLauncher.launch(request) }
        } else {
            runCatching {
                contentResolver.delete(uri, null, null)
            }.onFailure { ex ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ex is RecoverableSecurityException) {
                    val request = IntentSenderRequest.Builder(ex.userAction.actionIntent.intentSender).build()
                    runCatching { deleteScreenshotLauncher.launch(request) }
                }
            }
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

    companion object {
        const val EXTRA_DELETE_SCREENSHOT_URI = "extra_delete_screenshot_uri"
        const val ACTION_REMOTE_TUNNEL_STATE = "app.duinch.adbagent.ACTION_REMOTE_TUNNEL_STATE"
    }
}
