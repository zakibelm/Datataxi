package com.datataxi.data

import android.content.Context
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

    private val _targetPackage = MutableStateFlow<String?>(null)
    val targetPackage: StateFlow<String?> = _targetPackage.asStateFlow()

    private val _recentPackages = MutableStateFlow<List<String>>(emptyList())
    val recentPackages: StateFlow<List<String>> = _recentPackages.asStateFlow()

    private val _snapshot = MutableStateFlow<CaptureSnapshot?>(null)
    val snapshot: StateFlow<CaptureSnapshot?> = _snapshot.asStateFlow()

    @Volatile
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        val prefs = context.applicationContext
            .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        _targetPackage.value = prefs.getString(Constants.KEY_TARGET_PACKAGE, null)
        loaded = true
    }

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    fun setTarget(context: Context, packageName: String?) {
        context.applicationContext
            .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (packageName == null) remove(Constants.KEY_TARGET_PACKAGE)
                else putString(Constants.KEY_TARGET_PACKAGE, packageName)
            }
            .apply()
        _targetPackage.value = packageName
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
}
