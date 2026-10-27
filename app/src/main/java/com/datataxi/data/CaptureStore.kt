package com.datataxi.data

import android.content.Context
import com.datataxi.domain.BackfillState
import com.datataxi.domain.CaptureSnapshot
import com.datataxi.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État partagé entre le service d'accessibilité et l'écran.
 * Tout reste en mémoire sur la tablette ; seule l'application cible choisie est mémorisée.
 */
object CaptureStore {

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    /** Applications dont le contenu est lu (Fraxion, Padam Driver, ...). Choisies par l'utilisateur. */
    private val _targetPackages = MutableStateFlow<Set<String>>(emptySet())
    val targetPackages: StateFlow<Set<String>> = _targetPackages.asStateFlow()

    private val _recentPackages = MutableStateFlow<List<String>>(emptyList())
    val recentPackages: StateFlow<List<String>> = _recentPackages.asStateFlow()

    private val _snapshot = MutableStateFlow<CaptureSnapshot?>(null)
    val snapshot: StateFlow<CaptureSnapshot?> = _snapshot.asStateFlow()

    private val _backfill = MutableStateFlow(BackfillState())
    val backfill: StateFlow<BackfillState> = _backfill.asStateFlow()

    @Volatile
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext
            .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(Constants.KEY_TARGET_PACKAGES, null)
        // Ancienne version : une seule cible. On la reprend pour ne rien faire refaire à l'utilisateur.
        val legacy = prefs.getString(Constants.KEY_TARGET_PACKAGE, null)
        _targetPackages.value = when {
            saved != null -> saved.toSet()
            legacy != null -> setOf(legacy)
            else -> emptySet()
        }
        loaded = true
    }

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        if (!connected) _backfill.value = BackfillState()
    }

    /** Ajoute l'application à la liste des cibles, ou l'en retire si elle y est déjà. */
    fun toggleTarget(context: Context, packageName: String) {
        val current = _targetPackages.value
        saveTargets(context, if (packageName in current) current - packageName else current + packageName)
    }

    fun clearTargets(context: Context) = saveTargets(context, emptySet())

    private fun saveTargets(context: Context, targets: Set<String>) {
        context.applicationContext
            .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(Constants.KEY_TARGET_PACKAGE)
            .apply {
                if (targets.isEmpty()) remove(Constants.KEY_TARGET_PACKAGES)
                else putStringSet(Constants.KEY_TARGET_PACKAGES, targets)
            }
            .apply()
        _targetPackages.value = targets
        _snapshot.value = null
    }

    /** Mémorise seulement le nom du paquet au premier plan, jamais son contenu. */
    fun noteForegroundPackage(packageName: String) {
        if (packageName in Constants.IGNORED_PACKAGES) return
        val current = _recentPackages.value
        if (current.firstOrNull() == packageName) return
        _recentPackages.value =
            (listOf(packageName) + current.filter { it != packageName })
                .take(Constants.MAX_RECENT_PACKAGES)
    }

    fun updateSnapshot(snapshot: CaptureSnapshot) {
        _snapshot.value = snapshot
    }

    fun setBackfill(state: BackfillState) {
        _backfill.value = state
    }
}
