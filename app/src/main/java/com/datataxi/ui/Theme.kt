package com.datataxi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème de DataTaxi : bleu marine, le même que le tableau de bord du Google Sheet.
 * Écrit ici pour qu'il ne change jamais (version d'Android, mise à jour de bibliothèque...).
 * Correspondance des couleurs : docs/couleurs.md.
 */
private val Navy = Color(0xFF1F3864)
private val LightBlue = Color(0xFFD8EAFF)
private val CardGrey = Color(0xFFEDF1F6)
private val Ink = Color(0xFF1C1B1F)

private val DataTaxiColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = LightBlue,
    onPrimaryContainer = Color(0xFF0B1E3A),
    secondary = Color(0xFF3D5A80),
    onSecondary = Color.White,
    secondaryContainer = LightBlue,
    onSecondaryContainer = Color(0xFF0B1E3A),
    tertiary = Color(0xFF3D5A80),
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = CardGrey,
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F8FB),
    surfaceContainer = CardGrey,
    surfaceContainerHigh = CardGrey,
    surfaceContainerHighest = CardGrey,
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFF9DEDC),
)

@Composable
fun DataTaxiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DataTaxiColors, content = content)
}
