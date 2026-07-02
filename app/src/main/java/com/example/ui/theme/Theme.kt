package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = SpotifyGreen,
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

private val LightColorScheme = DarkColorScheme // Spotify is dark-only!

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force premium dark mode
  dynamicColor: Boolean = false, // Use our brand identity colors
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
