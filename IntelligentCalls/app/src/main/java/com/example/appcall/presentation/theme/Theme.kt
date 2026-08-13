package com.example.appcall.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkIndigo = Color(0xFF111B21) // WhatsApp Dark Background
val ElectricViolet = Color(0xFF075E54) // WhatsApp Primary Dark Green
val NeonTeal = Color(0xFF25D366) // WhatsApp Light Accent Green
val SlateBackground = Color(0xFF111B21) // WhatsApp Background
val CardBackground = Color(0xFF202C33) // WhatsApp Surface Card Background
val OnCardText = Color(0xFFE9EDF0) // WhatsApp Light Text

private val DarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    secondary = NeonTeal,
    background = SlateBackground,
    surface = CardBackground,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = OnCardText,
    onSurface = OnCardText
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricViolet,
    secondary = NeonTeal,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun AppCallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
