package com.anderson.wifiprevent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF003B33),
    primaryContainer = Color(0xFF164D44),
    onPrimaryContainer = Color(0xFFB0F1E5),
    secondary = Color(0xFFB3C9C3),
    secondaryContainer = Color(0xFF283F39),
    onSecondaryContainer = Color(0xFFD2E8E0),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF27363B),
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = Color(0xFFB6C6CC),
    outline = Color(0xFF829A9F),
    outlineVariant = Color(0xFF3B5359)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1EFE7),
    onPrimaryContainer = Color(0xFF0C493E),
    secondary = Color(0xFF4B625B),
    secondaryContainer = Color(0xFFE3EFEA),
    onSecondaryContainer = Color(0xFF233D34),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = Color(0xFFE7EEF0),
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = Color(0xFF53666E),
    outline = Color(0xFF758C93),
    outlineVariant = Color(0xFFCCDADC)


)

@Composable
fun WifiPreventTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
