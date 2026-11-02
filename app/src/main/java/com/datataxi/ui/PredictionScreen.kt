package com.datataxi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.datataxi.data.JournalStore
import com.datataxi.domain.SlotPrediction
import com.datataxi.domain.ZonePredictor
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Tranches affichées dans le tableau : la tranche en cours puis les suivantes (2 heures). */
private const val TABLE_SLOTS = 12

/** Zones affichées en colonnes : les plus probables sur ces 2 heures. */
private const val TABLE_ZONES = 6

private val SLOT_COL: Dp = 96.dp
private val ZONE_COL: Dp = 64.dp

private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.CANADA_FRENCH)

private fun percent(p: Double) = if (p <= 0.0) "–" else "${Math.round(p * 100)} %"

@Composable
fun PredictionScreen() {
    val journal by JournalStore.entries.collectAsState()
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // L'heure en cours avance toute seule : le tableau suit la tranche actuelle.
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(30_000)
        }
    }

    val observations = remember(journal) { ZonePredictor.observations(journal) }
    val currentSlot = ZonePredictor.slotOf(now.toLocalTime())
    val predictions: List<SlotPrediction> = remember(observations, now.toLocalDate(), currentSlot) {
        val start = now.withMinute(currentSlot % 6 * 10).withSecond(0).withNano(0)
        List(TABLE_SLOTS) { i ->
            val t = start.plusMinutes(i * 10L)
            ZonePredictor.predict(observations, t.dayOfWeek, ZonePredictor.slotOf(t.toLocalTime()))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Prédictions par zone", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Maintenant : ${dayName(now.dayOfWeek)} ${"%02d:%02d".format(now.hour, now.minute)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (observations.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "Pas encore assez d'historique. Les prédictions apparaîtront quand le journal contiendra des appels avec une zone de départ.",
                    modifier = Modifier.padding(16.dp),
                )
            }
            return@Column
        }

        RecommendationCard(predictions.first())
        if (predictions.size > 1) NextSlotCard(predictions[1])
        PredictionTable(predictions)

        Text(
            "Pourcentage = part des appels partis de cette zone, observés le même jour de la semaine autour de la même heure (± 20 min). " +
                "« tous les jours » : l'historique de ce jour de la semaine est encore trop court. " +
                "Ce sont des tendances passées, pas une garantie.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecommendationCard(p: SlotPrediction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Recommandation · ${ZonePredictor.label(p.slot)}", style = MaterialTheme.typography.titleMedium)
            if (p.chances.isEmpty()) {
                Text("Aucun appel observé à cette heure-ci.")
            } else {
                p.chances.take(3).forEachIndexed { i, c ->
                    Text(
                        "${i + 1}. Zone ${c.zone} — ${percent(c.probability)}",
                        style = if (i == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Text(sourceLabel(p), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NextSlotCard(p: SlotPrediction) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Ensuite · ${ZonePredictor.label(p.slot)}", style = MaterialTheme.typography.titleSmall)
            Text(
                if (p.chances.isEmpty()) "Aucun appel observé."
                else p.chances.take(3).joinToString("   ") { "Zone ${it.zone} ${percent(it.probability)}" }
            )
        }
    }
}

private fun sourceLabel(p: SlotPrediction): String {
    val base = if (p.sameWeekday) "Basé sur ${p.days} ${dayName(p.dayOfWeek)}(s)" else "Basé sur tous les jours (${p.days} jours)"
    return "$base · ${p.samples} appels autour de cette heure"
}

@Composable
private fun PredictionTable(predictions: List<SlotPrediction>) {
    // Colonnes : les zones les plus probables sur l'ensemble des tranches affichées.
    val zones = remember(predictions) {
        predictions.flatMap { it.chances }
            .groupBy { it.zone }
            .mapValues { (_, list) -> list.sumOf { it.probability } }
            .entries.sortedByDescending { it.value }
            .take(TABLE_ZONES)
            .map { it.key }
    }
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val current = MaterialTheme.colorScheme.secondaryContainer

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                "Tableau heure × zone (10 min)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Column(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                Row {
                    Cell("Heure", SLOT_COL, bold = true)
                    zones.forEach { Cell("Zone $it", ZONE_COL, bold = true) }
                    Cell("Base", ZONE_COL, bold = true)
                }
                predictions.forEachIndexed { i, p ->
                    val best = p.chances.firstOrNull()?.zone
                    Row(Modifier.background(if (i == 0) current else Color.Transparent)) {
                        Cell(ZonePredictor.label(p.slot), SLOT_COL, bold = i == 0)
                        zones.forEach { z ->
                            Cell(
                                percent(p.probabilityOf(z)),
                                ZONE_COL,
                                bold = z == best,
                                background = if (z == best) highlight else Color.Transparent,
                            )
                        }
                        Cell(if (p.sameWeekday) "${p.days} j." else "tous", ZONE_COL)
                    }
                }
            }
            Text(
                "Ligne colorée : tranche en cours. Case en couleur : zone la plus probable de la tranche.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Cell(text: String, width: Dp, bold: Boolean = false, background: Color = Color.Transparent) {
    Box(
        modifier = Modifier
            .width(width)
            .background(background)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
