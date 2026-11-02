package app.duinch.adbagent.features.pairing.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.R
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState

@Composable
fun PairingSection(
    state: PairingUiState,
    onIntent: (PairingUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = AdbTheme.colors
    val dim = AdbTheme.dimens
    var showManualInput by rememberSaveable { mutableStateOf(false) }
    val isFailed = state.pairingState is AdbPairingState.Failed

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = dim.spacingXxl, vertical = dim.spacingM)
    ) {
        val statusText = when (val ps = state.pairingState) {
            is AdbPairingState.Failed -> ps.reason
            is AdbPairingState.PairingInProgress -> stringResource(R.string.pairing_in_progress)
            else -> when {
                state.discoveredPairingPort != null -> stringResource(R.string.pairing_port_found, state.discoveredPairingPort)
                state.discoveredConnectPort != null -> stringResource(R.string.pairing_connect_found, state.discoveredConnectPort)
                else -> null
            }
        }

        // Center / Main Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = dim.spacingXs, bottom = dim.spacingXs)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (statusText != null) {
                Surface(
                    color = if (isFailed) MaterialTheme.colorScheme.errorContainer else ext.statusConnectedContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFailed) MaterialTheme.colorScheme.error else ext.statusConnected,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = dim.spacingMd, vertical = dim.spacingS - 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(dim.spacingM))
            }

            // Big, clean GIF illustration view
            PairingGifGuide(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dim.cardGifHeight)
            )

            Spacer(modifier = Modifier.height(dim.spacingXl - 2.dp))

            // Step instructions card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(dim.radiusLarge),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = dim.spacingL, vertical = dim.spacingMd),
                    verticalArrangement = Arrangement.spacedBy(dim.spacingS)
                ) {
                    Text(
                        text = stringResource(R.string.pairing_steps_header),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    PairingStepRow(step = "1", text = stringResource(R.string.pairing_step_1))
                    PairingStepRow(step = "2", text = stringResource(R.string.pairing_step_2))
                    PairingStepRow(step = "3", text = stringResource(R.string.pairing_step_3))
                }
            }

            AnimatedVisibility(visible = showManualInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dim.spacingL),
                    verticalArrangement = Arrangement.spacedBy(dim.spacingM - 2.dp)
                ) {
                    OutlinedTextField(
                        value = state.pairingPortInput,
                        onValueChange = { onIntent(PairingUiIntent.UpdatePairingPort(it)) },
                        label = { Text(stringResource(R.string.pairing_port_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.pairingCodeInput,
                        onValueChange = { onIntent(PairingUiIntent.UpdatePairingCode(it)) },
                        label = { Text(stringResource(R.string.pairing_code_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onIntent(PairingUiIntent.SubmitPair) },
                        enabled = state.canSubmitPair,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dim.btnHeightNormal),
                        shape = RoundedCornerShape(dim.radiusMedium - 2.dp)
                    ) {
                        Text(
                            if (state.pairingState is AdbPairingState.PairingInProgress) stringResource(R.string.pairing_in_progress)
                            else stringResource(R.string.pairing_btn_confirm),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Bottom Action Area: Primary action docked at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dim.spacingM - 2.dp, bottom = dim.spacingS - 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dim.spacingS)
        ) {
            Button(
                onClick = { onIntent(PairingUiIntent.StartNotificationPairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dim.btnHeightPrimary),
                shape = RoundedCornerShape(dim.radiusMedium)
            ) {
                Icon(AppIcons.LinkWireless, contentDescription = null, modifier = Modifier.size(dim.iconSizeM))
                Spacer(modifier = Modifier.width(dim.spacingS))
                Text(
                    text = stringResource(R.string.pairing_btn_open_settings),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }

            val port = state.effectiveConnectPort
            if (port != null) {
                OutlinedButton(
                    onClick = { onIntent(PairingUiIntent.ActivateTcpip5555) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dim.btnHeightNormal),
                    shape = RoundedCornerShape(dim.radiusMedium)
                ) {
                    Text(stringResource(R.string.pairing_btn_reactivate_tcpip, port), maxLines = 1)
                }
            }

            TextButton(
                onClick = { showManualInput = !showManualInput }
            ) {
                Text(
                    text = if (showManualInput) stringResource(R.string.pairing_btn_manual_toggle_hide)
                    else stringResource(R.string.pairing_btn_manual_toggle_show),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PairingStepRow(step: String, text: String) {
    val dim = AdbTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dim.spacingM - 2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(dim.iconSizeL)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
