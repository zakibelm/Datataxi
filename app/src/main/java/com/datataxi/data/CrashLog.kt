package com.datataxi.data

import android.content.Context
import android.os.Build
import com.datataxi.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rapport de plantage : si l'application s'arrête sur une erreur, l'erreur est écrite dans un
 * fichier privé. Au lancement suivant, l'application l'affiche (au lieu de se refermer) pour
 * qu'on puisse la copier et la transmettre. Rien n'est envoyé automatiquement.
 */
object CrashLog {

    private const val FILE = "dernier-plantage.txt"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CANADA).format(Date())
                File(app.filesDir, FILE).writeText(
                    "DataTaxi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Date : $stamp\nFil : ${thread.name}\n\n$trace"
                )
            } catch (_: Throwable) {
                // Impossible d'écrire : on laisse Android gérer l'arrêt.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        File(context.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.applicationContext.filesDir, FILE).delete()
    }
}
