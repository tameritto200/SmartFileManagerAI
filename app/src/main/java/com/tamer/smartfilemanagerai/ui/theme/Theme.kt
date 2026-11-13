package com.tamer.smartfilemanagerai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF006A60),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF74F8E5),
    background = Color(0xFFF8FAFD),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFF3F8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF53DBC9),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005048),
    background = Color(0xFF0E141B),
    surface = Color(0xFF161E27),
    surfaceVariant = Color(0xFF222C38)
)

@Composable
fun SmartFileManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    // Full Arabic RTL Layout Direction Support (Section 10)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
