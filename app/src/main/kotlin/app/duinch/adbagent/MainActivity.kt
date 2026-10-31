package app.duinch.adbagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import app.duinch.adbagent.features.tunnel.presentation.DashboardScreen
import app.duinch.adbagent.features.tunnel.presentation.DashboardUiEffect
import app.duinch.adbagent.features.tunnel.presentation.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* User grant/deny handled gracefully by ForegroundService */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        val appContainer = (application as AdbAgentApp).container

        setContent {
            AdbAgentTheme {
                val viewModel: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(appContainer)
                )
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(viewModel) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is DashboardUiEffect.ShowMessage ->
                                snackbarHostState.showSnackbar(effect.text)
                            DashboardUiEffect.LaunchDeveloperSettings ->
                                openDeveloperSettings()
                        }
                    }
                }

                DashboardScreen(
                    state = uiState,
                    snackbarHostState = snackbarHostState,
                    onIntent = viewModel::onIntent
                )
            }
        }
    }

    private fun openDeveloperSettings() {
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
        runCatching { startActivity(devIntent) }
            .onFailure { runCatching { startActivity(fallbackIntent) } }
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
