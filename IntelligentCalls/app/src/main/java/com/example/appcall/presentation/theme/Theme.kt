package com.example.appcall.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Exact Design System Tokens from HTML/CSS Design Mockup
val BgColor = Color(0xFF0C0D0F)
val Surface1 = Color(0xFF17181B)
val Surface2 = Color(0xFF1D1F23)
val BorderColor = Color(0x1AFFFFFF) // rgba(255,255,255,0.07)
val BorderStrong = Color(0x2EFFFFFF) // rgba(255,255,255,0.13)

val Text1 = Color(0xFFEDECE9)
val Text2 = Color(0xFF8B8D93)
val Text3 = Color(0xFF5A5C63)

val AccentColor = Color(0xFF6C79F5)
val AccentDim = Color(0x216C79F5)
val AccentText = Color(0xFFAAB2FA)

val SuccessColor = Color(0xFF5FAE83)
val SuccessDim = Color(0x215FAE83)

val WarnColor = Color(0xFFC99A4A)
val WarnDim = Color(0x21C99A4A)

val DangerColor = Color(0xFFC4685F)
val DangerDim = Color(0x21C4685F)

// Deterministic Avatar Backgrounds
val AvatarBgA = Color(0xFF2A2E3D)
val AvatarBgB = Color(0xFF2E2A22)
val AvatarBgC = Color(0xFF232626)
val AvatarBgD = Color(0xFF26222C)
val AvatarBgE = Color(0xFF1E2626)

// Compatibility aliases
val DarkIndigo = BgColor
val ElectricViolet = AccentColor
val NeonTeal = SuccessColor
val SlateBackground = BgColor
val CardBackground = Surface1
val OnCardText = Text1

private val DarkColorScheme = darkColorScheme(
    primary = AccentColor,
    secondary = SuccessColor,
    background = BgColor,
    surface = Surface1,
    onPrimary = Text1,
    onSecondary = Color.Black,
    onBackground = Text1,
    onSurface = Text1
)

private val LightColorScheme = darkColorScheme(
    primary = AccentColor,
    secondary = SuccessColor,
    background = BgColor,
    surface = Surface1,
    onPrimary = Text1,
    onSecondary = Color.Black,
    onBackground = Text1,
    onSurface = Text1
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
