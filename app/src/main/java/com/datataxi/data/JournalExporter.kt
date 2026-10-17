package com.datataxi.data

import android.content.Context
import android.net.Uri
import com.datataxi.domain.EventDecoder
import com.datataxi.domain.JournalEntry
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export fidèle pour Zone Predict : aucune prédiction, aucune donnée inventée.
 * Les colonnes que l'app ne sait pas encore décoder restent vides (jamais de zéro factice).
 */
object JournalExporter {

    private const val PREFS = "datataxi_export"
    private const val KEY_EXPORTED_COUNT = "exported_count"
    private const val KEY_LAST_EXPORT = "last_export_millis"

    const val HEADER =
        "trip_id,trip_date,trip_time,observed_at,departure_zone,departure_address," +
            "destination_zone,destination_address,service_type,vehicle_id,current_status," +
            "confidence_score,raw_text"

    private fun sha(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(12)

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun ordered(entries: List<JournalEntry>) =
        entries.sortedWith(compareBy<JournalEntry> { it.date }.thenBy { it.time })

    /** CSV UTF-8 (BOM pour Excel/Sheets). trip_id = empreinte stable de date+texte. */
    fun buildCsv(entries: List<JournalEntry>): String = buildString {
        append('﻿').append(HEADER).append('\n')
        ordered(entries).forEach { e ->
            val time = e.text.take(8)
            append(sha(e.key)).append(',')
            append(e.date).append(',')
            append(time).append(',')
            append(e.date).append('T').append(time).append(',')
            val d = EventDecoder.decode(e.text)
            append(d.departureZone.orEmpty()).append(',')
            append(',')                                   // departure_address : absente de Fraxion
            append(d.destinationZone.orEmpty()).append(',')
            append(',')                                   // destination_address : absente de Fraxion
            append(q(d.serviceType.orEmpty())).append(',')
            append(d.vehicleId.orEmpty()).append(',')
            append(d.status.orEmpty()).append(',')
            append(d.confidence?.toString().orEmpty()).append(',')
            append(q(e.text)).append('\n')
        }
    }

    /** Journal technique lisible (lignes brutes + statistiques de couverture). */
    fun buildJournalText(entries: List<JournalEntry>): String = buildString {
        val sorted = ordered(entries)
        appendLine("DataTaxi - journal / anomalies")
        appendLine("Lignes : ${sorted.size}")
        val perDay = sorted.groupBy { it.date }
        perDay.forEach { (d, list) ->
            appendLine("$d : ${list.size} lignes, de ${list.first().time} à ${list.last().time}")
        }
        appendLine("Anomalies détectées : aucune détection automatique pour l'instant.")
        appendLine()
        sorted.forEach { appendLine("${it.date} ${it.text}") }
    }

    fun write(context: Context, uri: Uri, content: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(content.toByteArray(Charsets.UTF_8))
        } != null
    } catch (_: Exception) {
        false
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Les lignes s'ajoutent toujours à la fin : un simple compteur suffit pour « nouveautés ». */
    fun exportedCount(context: Context): Int = prefs(context).getInt(KEY_EXPORTED_COUNT, 0)

    fun lastExportMillis(context: Context): Long = prefs(context).getLong(KEY_LAST_EXPORT, 0L)

    fun markExported(context: Context, total: Int) {
        prefs(context).edit()
            .putInt(KEY_EXPORTED_COUNT, total)
            .putLong(KEY_LAST_EXPORT, System.currentTimeMillis())
            .apply()
    }

    fun resetMarkers(context: Context) {
        prefs(context).edit().remove(KEY_EXPORTED_COUNT).remove(KEY_LAST_EXPORT).apply()
    }

    fun lastExportLabel(context: Context): String {
        val t = lastExportMillis(context)
        return if (t == 0L) "Aucun export réussi pour l'instant."
        else "Dernier export réussi : " +
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CANADA).format(Date(t))
    }

    fun fileStamp(): String = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.CANADA).format(Date())
}
