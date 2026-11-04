package app.duinch.adbagent.features.sharing.presentation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.duinch.adbagent.AdbAgentApp
import app.duinch.adbagent.core.designsystem.AdbAgentTheme
import app.duinch.adbagent.features.sharing.application.SendSharedFilesUseCase
import app.duinch.adbagent.features.sharing.application.shareTargets
import app.duinch.adbagent.features.sharing.data.acceptableShareUris
import app.duinch.adbagent.features.sharing.data.toSharedFile

/** Appears in the system share sheet. Stays open while sending: the read grant on a file ends with it. */
class ShareToComputerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AdbAgentApp).container
        val uris = acceptableShareUris(sharedUris(intent), "$packageName.fileprovider")
        val files = uris.mapNotNull { contentResolver.toSharedFile(it) }
        val targets = shareTargets(container.tunnelConfigRepository.getConnections())

        setContent {
            val vm: ShareViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ShareViewModel(
                            files,
                            targets,
                            SendSharedFilesUseCase(container.fileSendGateway) { address ->
                                container.serverApprovalGateway.measureLatencyMs(address) != null
                            }
                        )
                    }
                }
            )
            val state by vm.uiState.collectAsStateWithLifecycle()
            AdbAgentTheme(darkTheme = isSystemInDarkTheme()) {
                ShareDialog(state = state, onIntent = vm::onIntent, onClose = ::finish)
            }
        }
    }

    private fun sharedUris(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND -> listOfNotNull(streamExtra(intent))
        Intent.ACTION_SEND_MULTIPLE -> streamListExtra(intent)
        else -> emptyList()
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

    @Suppress("DEPRECATION")
    private fun streamListExtra(intent: Intent): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
}
