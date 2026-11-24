package com.datataxi.data

import android.content.Context
import com.datataxi.domain.CoverageBin
import java.time.LocalDateTime

/** Cases de 5 minutes « écran de Fraxion lisible » en attente d'envoi (conservées si le réseau manque). */
object CoverageStore {
    private const val PREFS = "datataxi_coverage"
    private const val KEY = "pending"
    private const val MAX_PENDING = 2000 // environ une semaine de cases

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    /** Renvoie true si la case de 5 minutes vient d'être ajoutée (donc pas encore connue). */
    fun mark(c: Context, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val p = prefs(c)
        val set = p.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (!set.add(CoverageBin.start(now))) return false
        val kept = if (set.size > MAX_PENDING) set.sorted().takeLast(MAX_PENDING).toMutableSet() else set
        p.edit().putStringSet(KEY, kept).apply()
        return true
    }

    @Synchronized
    fun pending(c: Context): List<String> = prefs(c).getStringSet(KEY, emptySet()).orEmpty().sorted()

    @Synchronized
    fun acknowledge(c: Context, sent: Collection<String>) {
        val p = prefs(c)
        val left = p.getStringSet(KEY, emptySet()).orEmpty().toMutableSet().apply { removeAll(sent.toSet()) }
        p.edit().putStringSet(KEY, left).apply()
    }
}
