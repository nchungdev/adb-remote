package app.duinch.adbagent.features.pairing.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.AdbAgentApp
import app.duinch.adbagent.core.designsystem.AdbAgentTheme
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
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
                val focusRequester = remember { FocusRequester() }

                LaunchedEffect(Unit) {
                    if (activePairPort <= 0 && container != null) {
                        container.adbDiscoveryGateway.startDiscovery()
                        container.adbDiscoveryGateway.observeDiscoveredPorts().collect { ports ->
                            ports.pairingPort?.let { activePairPort = it }
                            ports.connectPort?.let { activeConnectPort = it }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                fun submitCode() {
                    if (pairingCode.length != 6 || isSubmitting) return
                    val port = activePairPort.takeIf { it > 0 }
                    if (port == null || container == null) {
                        errorMessage = "Chưa phát hiện cổng ghép nối. Vui lòng mở mã trong Cài đặt."
                        return
                    }

                    isSubmitting = true
                    errorMessage = null
                    container.pairingNotificationGateway.reportPairingState(AdbPairingState.PairingInProgress)

                    coroutineScope.launch {
                        val res = container.pairLocalAdb(port, pairingCode, activeConnectPort)
                        res.fold(
                            onSuccess = { state ->
                                container.pairingNotificationGateway.reportPairingState(state)
                                Toast.makeText(this@PairingPromptActivity, "Ghép nối thành công!", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onFailure = { err ->
                                isSubmitting = false
                                val msg = err.message ?: "Sai mã ghép nối hoặc mã đã hết hạn"
                                container.pairingNotificationGateway.reportPairingState(AdbPairingState.Failed(msg))
                                errorMessage = msg
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (!isSubmitting) finish()
                        }
                        .imePadding(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { /* Block click pass-through */ },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, AdbTheme.colors.cardBorder),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.LinkWireless,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Ghép nối không dây",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    val subtitle = if (activePairPort > 0) {
                                        "Đã tìm thấy cổng: $activePairPort"
                                    } else {
                                        "Đang tìm mã ghép nối trên màn hình..."
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "Nhập mã 6 chữ số đang hiển thị:",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { input ->
                                    val digits = input.filter(Char::isDigit).take(6)
                                    pairingCode = digits
                                    if (errorMessage != null) errorMessage = null
                                    if (digits.length == 6) {
                                        submitCode()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Ví dụ: 123456") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitCode() }
                                )
                            )

                            if (errorMessage != null) {
                                Text(
                                    text = errorMessage.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { finish() },
                                    enabled = !isSubmitting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Đóng")
                                }
                                Button(
                                    onClick = { submitCode() },
                                    enabled = pairingCode.length == 6 && !isSubmitting,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isSubmitting) "Đang ghép..." else "Ghép nối")
                                }
                            }
                        }
                    }
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
