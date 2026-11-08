package com.animevostbr.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.animevostbr.core.ThemeMode

/** Global UI state (theme, TV mode). */
object UiState {
    var themeMode by mutableStateOf(ThemeMode.DARK)
    var tvMode by mutableStateOf(false)
    /** The activity is shown as a picture-in-picture window. */
    var inPip by mutableStateOf(false)
}

private val Accent = Color(0xFF00E676)
private val AccentDim = Color(0xFF00B05C)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF16161A),
    onSurfaceVariant = Color(0xFFB9B9C0),
    secondary = AccentDim,
    onSecondary = Color.Black,
    error = Color(0xFFCF6679)
)

private val LightColors = lightColorScheme(
    primary = AccentDim,
    onPrimary = Color.White,
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF17171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFEAEAEF),
    onSurfaceVariant = Color(0xFF54545C)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (UiState.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        else -> true
    }
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors) {
        // Surface provides LocalContentColor: without it Text and Icon default to black
        // and are invisible on the black background
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.background,
            contentColor = colors.onBackground,
            content = content
        )
    }
}
