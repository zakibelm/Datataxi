package com.datataxi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Thème de DataTaxi : les couleurs violettes que l'application a depuis le début, écrites ici
 * pour qu'elles ne changent jamais (version d'Android, mise à jour de bibliothèque...).
 * Le tableau de bord du Google Sheet utilise les mêmes : voir docs/couleurs.md.
 */
private val DataTaxiColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

@Composable
fun DataTaxiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DataTaxiColors, content = content)
}
