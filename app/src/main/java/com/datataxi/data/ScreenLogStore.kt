package com.datataxi.data

import android.content.Context
import com.datataxi.domain.ScreenRecord
import com.datataxi.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Résumé affiché à l'écran : combien d'écrans ont été enregistrés et de quels types. */
data class ScreenLogSummary(
    val total: Int = 0,
    val perScreen: Map<String, Int> = emptyMap(),
    val full: Boolean = false,
)

/**
 * Journal de TOUS les écrans de l'application cible (pas seulement Favoris), sans doublons.
 * Un écran n'est enregistré que si son contenu a changé. Fichier privé sur la tablette,
 * une ligne JSON par écran. Rien n'est envoyé ailleurs.
 */
object ScreenLogStore {

    private val _summary = MutableStateFlow(ScreenLogSummary())
    val summary: StateFlow<ScreenLogSummary> = _summary.asStateFlow()

    private val seen = HashSet<String>()
    private val perScreen = LinkedHashMap<String, Int>()
    private var total = 0

    @Volatile
    private var file: File? = null

    @Volatile
    private var full = false

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "screens.jsonl")
        file = f
        if (!f.exists()) return
        try {
            f.forEachLine { line ->
                val record = parse(line) ?: return@forEachLine
                seen.add(record.fingerprint)
                perScreen[record.screenId] = (perScreen[record.screenId] ?: 0) + 1
                total++
            }
        } catch (_: Exception) {
            // Fichier illisible : on repart avec ce qui a pu être lu plutôt que de planter.
        }
        full = f.length() >= Constants.SCREEN_LOG_MAX_BYTES
        publish()
    }

    /** Enregistre l'écran s'il est nouveau. Retourne true si une ligne a été ajoutée. */
    @Synchronized
    fun record(screenId: String, lines: List<String>, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(Constants.SCREEN_LOG_MAX_LINES)
        if (cleaned.isEmpty()) return false
        val record = ScreenRecord(nowMillis, screenId, cleaned)
        if (!seen.add(record.fingerprint)) return false

        val f = file ?: return false
        if (f.length() >= Constants.SCREEN_LOG_MAX_BYTES) {
            // Limite atteinte : on le signale à l'écran au lieu de continuer en silence.
            seen.remove(record.fingerprint)
            if (!full) {
                full = true
                publish()
            }
            return false
        }
        try {
            f.appendText(toJson(record) + "\n")
        } catch (_: Exception) {
            seen.remove(record.fingerprint)
            return false
        }
        perScreen[screenId] = (perScreen[screenId] ?: 0) + 1
        total++
        publish()
        return true
    }

    @Synchronized
    fun clear() {
        seen.clear()
        perScreen.clear()
        total = 0
        full = false
        try {
            file?.delete()
        } catch (_: Exception) {
        }
        publish()
    }

    /** Texte lisible, du plus ancien au plus récent, pour export ou partage. */
    @Synchronized
    fun exportText(): String = buildString {
        val f = file
        appendLine("DataTaxi - journal des écrans")
        appendLine("Écrans enregistrés : $total")
        appendLine()
        if (f == null || !f.exists()) return@buildString
        val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CANADA)
        try {
            f.forEachLine { line ->
                val r = parse(line) ?: return@forEachLine
                appendLine("=== ${stampFormat.format(Date(r.capturedAtMillis))}  [${r.screenId}] ===")
                r.lines.forEach { appendLine(it) }
                appendLine()
            }
        } catch (_: Exception) {
        }
    }

    /** Fichier brut (une ligne JSON par écran), pour traitement automatique. */
    @Synchronized
    fun exportJsonl(): String = try {
        file?.takeIf { it.exists() }?.readText().orEmpty()
    } catch (_: Exception) {
        ""
    }

    private fun publish() {
        _summary.value = ScreenLogSummary(total, LinkedHashMap(perScreen), full)
    }

    private fun toJson(r: ScreenRecord): String =
        JSONObject()
            .put("t", r.capturedAtMillis)
            .put("screen", r.screenId)
            .put("lines", JSONArray(r.lines))
            .toString()

    private fun parse(line: String): ScreenRecord? = try {
        val o = JSONObject(line)
        val arr = o.getJSONArray("lines")
        ScreenRecord(
            capturedAtMillis = o.getLong("t"),
            screenId = o.getString("screen"),
            lines = List(arr.length()) { arr.getString(it) },
        )
    } catch (_: Exception) {
        null
    }
}
