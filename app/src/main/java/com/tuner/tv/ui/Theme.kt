package com.tuner.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Bg = Color(0xFF0B0F14)
val Panel = Color(0xFF141A22)
val Teal = Color(0xFF2DD4BF)
val Muted = Color(0xFF8B97A6)
val ErrorRed = Color(0xFFF87171)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            onPrimary = Bg,
            background = Bg,
            onBackground = Color.White,
            surface = Panel,
            onSurface = Color.White,
            onSurfaceVariant = Muted,
            inverseSurface = Color(0xFFE6F7F5),
            inverseOnSurface = Bg,
        ),
        content = content,
    )
}
