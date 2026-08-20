package com.example.appcall.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

// ── Dark Theme Tokens (Sleek Obsidian & Cyber Indigo) ──
val DarkBgColor = Color(0xFF0C0D0F)
val DarkSurface1 = Color(0xFF17181B)
val DarkSurface2 = Color(0xFF1D1F23)
val DarkBorderColor = Color(0x1AFFFFFF)
val DarkBorderStrong = Color(0x2EFFFFFF)
val DarkText1 = Color(0xFFEDECE9)
val DarkText2 = Color(0xFF8B8D93)
val DarkText3 = Color(0xFF5A5C63)

// ── Light Theme Tokens (Modern Clean Enterprise) ──
val LightBgColor = Color(0xFFF6F8FA)
val LightSurface1 = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFEEF2F6)
val LightBorderColor = Color(0xFFE2E8F0)
val LightBorderStrong = Color(0xFFCBD5E1)
val LightText1 = Color(0xFF0F172A)
val LightText2 = Color(0xFF475569)
val LightText3 = Color(0xFF94A3B8)

// Dynamic Color Tokens (Defaults to Dark for direct reference)
var BgColor by mutableStateOf(DarkBgColor)
var Surface1 by mutableStateOf(DarkSurface1)
var Surface2 by mutableStateOf(DarkSurface2)
var BorderColor by mutableStateOf(DarkBorderColor)
var BorderStrong by mutableStateOf(DarkBorderStrong)
var Text1 by mutableStateOf(DarkText1)
var Text2 by mutableStateOf(DarkText2)
var Text3 by mutableStateOf(DarkText3)

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
val DarkIndigo = DarkBgColor
val ElectricViolet = AccentColor
val NeonTeal = SuccessColor
val SlateBackground = DarkBgColor
val CardBackground = DarkSurface1
val OnCardText = DarkText1

private val DarkColorScheme = darkColorScheme(
    primary = AccentColor,
    secondary = SuccessColor,
    background = DarkBgColor,
    surface = DarkSurface1,
    onPrimary = DarkText1,
    onSecondary = Color.Black,
    onBackground = DarkText1,
    onSurface = DarkText1
)

private val LightColorScheme = lightColorScheme(
    primary = AccentColor,
    secondary = SuccessColor,
    background = LightBgColor,
    surface = LightSurface1,
    onPrimary = LightText1,
    onSecondary = Color.White,
    onBackground = LightText1,
    onSurface = LightText1
)

@Composable
fun AppCallTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemDark
    }

    LaunchedEffect(isDark) {
        if (isDark) {
            BgColor = DarkBgColor
            Surface1 = DarkSurface1
            Surface2 = DarkSurface2
            BorderColor = DarkBorderColor
            BorderStrong = DarkBorderStrong
            Text1 = DarkText1
            Text2 = DarkText2
            Text3 = DarkText3
        } else {
            BgColor = LightBgColor
            Surface1 = LightSurface1
            Surface2 = LightSurface2
            BorderColor = LightBorderColor
            BorderStrong = LightBorderStrong
            Text1 = LightText1
            Text2 = LightText2
            Text3 = LightText3
        }
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
