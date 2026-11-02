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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.datataxi.data.HistoryStore
import com.datataxi.data.JournalStore
import com.datataxi.domain.RecurrenceBoard
import com.datataxi.domain.SlotRecurrences
import com.datataxi.domain.Weight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Tranches du tableau : la tranche en cours puis les suivantes (2 heures). */
private const val TABLE_SLOTS = 12

/** Au plus autant de zones en colonnes (les plus récurrentes sur ces 2 heures). */
private const val TABLE_ZONES = 8

private val SLOT_COL: Dp = 96.dp
private val ZONE_COL: Dp = 56.dp

private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.CANADA_FRENCH)

private fun weightColor(w: Weight): Color = when (w) {
    Weight.FAIBLE -> Color(0xFFFFF1C2)
    Weight.MOYEN -> Color(0xFFFFCF5C)
    Weight.FORT -> Color(0xFFF28C38)
    Weight.TRES_FORT -> Color(0xFFC62828)
}

private fun weightText(w: Weight): Color = if (w == Weight.TRES_FORT) Color.White else Color(0xFF1C1B1F)

@Composable
fun PredictionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val journal by JournalStore.entries.collectAsState()
    val history by HistoryStore.calls.collectAsState()
    val historyStatus by HistoryStore.status.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            withContext(Dispatchers.IO) { HistoryStore.refresh(context) }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) {
        HistoryStore.init(context)
        if (HistoryStore.isStale(context)) refresh()
        // L'heure en cours avance toute seule : le tableau suit la tranche actuelle.
        while (true) {
            now = LocalDateTime.now()
            delay(30_000)
        }
    }

    val index = remember(journal, history) {
        RecurrenceBoard.Index(RecurrenceBoard.merge(history, RecurrenceBoard.callsFromJournal(journal)))
    }

    RecurrencesContent(
        index = index,
        now = now,
        hasFullHistory = history.isNotEmpty(),
        historyStatus = historyStatus,
        refreshing = refreshing,
        onRefresh = { refresh() },
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    )
}

/** Affichage seul, sans réseau ni horloge : utilisé par l'écran et par les captures d'écran. */
@Composable
fun RecurrencesContent(
    index: RecurrenceBoard.Index,
    now: LocalDateTime,
    hasFullHistory: Boolean,
    historyStatus: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSlot = RecurrenceBoard.slotOf(now.toLocalTime())
    val rows: List<SlotRecurrences> = remember(index, now.toLocalDate(), currentSlot) {
        val start = now.withMinute(currentSlot % 6 * 10).withSecond(0).withNano(0)
        List(TABLE_SLOTS) { i ->
            val t = start.plusMinutes(i * 10L)
            RecurrenceBoard.recurrences(index, t.dayOfWeek, RecurrenceBoard.slotOf(t.toLocalTime()))
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Récurrences par zone", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Maintenant : ${dayName(now.dayOfWeek)} ${"%02d:%02d".format(now.hour, now.minute)}",
            style = MaterialTheme.typography.bodyMedium,
        )

        NowCard(rows.first())
        RecurrenceTable(rows)
        Legend()
        HistoryCard(index, hasFullHistory, historyStatus, refreshing, onRefresh)
    }
}

@Composable
private fun NowCard(r: SlotRecurrences) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Maintenant · ${RecurrenceBoard.label(r.slot)}", style = MaterialTheme.typography.titleMedium)
            if (r.zones.isEmpty()) {
                Text(
                    "Aucune zone n'a eu d'appel au moins 2 ${dayName(r.dayOfWeek)}s à cette heure-ci. Regarde les tranches suivantes ci-dessous.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                r.zones.forEach { z ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WeightChip(z.weight)
                        Text("Zone ${z.zone}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "${z.days} ${dayName(r.dayOfWeek)}s sur ${z.observedDays}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightChip(w: Weight) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(weightColor(w))
            .width(84.dp)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(w.label, color = weightText(w), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RecurrenceTable(rows: List<SlotRecurrences>) {
    // Colonnes : les zones récurrentes sur ces 2 heures, la plus forte d'abord.
    val zones = remember(rows) {
        rows.flatMap { it.zones }
            .groupBy { it.zone }
            .mapValues { (_, list) -> list.maxOf { it.days } * 100 + list.size }
            .entries.sortedByDescending { it.value }
            .take(TABLE_ZONES)
            .map { it.key }
    }
    val current = MaterialTheme.colorScheme.secondaryContainer

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                "Tableau heure × zone (10 min)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (zones.isEmpty()) {
                Text(
                    "Aucune récurrence dans les 2 prochaines heures.",
                    modifier = Modifier.padding(12.dp),
                )
                return@Column
            }
            Column(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                Row {
                    Cell("Heure", SLOT_COL, bold = true)
                    zones.forEach { Cell("Z $it", ZONE_COL, bold = true) }
                }
                rows.forEachIndexed { i, r ->
                    Row(Modifier.background(if (i == 0) current else Color.Transparent)) {
                        Cell(RecurrenceBoard.label(r.slot), SLOT_COL, bold = i == 0)
                        zones.forEach { z ->
                            val rec = r.of(z)
                            if (rec == null) Cell("", ZONE_COL)
                            else Cell(
                                "${rec.days}",
                                ZONE_COL,
                                bold = true,
                                background = weightColor(rec.weight),
                                color = weightText(rec.weight),
                            )
                        }
                    }
                }
            }
            Text(
                "Chiffre = nombre de jours (même jour de la semaine) où la zone a eu un appel à cette heure (± 10 min). Ligne colorée : maintenant.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Legend() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Poids d'une récurrence", style = MaterialTheme.typography.titleSmall)
            Weight.entries.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WeightChip(w)
                    val days = if (w == Weight.TRES_FORT) "${w.minDays} jours et plus" else "${w.minDays} jours"
                    Text("$days · appel dans la zone ${w.measured}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                "Mesuré en rejouant les 2 dernières semaines jour par jour (sept.–nov. 2026). Sans récurrence : environ 4 fois sur 100. Ce sont des habitudes passées, pas une garantie.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun HistoryCard(
    index: RecurrenceBoard.Index,
    hasFullHistory: Boolean,
    status: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasFullHistory) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Données utilisées", style = MaterialTheme.typography.titleSmall)
            val range = if (index.firstDate != null) " du ${index.firstDate} au ${index.lastDate}" else ""
            Text("${index.totalCalls} appels$range.", style = MaterialTheme.typography.bodyMedium)
            if (!hasFullHistory) {
                Text(
                    "Seulement le journal de cette tablette : les récurrences seront bien plus fiables avec tout l'historique.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth()) {
                Text(if (refreshing) "Mise à jour…" else "Mettre à jour l'historique")
            }
        }
    }
}

@Composable
private fun Cell(
    text: String,
    width: Dp,
    bold: Boolean = false,
    background: Color = Color.Transparent,
    color: Color = Color.Unspecified,
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(1.dp)
            .background(background)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = color,
            maxLines = 1,
        )
    }
}
