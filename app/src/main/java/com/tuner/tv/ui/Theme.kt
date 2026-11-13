package com.tuner.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Bg = Color(0xFF06080D)
val Ink = Color(0xFFF4F7FB)
val Muted = Color(0xFF8C98AA)
val Dim = Color(0xFF5B6677)
val Teal = Color(0xFF2DD4BF)
val Violet = Color(0xFF8B5CF6)
val Sky = Color(0xFF0EA5E9)
val RecRed = Color(0xFFFF4D5E)
val ErrorRed = Color(0xFFFF8A95)
val GlassFill = Color(0x0BFFFFFF)
val GlassLine = Color(0x14FFFFFF)
val Panel = Color(0xFF111620)

val BrandGradient = Brush.linearGradient(listOf(Teal, Violet))
val FocusFill = Brush.horizontalGradient(listOf(Teal.copy(alpha = 0.22f), Violet.copy(alpha = 0.20f)))
val SheetFill = Brush.linearGradient(listOf(Color(0xF00E121A), Color(0xF0161026)))

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal,
            onPrimary = Bg,
            background = Bg,
            onBackground = Ink,
            surface = Panel,
            onSurface = Ink,
            onSurfaceVariant = Muted,
        ),
        content = content,
    )
}
