package app.duinch.adbagent.features.pairing.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.AdbAgentApp
import app.duinch.adbagent.core.designsystem.AdbAgentTheme
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import kotlinx.coroutines.launch

class PairingPromptActivity : ComponentActivity() {

    private var activePairPort by mutableIntStateOf(-1)
    private var activeConnectPort by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseIntent(intent)

        val app = application as? AdbAgentApp
        val container = app?.container

        setContent {
            AdbAgentTheme {
                val coroutineScope = rememberCoroutineScope()
                var pairingCode by remember { mutableStateOf("") }
                var isSubmitting by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    if (activePairPort <= 0 && container != null) {
                        container.adbDiscoveryGateway.startDiscovery()
                        container.adbDiscoveryGateway.observeDiscoveredPorts().collect { ports ->
                            ports.pairingPort?.let { activePairPort = it }
                            ports.connectPort?.let { activeConnectPort = it }
                        }
                    }
                }

                fun submitCode(codeToSubmit: String) {
                    if (codeToSubmit.length != 6 || isSubmitting) return
                    val port = activePairPort.takeIf { it > 0 }
                    if (port == null || container == null) {
                        errorMessage = "Chưa phát hiện cổng ghép nối trên máy"
                        return
                    }

                    isSubmitting = true
                    errorMessage = null
                    container.pairingNotificationGateway.reportPairingState(AdbPairingState.PairingInProgress)

                    coroutineScope.launch {
                        val res = container.pairLocalAdb(port, codeToSubmit, activeConnectPort)
                        res.fold(
                            onSuccess = { state ->
                                container.pairingNotificationGateway.reportPairingState(state)
                                Toast.makeText(this@PairingPromptActivity, "Ghép nối thành công!", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onFailure = { err ->
                                isSubmitting = false
                                val msg = err.message ?: "Mã không đúng hoặc đã hết hạn"
                                container.pairingNotificationGateway.reportPairingState(AdbPairingState.Failed(msg))
                                errorMessage = msg
                                pairingCode = ""
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (!isSubmitting) finish()
                        }
                        .statusBarsPadding()
                        .padding(top = 10.dp, start = 14.dp, end = 14.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PairingPromptCard(
                        pairingCode = pairingCode,
                        activePairPort = activePairPort,
                        isSubmitting = isSubmitting,
                        errorMessage = errorMessage,
                        onCodeChange = {
                            pairingCode = it
                            errorMessage = null
                        },
                        onSubmit = { submitCode(it) },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    private fun parseIntent(intent: Intent?) {
        val pairPort = intent?.getIntExtra(EXTRA_PAIR_PORT, -1) ?: -1
        if (pairPort > 0) activePairPort = pairPort
        val connPort = intent?.getIntExtra(EXTRA_CONN_PORT, -1)?.takeIf { it > 0 }
        if (connPort != null) activeConnectPort = connPort
    }

    companion object {
        const val ACTION_PROMPT = "app.duinch.adbagent.ACTION_PAIRING_PROMPT"
        const val EXTRA_PAIR_PORT = "extra_pair_port"
        const val EXTRA_CONN_PORT = "extra_conn_port"

        fun createIntent(context: Context, pairPort: Int, connectPort: Int?): Intent {
            return Intent(context, PairingPromptActivity::class.java).apply {
                action = ACTION_PROMPT
                putExtra(EXTRA_PAIR_PORT, pairPort)
                if (connectPort != null) putExtra(EXTRA_CONN_PORT, connectPort)
            }
        }
    }
}
