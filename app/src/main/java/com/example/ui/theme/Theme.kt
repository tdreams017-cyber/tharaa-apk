package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Default to light Editorial Aesthetic
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    secondary = GoldAccent,
    tertiary = GoldLight,
    background = RichObsidian,
    surface = DeepForestCard,
    onPrimary = EditorialBg,
    onSecondary = EditorialBg,
    onTertiary = EditorialBg,
    onBackground = TextWhite,
    onSurface = TextWhite,
    outline = DeepForestBorder
  )

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

