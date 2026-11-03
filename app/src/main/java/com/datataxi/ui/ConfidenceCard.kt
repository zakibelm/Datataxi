package com.datataxi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.datataxi.domain.DayReliability
import com.datataxi.domain.PeriodReliability
import com.datataxi.domain.Reliability
import kotlin.math.roundToInt

private val GREEN = Color(0xFF1B7F2A)
private val RED = Color(0xFFC62828)
private val BASELINE = Color(0xFF8A8A8A)

private fun pct(x: Double) = "${(x * 100).roundToInt()} %"

private fun points(x: Double): String {
    val v = (x * 1000).roundToInt() / 10.0
    return (if (v > 0) "+" else "") + "%.1f".format(v).replace('.', ',') + " pts"
}

/**
 * Niveau de confiance : part des zones prédites (récurrences) qui ont réellement eu un appel,
 * par jour, par semaine ou par mois, comparée au hasard (zones sans récurrence).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfidenceCard(days: List<DayReliability>, initialPeriod: Reliability.Period) {
    var period by remember { mutableStateOf(initialPeriod) }
    val all = remember(days, period) { Reliability.group(days, period) }
    // Assez de points pour voir l'évolution sans écraser le graphique.
    val shown = all.takeLast(if (period == Reliability.Period.DAY) 21 else 12)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Niveau de confiance", style = MaterialTheme.typography.titleMedium)
            Text(
                "Part des zones prédites qui ont vraiment eu un appel. Chaque journée est rejouée avec les seules journées d'avant.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Reliability.Period.entries.forEach { p ->
                    FilterChip(selected = p == period, onClick = { period = p }, label = { Text("Par ${p.label.lowercase()}") })
                }
            }

            if (shown.isEmpty()) {
                Text("Pas encore assez d'historique pour mesurer la confiance.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Summary(shown)
                Chart(shown)
                LegendRow()
                PeriodTable(shown.takeLast(6))
            }
        }
    }
}

@Composable
private fun Summary(shown: List<PeriodReliability>) {
    val last = shown.last()
    val prev = shown.getOrNull(shown.size - 2)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
        Column {
            Text(pct(last.rate), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(last.label, style = MaterialTheme.typography.bodySmall)
        }
        if (prev != null) {
            val d = last.rate - prev.rate
            Column {
                Text(points(d), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (d >= 0) GREEN else RED)
                Text("par rapport à ${prev.label}", style = MaterialTheme.typography.bodySmall)
            }
        }
        last.baseline?.let { b ->
            Column {
                Text(if (b > 0) "%.1f fois".format(last.rate / b).replace('.', ',') else "–", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("mieux que le hasard (${pct(b)})", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Chart(shown: List<PeriodReliability>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth()) {
        // Axe vertical : 0 à 100 %.
        Column(Modifier.width(40.dp).height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("100 %", "75 %", "50 %", "25 %", "0 %").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
        Column(Modifier.weight(1f)) {
            Canvas(Modifier.fillMaxWidth().height(180.dp).padding(vertical = 6.dp, horizontal = 8.dp)) {
                val w = size.width
                val h = size.height
                for (i in 0..4) {
                    val y = h * i / 4f
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                fun x(i: Int) = if (shown.size == 1) w / 2 else w * i / (shown.size - 1).toFloat()
                fun y(v: Double) = h * (1f - v.toFloat().coerceIn(0f, 1f))

                // Hasard : ligne grise en pointillé.
                val base = Path()
                var started = false
                shown.forEachIndexed { i, p ->
                    val b = p.baseline ?: return@forEachIndexed
                    if (!started) { base.moveTo(x(i), y(b)); started = true } else base.lineTo(x(i), y(b))
                }
                if (started) drawPath(base, BASELINE, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))

                // Réussite des prédictions.
                val path = Path()
                shown.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.rate)) else path.lineTo(x(i), y(p.rate)) }
                drawPath(path, line, style = Stroke(width = 5f))
                shown.forEachIndexed { i, p -> drawCircle(line, radius = 7f, center = Offset(x(i), y(p.rate))) }
            }
            // Axe horizontal : première, milieu et dernière période.
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                val labels = listOfNotNull(shown.first().label, shown.getOrNull(shown.size / 2)?.label?.takeIf { shown.size > 2 }, shown.last().label.takeIf { shown.size > 1 })
                labels.forEachIndexed { i, l ->
                    Text(
                        l,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = when (i) { 0 -> TextAlign.Start; labels.lastIndex -> TextAlign.End; else -> TextAlign.Center },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendRow() {
    val line = MaterialTheme.colorScheme.primary
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(18.dp, 4.dp)) { drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 6f) }
            Text("Réussite des prédictions", style = MaterialTheme.typography.labelSmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(18.dp, 4.dp)) {
                drawLine(BASELINE, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f)))
            }
            Text("Hasard (zones sans récurrence)", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PeriodTable(rows: List<PeriodReliability>) {
    Column {
        Row {
            HeaderCell("Période", 1.4f)
            HeaderCell("Prédictions", 1f)
            HeaderCell("Réussite", 1f)
            HeaderCell("Écart", 1f)
        }
        rows.forEachIndexed { i, p ->
            val prev = rows.getOrNull(i - 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BodyCell(p.label, 1.4f)
                BodyCell("${p.predicted}", 1f)
                BodyCell(pct(p.rate), 1f, bold = true)
                if (prev == null) BodyCell("", 1f)
                else {
                    val d = p.rate - prev.rate
                    BodyCell(points(d), 1f, color = if (d >= 0) GREEN else RED, bold = true)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Box(Modifier.weight(weight).padding(vertical = 4.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyCell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
) {
    Box(Modifier.weight(weight).padding(vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}
