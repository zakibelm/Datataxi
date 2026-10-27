package com.datataxi.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.datataxi.data.AlertSettings
import com.datataxi.data.CaptureStore
import com.datataxi.data.JournalStore
import com.datataxi.data.ScreenLogStore
import com.datataxi.domain.AlertDetector
import com.datataxi.domain.BackfillPhase
import com.datataxi.domain.BackfillState
import com.datataxi.domain.CaptureSnapshot
import com.datataxi.domain.JournalParser
import com.datataxi.util.Constants

class DataTaxiAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val captureRunnable = Runnable { captureNow() }
    private val backfillRunnable = Runnable { backfillStep() }

    private var lastSignature: String? = null
    private var unchangedSteps = 0

    /** Pour chaque application : le mot déclencheur était-il déjà affiché à la lecture précédente ? */
    private val alertShown = HashMap<String, Boolean>()
    private var lastAlertAt = 0L

    /** Dernier nom de fenêtre vu, pour chaque application cible. */
    private val currentScreens = HashMap<String, String>()

    private class ReadResult(
        val root: AccessibilityNodeInfo,
        val events: List<String>,
        val added: Int,
        val isJournalApp: Boolean,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        CaptureStore.init(applicationContext)
        JournalStore.init(applicationContext)
        ScreenLogStore.init(applicationContext)
        AlertSettings.init(applicationContext)
        CaptureStore.setServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Pour toute application : on retient seulement son nom, jamais son contenu.
        CaptureStore.noteForegroundPackage(pkg)

        // Le contenu n'est lu que pour l'application cible choisie par l'utilisateur.
        if (pkg !in CaptureStore.targetPackages.value) return

        // Un changement de fenêtre = un nouvel écran : on retient son nom pour le journal des écrans.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.className?.toString()?.takeIf { it.isNotBlank() }?.let { currentScreens[pkg] = it }
        }

        // La récupération de l'historique par défilement ne vise que Fraxion (Favoris).
        if (CaptureStore.backfill.value.phase == BackfillPhase.ARMED && isJournalApp(pkg)) startBackfill()

        handler.removeCallbacks(captureRunnable)
        handler.postDelayed(captureRunnable, Constants.DEBOUNCE_MS)
    }

    private fun isJournalApp(pkg: String) =
        pkg.contains(Constants.JOURNAL_PACKAGE_HINT, ignoreCase = true)

    /** Lit l'écran d'une application cible, met à jour la capture et les journaux. */
    private fun readTarget(): ReadResult? {
        val root = rootInActiveWindow ?: return null
        val target = root.packageName?.toString() ?: return null
        if (target !in CaptureStore.targetPackages.value) return null
        val journalApp = isJournalApp(target)

        val lines = NodeTextExtractor.extract(root)
        CaptureStore.updateSnapshot(
            CaptureSnapshot(
                packageName = target,
                capturedAtMillis = System.currentTimeMillis(),
                lines = lines,
            )
        )
        // Journal d'événements : seulement pour Fraxion, pour ne pas y mêler les lignes d'une autre app.
        val events = if (journalApp) JournalParser.eventLines(lines) else emptyList()
        val added = if (journalApp) JournalStore.addAll(JournalStore.today(), events) else 0
        // Tous les écrans de toutes les applications cibles, avec le nom de l'application.
        val screen = currentScreens[target] ?: UNKNOWN_SCREEN
        ScreenLogStore.record("$target › $screen", lines.map { it.text })
        // Pas d'alerte pendant la récupération de l'historique : les vieilles lignes ne comptent pas.
        if (CaptureStore.backfill.value.phase != BackfillPhase.RUNNING) {
            checkAlert(target, lines.map { it.text })
        }
        return ReadResult(root, events, added, journalApp)
    }

    /**
     * Alerte « c'est l'heure » : se déclenche quand un mot déclencheur APPARAÎT à l'écran
     * (pas tant qu'il y reste), avec un délai minimal entre deux alertes.
     */
    private fun checkAlert(target: String, texts: List<String>) {
        if (!AlertSettings.enabled(applicationContext)) {
            alertShown[target] = false
            return
        }
        val match = AlertDetector.firstMatch(texts, AlertSettings.phrases(applicationContext))
        val wasShown = alertShown[target] == true
        alertShown[target] = match != null
        if (match == null || wasShown) return

        val now = SystemClock.elapsedRealtime()
        if (lastAlertAt != 0L && now - lastAlertAt < Constants.ALERT_COOLDOWN_MS) return
        lastAlertAt = now
        AlertSettings.recordAlert(applicationContext, target, match)
        AlertPlayer.play(applicationContext, AlertSettings.message(applicationContext))
        com.datataxi.companion.CompanionService.react(com.datataxi.companion.MascotView.Mood.EXCITED, 8000L)
    }

    private companion object {
        const val UNKNOWN_SCREEN = "écran inconnu"
    }

    private fun captureNow() {
        readTarget()
    }

    private fun startBackfill() {
        lastSignature = null
        unchangedSteps = 0
        CaptureStore.setBackfill(
            BackfillState(BackfillPhase.RUNNING, 0, 0, "Défilement en cours. Ne touche pas à l'écran.")
        )
        handler.removeCallbacks(backfillRunnable)
        handler.postDelayed(backfillRunnable, Constants.BACKFILL_DELAY_MS)
    }

    private fun finishBackfill(state: BackfillState, message: String) {
        handler.removeCallbacks(backfillRunnable)
        CaptureStore.setBackfill(
            state.copy(phase = BackfillPhase.DONE, message = message)
        )
    }

    private fun backfillStep() {
        val state = CaptureStore.backfill.value
        if (state.phase != BackfillPhase.RUNNING) return

        val result = readTarget()
        if (result == null || !result.isJournalApp) {
            finishBackfill(state, "Favoris n'est plus au premier plan. ${state.added} nouvelles lignes ajoutées.")
            return
        }

        val steps = state.steps + 1
        val added = state.added + result.added
        val current = state.copy(steps = steps, added = added)

        // Si l'écran n'a pas changé depuis l'étape précédente, on est au bout de la liste.
        val signature = result.events.joinToString("|")
        unchangedSteps = if (signature == lastSignature) unchangedSteps + 1 else 0
        lastSignature = signature

        if (unchangedSteps >= Constants.BACKFILL_UNCHANGED_LIMIT) {
            finishBackfill(current, "Fin de la liste atteinte. $added nouvelles lignes ajoutées.")
            return
        }
        if (steps >= Constants.BACKFILL_MAX_STEPS) {
            finishBackfill(current, "Limite de ${Constants.BACKFILL_MAX_STEPS} étapes atteinte. $added nouvelles lignes ajoutées.")
            return
        }

        val scrolled = ScrollHelper.scrollJournalForward(result.root)
        if (!scrolled) {
            finishBackfill(
                current,
                "Fraxion ne permet pas le défilement automatique (ou la fin est atteinte). $added nouvelles lignes ajoutées.",
            )
            return
        }

        CaptureStore.setBackfill(
            current.copy(message = "Défilement : étape $steps, $added nouvelles lignes. Ne touche pas à l'écran.")
        )
        handler.postDelayed(backfillRunnable, Constants.BACKFILL_DELAY_MS)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(captureRunnable)
        handler.removeCallbacks(backfillRunnable)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        CaptureStore.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureRunnable)
        handler.removeCallbacks(backfillRunnable)
        CaptureStore.setServiceConnected(false)
        super.onDestroy()
    }
}
