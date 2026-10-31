package app.duinch.adbagent.features.termux.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AdbTheme

private val QUICK_COMMANDS = listOf(
    "adb devices -l",
    "adb tcpip 5555",
    "adb version",
    "getprop ro.product.model",
    "uname -a"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxConsoleSheet(
    state: TermuxUiState,
    onIntent: (TermuxUiIntent) -> Unit
) {
    if (!state.isSheetVisible) return

    val ext = AdbTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.lastIndex)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onIntent(TermuxUiIntent.CloseConsole) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Embedded Termux Console",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Trạng thái runtime: ${state.bootstrapState.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { onIntent(TermuxUiIntent.ClearLogs) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Xoá log")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_COMMANDS.forEach { preset ->
                    AssistChip(
                        onClick = {
                            onIntent(TermuxUiIntent.UpdateCommandInput(preset))
                            onIntent(TermuxUiIntent.SubmitCommand)
                        },
                        label = { Text(preset, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(ext.terminalSurface, MaterialTheme.shapes.small)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.logs) { line ->
                    Text(
                        text = line,
                        color = ext.terminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.commandInput,
                    onValueChange = { onIntent(TermuxUiIntent.UpdateCommandInput(it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Lệnh POSIX / ADB") },
                    placeholder = { Text("adb devices -l") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!state.isRunningCommand && state.commandInput.isNotBlank()) {
                                onIntent(TermuxUiIntent.SubmitCommand)
                            }
                        }
                    ),
                    singleLine = true
                )
                Button(
                    onClick = { onIntent(TermuxUiIntent.SubmitCommand) },
                    enabled = !state.isRunningCommand && state.commandInput.isNotBlank(),
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Text(if (state.isRunningCommand) "..." else "Chạy")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
