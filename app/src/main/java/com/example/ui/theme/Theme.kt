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
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = LightBlueDim,
    onPrimary = Color(0xFF002B66),
    primaryContainer = DarkBlueContainer,
    onPrimaryContainer = CalmBlueContainer,
    secondary = LighterTeal,
    onSecondary = Color(0xFF003730),
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = SoftTealContainer,
    background = MidnightBg,
    onBackground = SlateTextDark,
    surface = CharcoalSurface,
    onSurface = SlateTextDark,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = MutedSlateDark,
    outline = OutlineDark,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CalmBlue,
    onPrimary = Color.White,
    primaryContainer = CalmBlueContainer,
    onPrimaryContainer = Color(0xFF0D3E73),
    secondary = SoftTeal,
    onSecondary = Color.White,
    secondaryContainer = SoftTealContainer,
    onSecondaryContainer = Color(0xFF00695C),
    background = SoftBackground,
    onBackground = DeepSlateText,
    surface = PureWhite,
    onSurface = DeepSlateText,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = MutedSlate,
    outline = OutlineColor,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve our strict custom Luminous Calm branding
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
