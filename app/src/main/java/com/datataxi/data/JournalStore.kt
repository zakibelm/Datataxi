package com.datataxi.data

import android.content.Context
import com.datataxi.domain.EventDater
import com.datataxi.domain.JournalDedup
import com.datataxi.domain.JournalEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Journal cumulatif des événements lus dans Fraxion, sans doublons.
 * Un événement est reconnu par son texte, sans tenir compte de la date (voir [JournalDedup]),
 * et reçoit la date réelle de l'événement, pas celle de sa lecture (voir [EventDater]).
 * Sauvegardé dans un fichier privé de l'application sur la tablette (aucun envoi ailleurs).
 */
object JournalStore {

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val seen = JournalDedup()

    private const val MIGRATION_PREFS = "datataxi_journal"
    private const val KEY_MIGRATED = "dedup_text_v1"

    @Volatile
    private var file: File? = null

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "journal.tsv")
        file = f
        var loaded = ArrayList<JournalEntry>()
        if (f.exists()) {
            val exact = HashSet<String>()
            try {
                f.forEachLine { line ->
                    val i = line.indexOf('\t')
                    if (i > 0) {
                        val entry = JournalEntry(line.substring(0, i), line.substring(i + 1))
                        if (exact.add(entry.key)) loaded.add(entry)
                    }
                }
            } catch (_: Exception) {
                // Fichier illisible : on repart d'un journal vide plutôt que de planter.
            }
        }
        loaded = ArrayList(migrateIfNeeded(context, f, loaded))
        seen.clear()
        loaded.forEach { e -> parse(e.date)?.let { seen.record(e.text, it) } }
        _entries.value = loaded
    }

    /**
     * Une seule fois après la mise à jour : supprime les copies créées par l'ancienne version
     * (même événement relu les jours suivants), en gardant la plus ancienne. Les compteurs
     * « déjà envoyé » et « déjà exporté » sont recalés pour ne rien renvoyer ni rien sauter.
     */
    private fun migrateIfNeeded(context: Context, f: File, loaded: List<JournalEntry>): List<JournalEntry> {
        val prefs = context.applicationContext.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return loaded
        if (loaded.isEmpty()) {
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
            return loaded
        }
        val (kept, keptIndex) = JournalDedup.keepOldest(loaded)
        try {
            f.copyTo(File(f.parentFile, "journal.avant-nettoyage.tsv"), overwrite = true)
            val tmp = File(f.parentFile, "journal.tsv.tmp")
            tmp.writeText(kept.joinToString("") { "${it.date}\t${it.text}\n" })
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
            return loaded // écriture impossible : on garde l'ancien journal, nouvel essai au prochain démarrage
        }
        SyncManager.remapSentCount(context, keptIndex)
        JournalExporter.remapExportedCount(context, keptIndex)
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        return kept
    }

    private fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()

    /**
     * Ajoute les lignes pas encore vues et retourne le nombre de nouvelles lignes.
     * @param texts lignes dans l'ordre de l'écran (la plus récente en premier).
     */
    @Synchronized
    fun addAll(now: LocalDateTime, texts: List<String>): Int {
        val today = now.toLocalDate()
        val dates = EventDater.date(texts, now) { seen.knownDate(it, today) }
        val added = ArrayList<JournalEntry>()
        texts.forEachIndexed { i, text ->
            val date = dates[i]
            if (!seen.isSeen(text, date)) {
                seen.record(text, date)
                added.add(JournalEntry(date.toString(), text))
            }
        }
        if (added.isEmpty()) return 0
        _entries.value = _entries.value + added
        try {
            file?.appendText(added.joinToString("") { "${it.date}\t${it.text}\n" })
        } catch (_: Exception) {
            // L'écriture échoue : le journal reste en mémoire pour cette session.
        }
        return added.size
    }

    @Synchronized
    fun clear() {
        seen.clear()
        _entries.value = emptyList()
        try {
            file?.delete()
        } catch (_: Exception) {
        }
    }

    /** Du plus récent au plus ancien. */
    fun sortedNewestFirst(): List<JournalEntry> =
        _entries.value.sortedWith(
            compareByDescending<JournalEntry> { it.date }.thenByDescending { it.time }
        )

    /** Texte partageable : une ligne par événement, du plus ancien au plus récent. */
    fun exportText(): String = buildString {
        val sorted = _entries.value.sortedWith(
            compareBy<JournalEntry> { it.date }.thenBy { it.time }
        )
        appendLine("DataTaxi - journal Fraxion")
        appendLine("Lignes : ${sorted.size}")
        appendLine()
        sorted.forEach { appendLine("${it.date} ${it.text}") }
    }
}
