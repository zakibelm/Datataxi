package com.datataxi.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.datataxi.data.CaptureStore
import com.datataxi.data.JournalStore
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

    private class ReadResult(
        val root: AccessibilityNodeInfo,
        val events: List<String>,
        val added: Int,
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        CaptureStore.init(applicationContext)
        JournalStore.init(applicationContext)
        CaptureStore.setServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Pour toute application : on retient seulement son nom, jamais son contenu.
        CaptureStore.noteForegroundPackage(pkg)

        // Le contenu n'est lu que pour l'application cible choisie par l'utilisateur.
        if (pkg != CaptureStore.targetPackage.value) return

        if (CaptureStore.backfill.value.phase == BackfillPhase.ARMED) startBackfill()

        handler.removeCallbacks(captureRunnable)
        handler.postDelayed(captureRunnable, Constants.DEBOUNCE_MS)
    }

    /** Lit l'écran de l'application cible, met à jour la capture et le journal. */
    private fun readTarget(): ReadResult? {
        val target = CaptureStore.targetPackage.value ?: return null
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() != target) return null

        val lines = NodeTextExtractor.extract(root)
        CaptureStore.updateSnapshot(
            CaptureSnapshot(
                packageName = target,
                capturedAtMillis = System.currentTimeMillis(),
                lines = lines,
            )
        )
        val events = JournalParser.eventLines(lines)
        val added = JournalStore.addAll(JournalStore.today(), events)
        return ReadResult(root, events, added)
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
        if (result == null) {
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
