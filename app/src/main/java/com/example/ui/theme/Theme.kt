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
    primary = AccentCyan,
    background = SportsDarkBackground,
    surface = SportsDarkSurface,
    secondary = TextSecondary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SportsHeaderBg
  )

private val LightColorScheme =
  darkColorScheme( // We override light scheme with sport dark too because sports apps thrive on dark, sleek modes.
    primary = AccentCyan,
    background = SportsDarkBackground,
    surface = SportsDarkSurface,
    secondary = TextSecondary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SportsHeaderBg
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic system color overrides to lock the beautiful Elegant Dark brand aesthetic
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
