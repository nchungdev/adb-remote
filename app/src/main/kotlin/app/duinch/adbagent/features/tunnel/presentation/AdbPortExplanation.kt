package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

internal fun computerAdbAddress(mappedPort: Int) = "127.0.0.1:$mappedPort"

internal fun computerAdbCommand(mappedPort: Int) = "adb -s ${computerAdbAddress(mappedPort)} shell"

/** The phone and the computer see this connection on different ports, so each row says whose it is. */
internal fun adbPortDetails(mappedPort: Int?, phonePort: Int): List<Pair<String, String>> =
    if (mappedPort == null) {
        emptyList()
    } else {
        listOf(
            "ADB điện thoại" to phonePort.toString(),
            "ADB máy tính" to computerAdbAddress(mappedPort)
        )
    }

@Suppress("DEPRECATION")
@Composable
internal fun AdbCommandFooter(mappedPort: Int) {
    val clipboard = LocalClipboardManager.current
    val command = computerAdbCommand(mappedPort)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = "Lệnh dùng trên máy tính:", style = MaterialTheme.typography.bodySmall)
        Text(
            text = command,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary
        )
        TextButton(onClick = { clipboard.setText(AnnotatedString(command)) }) { Text("Sao chép lệnh") }
    }
}
