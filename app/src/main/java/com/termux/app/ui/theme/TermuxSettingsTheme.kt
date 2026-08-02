package com.termux.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TermuxRedPrimary = Color(0xFFC00021)
private val TermuxRedPrimaryDark = Color(0xFFFFB3AE)
private val TermuxRedSecondary = Color(0xFF9C4146)
private val TermuxRedSecondaryDark = Color(0xFFFFB3B4)
private val TermuxRedTertiary = Color(0xFF7B580D)
private val TermuxRedTertiaryDark = Color(0xFFEFC16C)

private val TermuxLightColorScheme = lightColorScheme(
    primary = TermuxRedPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD7),
    onPrimaryContainer = Color(0xFF410004),
    secondary = TermuxRedSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADA),
    onSecondaryContainer = Color(0xFF3B080B),
    tertiary = TermuxRedTertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDAF),
    onTertiaryContainer = Color(0xFF271900),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF201A19),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF201A19),
    surfaceVariant = Color(0xFFF5DDDB),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF)
)

private val TermuxDarkColorScheme = darkColorScheme(
    primary = TermuxRedPrimaryDark,
    onPrimary = Color(0xFF68000B),
    primaryContainer = Color(0xFF930010),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = TermuxRedSecondaryDark,
    onSecondary = Color(0xFF5F1317),
    secondaryContainer = Color(0xFF7C2A2E),
    onSecondaryContainer = Color(0xFFFFDADA),
    tertiary = TermuxRedTertiaryDark,
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5F4200),
    onTertiaryContainer = Color(0xFFFFDDAF),
    background = Color(0xFF201A19),
    onBackground = Color(0xFFEDE0DE),
    surface = Color(0xFF201A19),
    onSurface = Color(0xFFEDE0DE),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFF9F8C8A),
    outlineVariant = Color(0xFF534341)
)

@Composable
fun TermuxSettingsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        if (darkTheme) {
            TermuxDarkColorScheme
        } else {
            TermuxLightColorScheme
        }
    }

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = context as? Activity
            if (activity != null) {
                val window = activity.window
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}