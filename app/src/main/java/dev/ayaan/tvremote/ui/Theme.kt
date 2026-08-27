package dev.ayaan.tvremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A remote gets used in a dark room with the TV as the only light source, so the
// palette is built around near-black with one warm signal colour reserved for
// the select ring -- the same visual role a standby LED plays on real hardware.
object Ink {
    val Base = Color(0xFF0B0E14)
    val Panel = Color(0xFF141926)
    val Key = Color(0xFF1C2333)
    val KeyDown = Color(0xFF2A3348)
    val Hairline = Color(0xFF232B3D)
    val Signal = Color(0xFFE4B363)
    val SignalDim = Color(0xFF6B5430)
    val Text = Color(0xFFE8EAF0)
    val Muted = Color(0xFF7F8AA3)
    val Alert = Color(0xFFE07A5F)
}

private val scheme = darkColorScheme(
    primary = Ink.Signal,
    onPrimary = Ink.Base,
    background = Ink.Base,
    onBackground = Ink.Text,
    surface = Ink.Panel,
    onSurface = Ink.Text,
    surfaceVariant = Ink.Key,
    onSurfaceVariant = Ink.Muted,
    error = Ink.Alert
)

private val type = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        color = Ink.Text
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = Ink.Muted
    )
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()   // always dark by design
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
