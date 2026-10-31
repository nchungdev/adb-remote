package app.duinch.adbagent.features.pairing.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons

@Composable
internal fun SlideOneToggleIllustration() {
    val ext = AdbTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.LinkWireless,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = ext.statusConnectedContainer,
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(ext.statusConnected, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Gỡ lỗi không dây: BẬT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ext.statusConnected
                )
            }
        }
    }
}

@Composable
internal fun SlideTwoDialogIllustration() {
    val ext = AdbTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(ext.statusWarningContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Sliders,
                contentDescription = null,
                tint = ext.statusWarning,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "8  4  9  2  1  5",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun SlideThreeScreenshotIllustration() {
    val ext = AdbTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(ext.statusConnectedContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.CheckCircle,
                contentDescription = null,
                tint = ext.statusConnected,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
        ) {
            Text(
                text = "⚡ AI tự quét mã & kết nối",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
