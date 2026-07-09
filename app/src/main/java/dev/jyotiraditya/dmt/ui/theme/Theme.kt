package dev.jyotiraditya.dmt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val AccentPalette = listOf(
    "orange" to TuiAccent,
    "moss" to Color(0xFF9CB56C),
    "steel" to Color(0xFF6F9FBA),
    "mono" to MonoAccent,
)

val LocalAccent = staticCompositionLocalOf { TuiAccent }
val LocalIsLight = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = DarkBright,
    onPrimary = DarkBg,
    secondary = DarkFg,
    onSecondary = DarkBg,
    tertiary = DarkDim,
    onTertiary = DarkBg,
    background = DarkBg,
    onBackground = DarkFg,
    surface = DarkBg,
    onSurface = DarkFg,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkDim,
    outline = DarkLine,
    error = DarkRed,
)

private val LightColorScheme = lightColorScheme(
    primary = LightBright,
    onPrimary = LightBg,
    secondary = LightFg,
    onSecondary = LightBg,
    tertiary = LightDim,
    onTertiary = LightBg,
    background = LightBg,
    onBackground = LightFg,
    surface = LightBg,
    onSurface = LightFg,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightDim,
    outline = LightLine,
    error = LightRed,
)

@Composable
fun DMTTheme(isLight: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsLight provides isLight) {
        MaterialTheme(
            colorScheme = if (isLight) LightColorScheme else DarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
