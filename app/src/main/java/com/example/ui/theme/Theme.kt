package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Cobalt400,
    onPrimary = InkPrimary,
    primaryContainer = Cobalt900,
    onPrimaryContainer = Cobalt100,
    secondary = Amber400,
    onSecondary = InkPrimary,
    secondaryContainer = Amber900,
    onSecondaryContainer = Amber100,
    tertiary = SignalGreenLight,
    onTertiary = InkPrimary,
    tertiaryContainer = SignalGreenBgDark,
    onTertiaryContainer = SignalGreenTextDark,
    background = DarkGraphiteBg,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkCardSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = DarkBorder,
    error = Color(0xFFF87171),
    onError = InkPrimary,
    errorContainer = AlertRedDark,
    onErrorContainer = AlertRedTextDark
)

private val LightColorScheme = lightColorScheme(
    primary = Cobalt600,
    onPrimary = Color.White,
    primaryContainer = Cobalt100,
    onPrimaryContainer = Cobalt900,
    secondary = Amber600,
    onSecondary = Color.White,
    secondaryContainer = Amber100,
    onSecondaryContainer = Amber900,
    tertiary = SignalGreen,
    onTertiary = Color.White,
    tertiaryContainer = SignalGreenBgLight,
    onTertiaryContainer = SignalGreenTextLight,
    background = AlabasterCanvas,
    onBackground = InkPrimary,
    surface = Color.White,
    onSurface = InkPrimary,
    surfaceVariant = MistSurface,
    onSurfaceVariant = InkSecondary,
    outline = CrispOutline,
    outlineVariant = HairlineBorder,
    error = AlertRed,
    onError = Color.White,
    errorContainer = AlertRedLight,
    onErrorContainer = AlertRedDark
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OneBookDialerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
