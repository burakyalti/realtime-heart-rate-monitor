package net.hrapp.hr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design Document Colors
object HeartMonitorColors {
    // Main Colors
    val DarkBackground = Color(0xFF1a1a2e)
    val CardBackground = Color(0xFF16213e)
    val SurfaceDark = Color(0xFF0f0f23)
    val Background = Color(0xFF0D0D0D)  // WebView/Tab background

    // Primary
    val HeartRed = Color(0xFFE53935)
    val HeartRedDark = Color(0xFFB71C1C)
    val HeartPink = Color(0xFFFF5252)

    // Status Colors
    val Connected = Color(0xFF2ed573)      // Normal/Connected - Green
    val LowHeartRate = Color(0xFF3498db)   // Low HR - Blue
    val HighHeartRate = Color(0xFFffa502)  // High HR - Orange
    val Critical = Color(0xFFe74c3c)       // Critical/Error - Red
    val Offline = Color(0xFF95a5a6)        // Offline/Checking - Grey
    val NoSignal = Color(0xFFff6b6b)       // No Signal - Light Red

    // Text Colors
    val TextPrimary = Color.White
    val TextSecondary = Color(0xFFB0B0B0)
    val TextMuted = Color(0xFF808080)
}

private val DarkColorScheme = darkColorScheme(
    primary = HeartMonitorColors.HeartRed,
    secondary = HeartMonitorColors.HeartPink,
    tertiary = HeartMonitorColors.HeartRedDark,
    background = HeartMonitorColors.SurfaceDark,
    surface = HeartMonitorColors.DarkBackground,
    surfaceVariant = HeartMonitorColors.CardBackground,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = HeartMonitorColors.TextSecondary
)

@Composable
fun HeartMonitorTheme(
    content: @Composable () -> Unit
) {
    // Always use dark theme
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
