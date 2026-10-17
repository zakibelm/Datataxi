package com.datataxi.util

object Constants {
    const val PREFS_NAME = "datataxi_prefs"
    const val KEY_TARGET_PACKAGE = "target_package"

    /** Garde-fous pour ne jamais surcharger la tablette. */
    const val MAX_NODES = 2000
    const val MAX_DEPTH = 40
    const val MAX_RECENT_PACKAGES = 12
    const val MAX_LINES_DISPLAYED = 400

    /** Attend la fin d'une rafale d'événements avant de lire l'écran. */
    const val DEBOUNCE_MS = 300L

    /** Paquets système jamais proposés comme cible. */
    val IGNORED_PACKAGES = setOf("com.android.systemui")
}
