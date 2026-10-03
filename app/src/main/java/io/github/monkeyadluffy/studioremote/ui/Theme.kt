package io.github.monkeyadluffy.studioremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF161616)
    val SurfaceHigh = Color(0xFF1F1F1F)
    val Border = Color(0xFF2A2A2A)
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFF9A9A9A)
    val TextFaint = Color(0xFF6B6B6B)
    val Avatar = Color(0xFFD94A4A)
    val Accent = Color(0xFF3DDC84)
    val Error = Color(0xFFFF6B6B)
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = C.SurfaceHigh,
    onSecondary = Color.White,
    background = C.Bg,
    onBackground = C.Text,
    surface = C.Bg,
    onSurface = C.Text,
    surfaceVariant = C.Surface,
    onSurfaceVariant = C.TextDim,
    surfaceContainer = C.Surface,
    surfaceContainerHigh = C.SurfaceHigh,
    surfaceContainerHighest = C.SurfaceHigh,
    surfaceContainerLow = C.Surface,
    outline = C.Border,
    outlineVariant = C.Border,
    error = C.Error,
)

private val base = Typography()
private val typography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.5.sp, lineHeight = 25.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
)

val DrawerItemStyle = TextStyle(fontSize = 19.sp, color = C.Text, fontWeight = FontWeight.Normal)

@Composable
fun UnslothTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
