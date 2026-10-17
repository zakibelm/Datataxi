package com.datataxi.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SyncResult(val ok: Boolean, val message: String)

/**
 * Envoi des nouvelles lignes vers un webhook (n8n). L'application ne connaît aucun service Google :
 * le destinataire est interchangeable, seule l'adresse https change.
 */
object SyncManager {

    private const val PREFS = "datataxi_sync"
    private const val WORK_NAME = "datataxi_hourly_sync"
    private const val BATCH = 300

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun url(c: Context): String = prefs(c).getString("url", "").orEmpty()
    fun token(c: Context): String = prefs(c).getString("token", "").orEmpty()
    fun enabled(c: Context): Boolean = prefs(c).getBoolean("enabled", false)
    fun sentCount(c: Context): Int = prefs(c).getInt("sent_count", 0)

    fun statusLabel(c: Context): String {
        val p = prefs(c)
        val t = p.getLong("last_try", 0L)
        if (t == 0L) return "Aucun envoi pour l'instant."
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CANADA).format(Date(t))
        return "Dernier essai $stamp : ${p.getString("last_status", "")}"
    }

    fun save(c: Context, url: String, token: String) {
        prefs(c).edit().putString("url", url.trim()).putString("token", token.trim()).apply()
    }

    fun resetSent(c: Context) {
        prefs(c).edit().putInt("sent_count", 0).apply()
    }

    fun enable(c: Context) {
        prefs(c).edit().putBoolean("enabled", true).apply()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable(c: Context) {
        prefs(c).edit().putBoolean("enabled", false).apply()
        WorkManager.getInstance(c.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    /** Envoie les lignes pas encore envoyées. À appeler hors du fil principal. */
    fun sendNow(c: Context): SyncResult {
        val ctx = c.applicationContext
        JournalStore.init(ctx)
        val result = try {
            doSend(ctx)
        } catch (e: Exception) {
            SyncResult(false, "Erreur réseau (${e.javaClass.simpleName}). Nouvel essai à la prochaine heure.")
        }
        prefs(ctx).edit()
            .putLong("last_try", System.currentTimeMillis())
            .putString("last_status", result.message)
            .apply()
        return result
    }

    private fun doSend(ctx: Context): SyncResult {
        val address = url(ctx)
        if (!address.startsWith("https://")) {
            return SyncResult(false, "Adresse du webhook absente ou non https.")
        }
        val all = JournalStore.entries.value
        var sent = sentCount(ctx).coerceAtMost(all.size)
        if (sent >= all.size) return SyncResult(true, "Rien de nouveau à envoyer.")

        var total = 0
        while (sent < all.size) {
            val chunk = all.subList(sent, minOf(sent + BATCH, all.size))
            val rows = JSONArray()
            chunk.forEach { e ->
                val o = JSONObject()
                JournalExporter.record(e).forEach { (k, v) -> o.put(k, v) }
                rows.put(o)
            }
            val body = JSONObject().put("token", token(ctx)).put("rows", rows).toString()

            val conn = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    return SyncResult(false, "Le serveur a répondu $code. $total lignes envoyées avant l'erreur.")
                }
            } finally {
                conn.disconnect()
            }
            sent += chunk.size
            total += chunk.size
            prefs(ctx).edit().putInt("sent_count", sent).apply()
        }
        return SyncResult(true, "$total nouvelles lignes envoyées.")
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        SyncManager.sendNow(applicationContext)
        return Result.success() // l'échec est noté ; le prochain cycle horaire réessaie
    }
}
