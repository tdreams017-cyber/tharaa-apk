package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// Global theme state for real-time reactive theme customisation
object ThemeConfig {
    var isDarkMode by mutableStateOf(false)
    var fontColorIndex by mutableStateOf(0) // 0: Default, 1: Sepia, 2: Emerald, 3: Royal Blue
}

// Helper to resolve font color dynamically for the reader
@Composable
fun getDynamicFontColor(): Color {
    return when (ThemeConfig.fontColorIndex) {
        1 -> if (ThemeConfig.isDarkMode) Color(0xFFFFCC80) else Color(0xFF5D4037) // Sepia Warm
        2 -> if (ThemeConfig.isDarkMode) Color(0xFFA5D6A7) else Color(0xFF1B5E20) // Deep Emerald
        3 -> if (ThemeConfig.isDarkMode) Color(0xFF90CAF9) else Color(0xFF0D47A1) // Royal Blue
        else -> if (ThemeConfig.isDarkMode) Color(0xFFEEEEEE) else Color(0xFF1D1B1E) // Default Charcoal/White
    }
}

// Editorial Aesthetic Palette
val EditorialBg = Color(0xFFFDF8F6)        // Warm, light editorial paper
val EditorialTextMain = Color(0xFF1D1B1E)  // Dark elegant charcoal text
val EditorialTextSub = Color(0xFF49454F)   // Muted medium-dark slate for subtitles
val EditorialPrimary = Color(0xFF6750A4)   // Deep Royal Purple (Brand)
val EditorialPrimaryContainer = Color(0xFFEADDFF) // Soft Lavender/Lilac
val EditorialSecondaryContainer = Color(0xFFE8DEF8) // Elegant warm lavender
val EditorialBorder = Color(0xFFCAC4D0)    // Delicate Editorial Border
val EditorialCardBg = Color(0xFFFFFFFF)    // Crisp white for cards

// Keep original variable names mapped to our Editorial Aesthetic to ensure compile compatibility
val EmeraldPrimary: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFFD4AF37) else EditorialPrimary

val EmeraldDark: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFF1A3E2E) else EditorialPrimaryContainer

val GoldAccent: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFFD4AF37) else EditorialPrimary

val GoldLight: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFF2A2A2A) else EditorialPrimaryContainer

val GoldDark = Color(0xFF4F378B)

val RichObsidian: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFF121212) else EditorialBg

val DeepForestCard: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFF1E1E1E) else EditorialCardBg

val DeepForestBorder: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFF333333) else EditorialBorder

val TextWhite: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFFEEEEEE) else EditorialTextMain

val TextGray: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFF9CA3AF) else EditorialTextSub

val TextGold: Color
    @Composable get() = if (ThemeConfig.isDarkMode) Color(0xFFEADDFF) else EditorialPrimary
