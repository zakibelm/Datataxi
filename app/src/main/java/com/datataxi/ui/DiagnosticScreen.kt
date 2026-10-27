package com.datataxi.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datataxi.data.AlertSettings
import com.datataxi.data.CaptureStore
import com.datataxi.data.JournalExporter
import com.datataxi.data.JournalStore
import com.datataxi.data.ScreenLogStore
import com.datataxi.data.SyncManager
import com.datataxi.domain.BackfillPhase
import com.datataxi.domain.BackfillState
import com.datataxi.service.AlertPlayer
import com.datataxi.util.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingExport { CSV_ALL, CSV_NEW, JOURNAL }

@Composable
fun DiagnosticScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val connected by CaptureStore.serviceConnected.collectAsState()
    val targets by CaptureStore.targetPackages.collectAsState()
    val recent by CaptureStore.recentPackages.collectAsState()
    val snapshot by CaptureStore.snapshot.collectAsState()
    val backfill by CaptureStore.backfill.collectAsState()
    val journal by JournalStore.entries.collectAsState()
    val screenLog by ScreenLogStore.summary.collectAsState()
    val lastAlert by AlertSettings.lastAlert.collectAsState()
    val alertStatus by AlertPlayer.status.collectAsState()
    var alertEnabled by remember { mutableStateOf(AlertSettings.enabled(context)) }
    var alertPhrases by remember { mutableStateOf(AlertSettings.phrasesRaw(context)) }
    var alertMessage by remember { mutableStateOf(AlertSettings.message(context)) }

    var confirmClear by remember { mutableStateOf(false) }
    var confirmClearScreens by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var syncUrl by remember { mutableStateOf(SyncManager.url(context)) }
    var syncToken by remember { mutableStateOf(SyncManager.token(context)) }
    var syncEnabled by remember { mutableStateOf(SyncManager.enabled(context)) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf(SyncManager.statusLabel(context)) }
    var pendingExport by remember { mutableStateOf(PendingExport.CSV_ALL) }
    var exportedCount by remember { mutableStateOf(JournalExporter.exportedCount(context)) }
    var lastExportLabel by remember { mutableStateOf(JournalExporter.lastExportLabel(context)) }

    fun finishExport(uri: android.net.Uri?, content: String, markTotal: Int?) {
        if (uri == null) return
        if (JournalExporter.write(context, uri, content)) {
            if (markTotal != null) {
                JournalExporter.markExported(context, markTotal)
                exportedCount = markTotal
            }
            lastExportLabel = JournalExporter.lastExportLabel(context)
            exportMessage = "Fichier enregistré."
        } else {
            exportMessage = "Échec de l'enregistrement. Réessaie ou utilise « Partager »."
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val all = JournalStore.entries.value
        val part = if (pendingExport == PendingExport.CSV_NEW)
            all.drop(exportedCount.coerceAtMost(all.size)) else all
        finishExport(uri, JournalExporter.buildCsv(part), all.size)
    }
    val txtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        finishExport(uri, JournalExporter.buildJournalText(JournalStore.entries.value), null)
    }

    val screensLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        finishExport(uri, ScreenLogStore.exportText(), null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("DataTaxi", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Lecture des écrans de tes applications de répartition. Fais l'activation et les essais à l'arrêt, jamais en roulant.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // 1. Service d'accessibilité
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (connected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Service d'accessibilité", style = MaterialTheme.typography.titleMedium)
                Text(if (connected) "ACTIF" else "INACTIF")
                if (!connected) {
                    Text(
                        "Si l'interrupteur est grisé : Paramètres > Applications > DataTaxi > menu ⋮ > « Autoriser les paramètres restreints », puis réessaie.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ouvrir les paramètres d'accessibilité") }
            }
        }

        // 2. Applications cibles
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Applications cibles", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (targets.isEmpty()) "Aucune application choisie."
                    else "Applications lues : ${targets.sorted().joinToString(", ")}",
                )
                Text(
                    "Ouvre chaque application à suivre (Fraxion, Padam Driver...), reviens ici, puis touche-la dans la liste pour l'ajouter. Touche-la de nouveau pour la retirer. Seul son nom est retenu avant que tu la choisisses.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (recent.isEmpty()) {
                    Text("Aucune application détectée pour l'instant.")
                }
                recent.forEach { pkg ->
                    if (pkg in targets) {
                        Button(
                            onClick = { CaptureStore.toggleTarget(context, pkg) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("✓ $pkg") }
                    } else {
                        OutlinedButton(
                            onClick = { CaptureStore.toggleTarget(context, pkg) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(pkg) }
                    }
                }
                // Une cible déjà choisie qui n'est plus dans la liste récente doit rester retirable.
                targets.filter { it !in recent }.forEach { pkg ->
                    Button(
                        onClick = { CaptureStore.toggleTarget(context, pkg) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("✓ $pkg") }
                }
                if (targets.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { CaptureStore.clearTargets(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Retirer toutes les cibles") }
                }
            }
        }

        // 3. Journal cumulatif
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Journal des événements", style = MaterialTheme.typography.titleMedium)
                Text("${journal.size} lignes enregistrées, sans doublons, sur cette tablette.")
                Text(backfillStatus(backfill), style = MaterialTheme.typography.bodySmall)

                if (journal.isNotEmpty()) {
                    val preview = remember(journal) {
                        JournalStore.sortedNewestFirst().take(Constants.JOURNAL_PREVIEW_LINES)
                    }
                    Text("Dernières lignes :", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(
                            preview.joinToString("\n") { "${it.date} ${it.text}" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Text(
                        "Aucune ligne d'événement reconnue pour l'instant. Ouvre Favoris : les lignes au format « 12:34:56 ... » seront ajoutées ici.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text("Export & Partage", style = MaterialTheme.typography.titleSmall)
                val newCount = journal.size - exportedCount.coerceAtMost(journal.size)
                Button(
                    onClick = {
                        pendingExport = PendingExport.CSV_ALL
                        csvLauncher.launch("DataTaxi_historique_${JournalExporter.fileStamp()}.csv")
                    },
                    enabled = journal.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Exporter l'historique CSV") }
                OutlinedButton(
                    onClick = {
                        pendingExport = PendingExport.CSV_NEW
                        csvLauncher.launch("DataTaxi_nouveautes_${JournalExporter.fileStamp()}.csv")
                    },
                    enabled = newCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Exporter seulement les nouveautés ($newCount)") }
                OutlinedButton(
                    onClick = {
                        pendingExport = PendingExport.JOURNAL
                        txtLauncher.launch("DataTaxi_journal_${JournalExporter.fileStamp()}.txt")
                    },
                    enabled = journal.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Exporter les anomalies / journal") }
                Text(lastExportLabel, style = MaterialTheme.typography.bodySmall)
                exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

                when (backfill.phase) {
                    BackfillPhase.IDLE, BackfillPhase.DONE -> Button(
                        onClick = {
                            CaptureStore.setBackfill(
                                BackfillState(
                                    BackfillPhase.ARMED,
                                    message = "Ouvre maintenant Favoris, à l'arrêt : le défilement démarre tout seul.",
                                )
                            )
                        },
                        enabled = connected && targets.any { it.contains(Constants.JOURNAL_PACKAGE_HINT, ignoreCase = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Récupérer l'historique de Favoris") }

                    BackfillPhase.ARMED, BackfillPhase.RUNNING -> OutlinedButton(
                        onClick = { CaptureStore.setBackfill(BackfillState()) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Arrêter") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(JournalStore.exportText())) },
                        enabled = journal.isNotEmpty(),
                    ) { Text("Copier tout") }
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Journal DataTaxi")
                                putExtra(Intent.EXTRA_TEXT, JournalStore.exportText())
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Partager le journal")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        enabled = journal.isNotEmpty(),
                    ) { Text("Partager") }
                    OutlinedButton(
                        onClick = {
                            if (confirmClear) {
                                JournalStore.clear()
                                JournalExporter.resetMarkers(context)
                                SyncManager.resetSent(context)
                                syncStatus = SyncManager.statusLabel(context)
                                exportedCount = 0
                                lastExportLabel = JournalExporter.lastExportLabel(context)
                                confirmClear = false
                            } else {
                                confirmClear = true
                            }
                        },
                        enabled = journal.isNotEmpty() || confirmClear,
                    ) { Text(if (confirmClear) "Confirmer ?" else "Vider") }
                }
            }
        }

        // Alerte « c'est l'heure »
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Alerte « c'est l'heure »", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Quand un de ces mots apparaît dans une application suivie, la tablette joue un signal sonore puis dit la phrase à voix haute (volume alarme).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Switch(
                        checked = alertEnabled,
                        onCheckedChange = {
                            alertEnabled = it
                            AlertSettings.setEnabled(context, it)
                        },
                    )
                    Text(if (alertEnabled) "Alerte activée" else "Alerte désactivée")
                }
                OutlinedTextField(
                    value = alertPhrases,
                    onValueChange = { alertPhrases = it },
                    label = { Text("Mots déclencheurs (séparés par des virgules)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = alertMessage,
                    onValueChange = { alertMessage = it },
                    label = { Text("Phrase dite à voix haute") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        AlertSettings.save(context, alertPhrases, alertMessage)
                        alertPhrases = AlertSettings.phrasesRaw(context)
                        alertMessage = AlertSettings.message(context)
                    }) { Text("Enregistrer") }
                    OutlinedButton(onClick = {
                        AlertPlayer.play(context, alertMessage.ifBlank { AlertSettings.DEFAULT_MESSAGE })
                    }) { Text("Tester le son") }
                }
                Text(lastAlert, style = MaterialTheme.typography.bodySmall)
                if (alertStatus.isNotEmpty()) {
                    Text(
                        alertStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Journal de tous les écrans
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Journal de tous les écrans", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Chaque écran de l'application cible que tu ouvres est enregistré une fois, quand son contenu change. Navigue dans tous les écrans, à l'arrêt, pour les capter.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("${screenLog.total} écrans enregistrés sur cette tablette.")
                if (screenLog.full) {
                    Text(
                        "Limite de stockage atteinte : exporte puis vide le journal pour reprendre l'enregistrement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                screenLog.perScreen.entries.sortedByDescending { it.value }.take(12).forEach { (id, n) ->
                    Text("$n × $id", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Button(
                    onClick = { screensLauncher.launch("DataTaxi_ecrans_${JournalExporter.fileStamp()}.txt") },
                    enabled = screenLog.total > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Exporter les écrans") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Journal des écrans DataTaxi")
                                putExtra(Intent.EXTRA_TEXT, ScreenLogStore.exportText())
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Partager les écrans")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        enabled = screenLog.total > 0,
                    ) { Text("Partager") }
                    OutlinedButton(
                        onClick = {
                            if (confirmClearScreens) {
                                ScreenLogStore.clear()
                                confirmClearScreens = false
                            } else {
                                confirmClearScreens = true
                            }
                        },
                        enabled = screenLog.total > 0 || confirmClearScreens,
                    ) { Text(if (confirmClearScreens) "Confirmer ?" else "Vider") }
                }
            }
        }

        // Envoi automatique vers n8n
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Envoi automatique (n8n)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Les nouvelles lignes sont envoyées au webhook toutes les heures (quand il y a du réseau). Les lignes déjà envoyées ne sont jamais renvoyées.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = syncUrl,
                    onValueChange = { syncUrl = it },
                    label = { Text("Adresse du webhook (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = syncToken,
                    onValueChange = { syncToken = it },
                    label = { Text("Mot de passe secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (syncEnabled) "Envoi horaire : ACTIVÉ" else "Envoi horaire : désactivé",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            SyncManager.save(context, syncUrl, syncToken)
                            SyncManager.enable(context)
                            syncEnabled = true
                            syncStatus = "Réglages enregistrés, envoi horaire activé."
                        },
                        enabled = syncUrl.startsWith("https://"),
                    ) { Text("Enregistrer et activer") }
                    OutlinedButton(
                        onClick = {
                            SyncManager.disable(context)
                            syncEnabled = false
                        },
                        enabled = syncEnabled,
                    ) { Text("Désactiver") }
                }
                OutlinedButton(
                    onClick = {
                        SyncManager.save(context, syncUrl, syncToken)
                        syncBusy = true
                        syncStatus = "Envoi en cours..."
                        Thread {
                            val r = SyncManager.sendNow(context)
                            syncStatus = r.message
                            syncBusy = false
                        }.start()
                    },
                    enabled = syncUrl.startsWith("https://") && !syncBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Envoyer maintenant") }
                Text(syncStatus, style = MaterialTheme.typography.bodySmall)
            }
        }

        // 4. Dernière capture
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("4. Dernière capture", style = MaterialTheme.typography.titleMedium)
                val snap = snapshot
                if (snap == null) {
                    Text("Aucune capture. Ouvre l'application cible, laisse l'écran des assignations visible quelques secondes, puis reviens ici.")
                } else {
                    val stamp = SimpleDateFormat("HH:mm:ss", Locale.CANADA)
                        .format(Date(snap.capturedAtMillis))
                    Text(
                        if (snap.lines.isEmpty()) "FAIL probable : écran lu à $stamp mais aucun texte trouvé."
                        else "PASS : ${snap.lines.size} éléments lus à $stamp.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            clipboard.setText(AnnotatedString(snap.toPlainText()))
                        }) { Text("Copier") }
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Capture DataTaxi")
                                putExtra(Intent.EXTRA_TEXT, snap.toPlainText())
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Partager la capture")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Partager") }
                    }
                    SelectionContainer {
                        Text(
                            snap.toPlainText(Constants.MAX_LINES_DISPLAYED),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun backfillStatus(state: BackfillState): String = when (state.phase) {
    BackfillPhase.IDLE ->
        "Les nouvelles lignes s'ajoutent toutes seules tant que Favoris est ouvert au premier plan. Pour récupérer aussi les plus anciennes, appuie sur le bouton ci-dessous puis ouvre Favoris."
    else -> state.message
}
