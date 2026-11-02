package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.presentation.PairingSection
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WirelessPairingScreen(
    state: DashboardUiState,
    onBack: () -> Unit,
    onPairingIntent: (PairingUiIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ghép đôi không dây",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.ArrowLeft,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PairingSection(
            state = state.pairing,
            onIntent = onPairingIntent,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
