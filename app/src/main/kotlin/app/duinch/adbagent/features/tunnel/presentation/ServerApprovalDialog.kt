package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ServerApprovalDialog(code: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Duyệt trên máy tính") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = code,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 3.sp
                )
                Text(
                    text = "Mở Dashboard trên máy tính chạy server, kiểm tra đúng mã này rồi bấm Cho Phép.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("Hủy") } }
    )
}
