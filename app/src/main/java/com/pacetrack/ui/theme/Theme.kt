package com.pacetrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF2D6A4F)
val OffWhite = Color(0xFFF5F7F2)
val Deep = Color(0xFF1B4332)
val Mint = Color(0xFFB7E4C7)
val Sage = Color(0xFF74C69D)

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = OffWhite,
    background = OffWhite,
    onBackground = Deep,
    surface = OffWhite,
    onSurface = Deep,
)

@Composable
fun PaceTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}