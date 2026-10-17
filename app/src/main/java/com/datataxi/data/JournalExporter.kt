package com.datataxi.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.datataxi.domain.JournalEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écrit le journal dans un fichier CSV visible dans « Mes fichiers > Téléchargements ». */
object JournalExporter {

    /** CSV : date, heure, événement. Du plus ancien au plus récent, UTF-8 avec BOM (accents OK dans Excel). */
    fun buildCsv(entries: List<JournalEntry>): String = buildString {
        append('﻿')
        append("date,heure,evenement\n")
        entries
            .sortedWith(compareBy<JournalEntry> { it.date }.thenBy { it.time })
            .forEach { e ->
                val time = e.text.take(8)
                val event = e.text.drop(8).trim().replace("\"", "\"\"")
                append(e.date).append(',').append(time).append(",\"").append(event).append("\"\n")
            }
    }

    /** Retourne le nom du fichier créé dans Téléchargements, ou null si l'écriture a échoué. */
    fun saveToDownloads(context: Context, entries: List<JournalEntry>): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.CANADA).format(Date())
        val name = "DataTaxi_journal_$stamp.csv"
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(buildCsv(entries).toByteArray(Charsets.UTF_8)) }
                ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            name
        } catch (_: Exception) {
            null
        }
    }
}
