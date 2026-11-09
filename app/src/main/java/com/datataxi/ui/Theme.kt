package com.datataxi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème de DataTaxi : rouge, blanc et noir (couleurs du logo).
 * Écrit ici pour qu'il ne change jamais (version d'Android, mise à jour de bibliothèque...).
 */
private val Black = Color(0xFF0A0A0A)
private val Red = Color(0xFFD10000)
private val CardGrey = Color(0xFFF5F5F5)
private val Ink = Color(0xFF1C1B1F)

private val DataTaxiColors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = Black,
    onPrimaryContainer = Color.White,
    secondary = Black,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3E3),
    onSecondaryContainer = Black,
    tertiary = Red,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CardGrey,
    onSurfaceVariant = Color(0xFF5C5C5C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = CardGrey,
    surfaceContainerHigh = CardGrey,
    surfaceContainerHighest = CardGrey,
    outline = Color(0xFF757575),
    outlineVariant = Color(0xFFE2E2E2),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFF9DEDC),
)

@Composable
fun DataTaxiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DataTaxiColors, content = content)
}
