package app.duinch.adbagent.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AdbExtendedColors(
    val statusConnected: Color,
    val statusConnectedContainer: Color,
    val statusWarning: Color,
    val statusWarningContainer: Color,
    val cardBorder: Color,
    val surfaceElevated: Color,
    val terminalSurface: Color,
    val terminalText: Color
)

private val DarkExtended = AdbExtendedColors(
    statusConnected = Color(0xFF34D399),
    statusConnectedContainer = Color(0xFF064E3B),
    statusWarning = Color(0xFFFBBF24),
    statusWarningContainer = Color(0xFF451A03),
    cardBorder = Color(0xFF1E293B),
    surfaceElevated = Color(0xFF162033),
    terminalSurface = Color(0xFF060A12),
    terminalText = Color(0xFFA5F3FC)
)

private val LightExtended = AdbExtendedColors(
    statusConnected = Color(0xFF047857),
    statusConnectedContainer = Color(0xFFD1FAE5),
    statusWarning = Color(0xFFB45309),
    statusWarningContainer = Color(0xFFFEF3C7),
    cardBorder = Color(0xFFD8E2EE),
    surfaceElevated = Color(0xFFEFF4FA),
    terminalSurface = Color(0xFF0F172A),
    terminalText = Color(0xFFE0F2FE)
)

val LocalAdbColors = staticCompositionLocalOf { DarkExtended }

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF06283D),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    outline = Color(0xFF334155)
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF047857),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFAFBFD),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF334155),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF94A3B8)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

object AdbTheme {
    val colors: AdbExtendedColors
        @Composable get() = LocalAdbColors.current
}

@Composable
fun AdbAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extended = if (darkTheme) DarkExtended else LightExtended
    CompositionLocalProvider(LocalAdbColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkPalette else LightPalette,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
