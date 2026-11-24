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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class SyncResult(val ok: Boolean, val message: String)

/**
 * Envoi des nouvelles lignes vers un webhook (n8n). L'application ne connaît aucun service Google :
 * le destinataire est interchangeable, seule l'adresse https change.
 */
object SyncManager {

    private const val PREFS = "datataxi_sync"
    private const val WORK_NAME = "datataxi_hourly_sync"
    private const val BATCH = 300

    /** Envoi quasi direct : délai de regroupement après une capture, puis nouvel essai en cas d'échec. */
    private const val PUSH_DELAY_SECONDS = 15L
    private const val RETRY_DELAY_SECONDS = 60L

    /** Un seul envoi à la fois (l'envoi direct et le worker de secours ne doivent pas se croiser). */
    private val sendLock = Any()
    private val pushPending = AtomicBoolean(false)
    private val pusher: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "datataxi-push").apply { isDaemon = true }
    }

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

    /** Recale le compteur après le nettoyage du journal (voir JournalStore). */
    fun remapSentCount(c: Context, keptIndex: List<Int>) {
        val old = sentCount(c)
        prefs(c).edit().putInt("sent_count", keptIndex.count { it < old }).apply()
    }

    fun resetSent(c: Context) {
        prefs(c).edit().putInt("sent_count", 0).apply()
    }

    fun enable(c: Context) {
        prefs(c).edit().putBoolean("enabled", true).apply()
        // Filet de sécurité : l'envoi direct (pushSoon) fait le gros du travail, ce cycle rattrape les ratés.
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c.applicationContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable(c: Context) {
        prefs(c).edit().putBoolean("enabled", false).apply()
        WorkManager.getInstance(c.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Demande un envoi dans quelques secondes (regroupe les captures rapprochées).
     * Sans effet si l'envoi n'est pas activé. Peut être appelé depuis le fil principal.
     */
    fun pushSoon(c: Context, delaySeconds: Long = PUSH_DELAY_SECONDS) {
        val ctx = c.applicationContext
        if (!enabled(ctx)) return
        if (!pushPending.compareAndSet(false, true)) return
        pusher.schedule(Runnable {
            pushPending.set(false)
            val result = sendNow(ctx)
            if (!result.ok) pushSoon(ctx, RETRY_DELAY_SECONDS)
        }, delaySeconds, TimeUnit.SECONDS)
    }

    /** Envoie les lignes pas encore envoyées. À appeler hors du fil principal. */
    fun sendNow(c: Context): SyncResult = synchronized(sendLock) {
        val ctx = c.applicationContext
        JournalStore.init(ctx)
        val result = try {
            doSend(ctx)
        } catch (e: Exception) {
            SyncResult(false, "Erreur réseau (${e.javaClass.simpleName}). Nouvel essai dans une minute.")
        }
        prefs(ctx).edit()
            .putLong("last_try", System.currentTimeMillis())
            .putString("last_status", result.message)
            .apply()
        result
    }

    private fun doSend(ctx: Context): SyncResult {
        val address = url(ctx)
        if (!address.startsWith("https://")) {
            return SyncResult(false, "Adresse du webhook absente ou non https.")
        }
        val events = sendEvents(ctx, address)
        // La couverture part après les appels, et son échec ne bloque jamais l'envoi des appels.
        val coverage = sendCoverage(ctx, address)
        return if (coverage.isEmpty()) events else SyncResult(events.ok, events.message + " " + coverage)
    }

    private fun post(address: String, ctx: Context, rows: JSONArray): Int {
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
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun sendEvents(ctx: Context, address: String): SyncResult {
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
            val code = post(address, ctx, rows)
            if (code !in 200..299) {
                return SyncResult(false, "Le serveur a répondu $code. $total lignes envoyées avant l'erreur.")
            }
            sent += chunk.size
            total += chunk.size
            prefs(ctx).edit().putInt("sent_count", sent).apply()
        }
        return SyncResult(true, "$total nouvelles lignes envoyées.")
    }

    /** Envoie les cases de couverture en attente ; renvoie un court message, vide s'il n'y avait rien. */
    private fun sendCoverage(ctx: Context, address: String): String {
        val pending = CoverageStore.pending(ctx)
        if (pending.isEmpty()) return ""
        val p = prefs(ctx)
        val retryAfter = p.getLong("coverage_retry_after", 0L)
        if (System.currentTimeMillis() < retryAfter) return ""
        return try {
            var sentBins = 0
            pending.chunked(BATCH).forEach { chunk ->
                val rows = JSONArray()
                chunk.forEach { bin ->
                    val o = JSONObject()
                    JournalExporter.coverageRecord(bin).forEach { (k, v) -> o.put(k, v) }
                    rows.put(o)
                }
                val code = post(address, ctx, rows)
                if (code !in 200..299) {
                    p.edit().putLong("coverage_retry_after", System.currentTimeMillis() + 10 * 60_000L).apply()
                    return "Couverture : le serveur a répondu $code, nouvel essai dans 10 min."
                }
                CoverageStore.acknowledge(ctx, chunk)
                sentBins += chunk.size
            }
            "Couverture : $sentBins cases envoyées."
        } catch (e: Exception) {
            p.edit().putLong("coverage_retry_after", System.currentTimeMillis() + 10 * 60_000L).apply()
            "Couverture : erreur réseau, nouvel essai dans 10 min."
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        SyncManager.sendNow(applicationContext)
        if (HistoryStore.isStale(applicationContext)) HistoryStore.refresh(applicationContext)
        return Result.success() // l'échec est noté ; le prochain cycle (15 min) ou la prochaine capture réessaie
    }
}
