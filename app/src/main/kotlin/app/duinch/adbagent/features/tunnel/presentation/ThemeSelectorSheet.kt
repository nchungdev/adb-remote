package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.R
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorSheet(
    currentTheme: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dim = AdbTheme.dimens

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dim.spacingXl, vertical = dim.spacingS)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(dim.spacingM)
        ) {
            Text(
                text = stringResource(R.string.settings_theme_mode_title),
                style = MaterialTheme.typography.titleLarge
            )

            ThemeOptionRow(
                label = stringResource(R.string.settings_theme_system),
                selected = currentTheme == "system",
                onClick = {
                    onSelect("system")
                    onDismiss()
                }
            )

            ThemeOptionRow(
                label = stringResource(R.string.settings_theme_light),
                selected = currentTheme == "light",
                onClick = {
                    onSelect("light")
                    onDismiss()
                }
            )

            ThemeOptionRow(
                label = stringResource(R.string.settings_theme_dark),
                selected = currentTheme == "dark",
                onClick = {
                    onSelect("dark")
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(dim.spacingL))
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val dim = AdbTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = dim.spacingM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(dim.spacingM))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
