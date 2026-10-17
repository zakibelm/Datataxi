package com.datataxi.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.datataxi.data.CaptureStore
import com.datataxi.domain.CaptureSnapshot
import com.datataxi.util.Constants

class DataTaxiAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val captureRunnable = Runnable { captureNow() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        CaptureStore.init(applicationContext)
        CaptureStore.setServiceConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Pour toute application : on retient seulement son nom, jamais son contenu.
        CaptureStore.noteForegroundPackage(pkg)

        // Le contenu n'est lu que pour l'application cible choisie par l'utilisateur.
        if (pkg != CaptureStore.targetPackage.value) return

        handler.removeCallbacks(captureRunnable)
        handler.postDelayed(captureRunnable, Constants.DEBOUNCE_MS)
    }

    private fun captureNow() {
        val target = CaptureStore.targetPackage.value ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != target) return

        CaptureStore.updateSnapshot(
            CaptureSnapshot(
                packageName = target,
                capturedAtMillis = System.currentTimeMillis(),
                lines = NodeTextExtractor.extract(root),
            )
        )
    }

    override fun onInterrupt() {
        handler.removeCallbacks(captureRunnable)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        CaptureStore.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(captureRunnable)
        CaptureStore.setServiceConnected(false)
        super.onDestroy()
    }
}
