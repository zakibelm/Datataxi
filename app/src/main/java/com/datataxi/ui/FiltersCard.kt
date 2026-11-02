package com.datataxi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.datataxi.domain.CallType
import com.datataxi.domain.Filters

/** Heures proposées pour le début et la fin de la plage, toutes les 30 minutes. */
private val HALF_HOURS = (0 until 48).map { it * 30 }

private fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * Filtres : zones (plusieurs au choix), type d'appel, heure de début et de fin.
 * Repliée par défaut pour laisser la place aux résultats ; le résumé reste visible.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FiltersCard(
    filters: Filters,
    zones: List<String>,
    initiallyOpen: Boolean,
    onChange: (Filters) -> Unit,
) {
    var open by remember { mutableStateOf(initiallyOpen) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val title = if (filters.activeCount == 0) "Filtres" else "Filtres (${filters.activeCount} actifs)"
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary(filters), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { open = !open }) { Text(if (open) "Masquer" else "Modifier") }
            }
            if (!open) return@Column

            Text("Zones", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filters.zones.isEmpty(),
                    onClick = { onChange(filters.copy(zones = emptySet())) },
                    label = { Text("Toutes") },
                )
                zones.forEach { z ->
                    val on = z in filters.zones
                    FilterChip(
                        selected = on,
                        onClick = { onChange(filters.copy(zones = if (on) filters.zones - z else filters.zones + z)) },
                        label = { Text("Zone $z") },
                    )
                }
            }

            Text("Type d'appel", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = filters.type == null,
                    onClick = { onChange(filters.copy(type = null)) },
                    label = { Text("Tous") },
                )
                CallType.ALL.forEach { t ->
                    FilterChip(
                        selected = filters.type == t,
                        onClick = { onChange(filters.copy(type = if (filters.type == t) null else t)) },
                        label = { Text(t) },
                    )
                }
            }

            Text("Heures", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HourPicker(
                    label = "Début",
                    value = filters.startMinute?.let(::hhmm) ?: "Maintenant",
                    emptyChoice = "Maintenant",
                    onPick = { onChange(filters.copy(startMinute = it)) },
                )
                HourPicker(
                    label = "Fin",
                    value = filters.endMinute?.let(::hhmm) ?: "+ 2 h",
                    emptyChoice = "+ 2 h",
                    onPick = { onChange(filters.copy(endMinute = it)) },
                )
            }
            Text("La plage va jusqu'à 6 heures. Le jour de la semaine est celui d'aujourd'hui.", style = MaterialTheme.typography.bodySmall)

            if (filters.activeCount > 0) {
                TextButton(onClick = { onChange(Filters()) }) { Text("Effacer les filtres") }
            }
        }
    }
}

@Composable
private fun HourPicker(label: String, value: String, emptyChoice: String, onPick: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label : $value ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(emptyChoice) }, onClick = { onPick(null); expanded = false })
            HALF_HOURS.forEach { m ->
                DropdownMenuItem(text = { Text(hhmm(m)) }, onClick = { onPick(m); expanded = false })
            }
        }
    }
}

private fun summary(f: Filters): String {
    val zones = if (f.zones.isEmpty()) "toutes les zones" else "zones " + f.zones.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }.joinToString(", ")
    val type = f.type ?: "tous les types"
    val start = f.startMinute?.let(::hhmm) ?: "maintenant"
    val end = f.endMinute?.let(::hhmm) ?: "+ 2 h"
    return "$zones · $type · $start → $end"
}
