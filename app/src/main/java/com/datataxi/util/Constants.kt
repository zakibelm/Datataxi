package com.datataxi.util

object Constants {
    const val PREFS_NAME = "datataxi_prefs"
    const val KEY_TARGET_PACKAGE = "target_package"

    /** Garde-fous pour ne jamais surcharger la tablette. */
    const val MAX_NODES = 2000
    const val MAX_DEPTH = 40
    const val MAX_RECENT_PACKAGES = 12
    const val MAX_LINES_DISPLAYED = 400
    const val JOURNAL_PREVIEW_LINES = 15

    /** Attend la fin d'une rafale d'événements avant de lire l'écran. */
    const val DEBOUNCE_MS = 300L

    /** Récupération automatique de l'historique : pauses et limites du défilement. */
    const val BACKFILL_DELAY_MS = 800L
    const val BACKFILL_MAX_STEPS = 60
    const val BACKFILL_UNCHANGED_LIMIT = 2

    /** Journal des écrans : limites pour ne jamais remplir la tablette. */
    const val SCREEN_LOG_MAX_LINES = 300
    const val SCREEN_LOG_MAX_BYTES = 8L * 1024 * 1024

    /** Paquets système jamais proposés comme cible. */
    val IGNORED_PACKAGES = setOf("com.android.systemui")
}
