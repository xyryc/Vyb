package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeAccent(val label: String, val color: Color) {
    SUNSET_AMBER("Sunset Amber", Color(0xFFFF9100)),
    EMERALD_GLOW("Emerald Glow", Color(0xFF1ED760)),
    COSMIC_BLUE("Cosmic Blue", Color(0xFF00E5FF)),
    NEON_PURPLE("Neon Purple", Color(0xFFD500F9)),
    CYBERPUNK_CORAL("Cyberpunk Coral", Color(0xFFFF2A7A)),
    CLAUDE_CLAY("Claude Clay", Color(0xFFCC5A37)),
    CLAUDE_APRICOT("Claude Apricot", Color(0xFFE0B8A5))
}

val LocalAccentColor = compositionLocalOf { Color(0xFF1ED760) }

private fun getDarkColorScheme(primaryColor: Color) =
  darkColorScheme(
    primary = primaryColor,
    secondary = SpotifyGrey,
    tertiary = SpotifyDarkGreen,
    background = SpotifyBlack,
    surface = SpotifySurface,
    onPrimary = SpotifyBlack,
    onSecondary = SpotifyWhite,
    onTertiary = SpotifyWhite,
    onBackground = SpotifyWhite,
    onSurface = SpotifyWhite,
    surfaceVariant = SpotifySurfaceVariant,
    onSurfaceVariant = SpotifyGrey
  )

@Composable
fun MyApplicationTheme(
  primaryColor: Color = Color(0xFF1ED760),
  content: @Composable () -> Unit,
) {
  val colorScheme = getDarkColorScheme(primaryColor)

  CompositionLocalProvider(LocalAccentColor provides primaryColor) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
