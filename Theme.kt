package com.shadowhat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BlackOnyx = Color(0xFF09090B)
val DarkGraphite = Color(0xFF18181B)
val CrimsonPrimary = Color(0xFFE11D48)
val CrimsonDark = Color(0xFF9F1239)
val GoldAccent = Color(0xFFF59E0B)
val WhiteText = Color(0xFFF4F4F5)
val GraySubtext = Color(0xFFA1A1AA)

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonPrimary,
    onPrimary = Color.White,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = Color.White,
    secondary = GoldAccent,
    onSecondary = Color.Black,
    background = BlackOnyx,
    onBackground = WhiteText,
    surface = DarkGraphite,
    onSurface = WhiteText
)

@Composable
fun ShadowHatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
