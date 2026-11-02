package com.datataxi.data

import android.content.Context
import com.datataxi.domain.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Date
import java.util.Locale

/**
 * Historique complet des appels (tout le Google Sheet, archives comprises), reçu de n8n.
 * Sans lui, les récurrences ne portent que sur les quelques jours lus par cette tablette.
 *
 * Adresse : celle de l'envoi, avec « datataxi-historique » comme dernier segment
 * (https://…/webhook/datataxi → https://…/webhook/datataxi-historique), même jeton.
 * Gardé dans un fichier privé : les récurrences restent disponibles sans réseau.
 */
object HistoryStore {

    private const val PREFS = "datataxi_history"
    private const val KEY_LAST_OK = "last_ok_millis"
    private const val KEY_STATUS = "status"
    private const val REFRESH_AFTER_MS = 6 * 3600 * 1000L

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    @Volatile
    private var file: File? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "historique.json")
        file = f
        if (f.exists()) runCatching { _calls.value = parse(f.readText()) }
        _status.value = statusLabel(context)
    }

    fun historyUrl(syncUrl: String): String? {
        val u = syncUrl.trim().trimEnd('/')
        if (!u.startsWith("https://") || !u.contains('/')) return null
        return u.substringBeforeLast('/') + "/datataxi-historique"
    }

    fun isStale(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_LAST_OK, 0L) > REFRESH_AFTER_MS

    /** Télécharge l'historique. À appeler hors du fil principal. */
    fun refresh(context: Context): Boolean {
        val ctx = context.applicationContext
        init(ctx)
        val address = historyUrl(SyncManager.url(ctx))
        if (address == null) {
            setStatus(ctx, "Historique complet non reçu : adresse d'envoi n8n absente (onglet Diagnostic).")
            return false
        }
        return try {
            val conn = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val text = try {
                val body = JSONObject().put("token", SyncManager.token(ctx)).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    setStatus(
                        ctx,
                        if (code == 404) "Historique complet non reçu : le workflow n8n « DataTaxi - Historique pour l'app » n'est pas encore installé."
                        else "Historique complet non reçu : le serveur a répondu $code."
                    )
                    return false
                }
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val parsed = parse(text)
            file?.writeText(text)
            _calls.value = parsed
            prefs(ctx).edit().putLong(KEY_LAST_OK, System.currentTimeMillis()).apply()
            setStatus(ctx, null)
            true
        } catch (e: Exception) {
            setStatus(ctx, "Historique complet non reçu : erreur réseau (${e.javaClass.simpleName}). Nouvel essai plus tard.")
            false
        }
    }

    private fun setStatus(ctx: Context, error: String?) {
        prefs(ctx).edit().putString(KEY_STATUS, error.orEmpty()).apply()
        _status.value = statusLabel(ctx)
    }

    private fun statusLabel(ctx: Context): String {
        val p = prefs(ctx)
        val error = p.getString(KEY_STATUS, "").orEmpty()
        val t = p.getLong(KEY_LAST_OK, 0L)
        val stamp = if (t == 0L) null else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CANADA).format(Date(t))
        return when {
            error.isNotEmpty() && stamp != null -> "$error Dernière copie : $stamp."
            error.isNotEmpty() -> error
            stamp != null -> "Historique complet mis à jour le $stamp."
            else -> "Historique complet pas encore téléchargé."
        }
    }

    /** {"ok":true,"rows":[["2026-09-01","00:02:00","1","430"], ...]} */
    private fun parse(text: String): List<Call> {
        val rows = JSONObject(text).optJSONArray("rows") ?: return emptyList()
        val out = ArrayList<Call>(rows.length())
        for (i in 0 until rows.length()) {
            val r = rows.optJSONArray(i) ?: continue
            val date = runCatching { LocalDate.parse(r.optString(0)) }.getOrNull() ?: continue
            val time = runCatching { LocalTime.parse(r.optString(1)) }.getOrNull() ?: continue
            val zone = r.optString(2).trim()
            if (zone.isEmpty()) continue
            out.add(Call(date, time.toSecondOfDay(), zone, r.optString(3).trim()))
        }
        return out
    }
}
