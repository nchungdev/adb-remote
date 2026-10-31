package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState

@Composable
internal fun TunnelSwitchboardCard(
    state: DashboardUiState,
    connected: TunnelConnectionState.Connected?,
    animatedContainer: Color,
    animatedTint: Color,
    icon: ImageVector,
    badgeLabel: String,
    headline: String,
    subtitle: String,
    nowEpochMs: Long,
    onIntent: (DashboardUiIntent) -> Unit,
    onCopyText: (String) -> Unit
) {
    val ext = AdbTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(animatedContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedTint,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(color = animatedContainer, shape = CircleShape) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = animatedTint,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val mappedAddr = connected?.mappedServerPort?.let { "127.0.0.1:$it" }
                if (mappedAddr != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ext.surfaceElevated,
                        shape = CircleShape,
                        modifier = Modifier.clickable(role = Role.Button) {
                            onCopyText(mappedAddr)
                        }
                    ) {
                        Icon(
                            imageVector = AppIcons.Copy,
                            contentDescription = "Sao chép $mappedAddr",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(12.dp)
                        )
                    }
                }
            }

            val uptimeText = when {
                connected != null -> formatDurationVi((nowEpochMs - connected.connectedAtEpochMs).coerceAtLeast(0L))
                state.isUsbConnected -> "Cáp USB"
                else -> "--:--"
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryTile(
                        label = "IP Thiết bị",
                        value = state.localWifiIp.ifBlank { "--" },
                        monospace = state.localWifiIp.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (state.localWifiIp.isNotBlank()) {
                                    Modifier.clickable(role = Role.Button) { onCopyText(state.localWifiIp) }
                                } else Modifier
                            )
                    )
                    TelemetryTile(
                        label = "Cổng ADB",
                        value = state.localAdbPortInput.ifBlank { "5555" },
                        monospace = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryTile(
                        label = "Mạng",
                        value = state.activeNetwork.label,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "Thời gian",
                        value = uptimeText,
                        monospace = connected != null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            when {
                state.isTunnelActive -> {
                    Button(
                        onClick = { onIntent(DashboardUiIntent.ToggleTunnel) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(
                            text = "Ngắt kết nối đường hầm",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                    }
                }
                state.isUsbConnected -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onIntent(DashboardUiIntent.ToggleTunnel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(AppIcons.ShieldBolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mở đường hầm từ xa (P2P)",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onIntent(DashboardUiIntent.ToggleTunnel) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(AppIcons.ShieldBolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Bật kết nối Mac mini",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(AppIcons.LinkWireless, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ghép đôi không dây",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
