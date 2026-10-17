package com.datataxi.data

import android.content.Context
import com.datataxi.domain.JournalEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal cumulatif des événements lus dans Fraxion, sans doublons.
 * Sauvegardé dans un fichier privé de l'application sur la tablette (aucun envoi ailleurs).
 */
object JournalStore {

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val seen = HashSet<String>()

    @Volatile
    private var file: File? = null

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "journal.tsv")
        file = f
        if (!f.exists()) return
        val loaded = ArrayList<JournalEntry>()
        try {
            f.forEachLine { line ->
                val i = line.indexOf('\t')
                if (i > 0) {
                    val entry = JournalEntry(line.substring(0, i), line.substring(i + 1))
                    if (seen.add(entry.key)) loaded.add(entry)
                }
            }
        } catch (_: Exception) {
            // Fichier illisible : on repart d'un journal vide plutôt que de planter.
        }
        _entries.value = loaded
    }

    fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CANADA).format(Date())

    /** Ajoute les lignes pas encore vues et retourne le nombre de nouvelles lignes. */
    fun addAll(date: String, texts: List<String>): Int {
        val added = ArrayList<JournalEntry>()
        for (text in texts) {
            val entry = JournalEntry(date, text)
            if (seen.add(entry.key)) added.add(entry)
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
