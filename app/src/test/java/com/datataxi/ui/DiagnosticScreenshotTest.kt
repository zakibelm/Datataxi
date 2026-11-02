package com.datataxi.ui

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.datataxi.data.CaptureStore
import com.datataxi.data.JournalStore
import com.datataxi.domain.CaptureSnapshot
import com.datataxi.domain.CapturedLine
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** Capture de l'onglet Diagnostic, avec un journal d'exemple (aucune donnée réelle). */
class DiagnosticScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        showSystemUi = false,
    )

    /** L'écran ouvre des fenêtres « Enregistrer sous » : en capture, on n'en ouvre aucune. */
    private val noResults = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) = Unit
        }
    }

    @Test
    fun diagnostic() {
        val fraxion = "com.fraxion.siv"
        CaptureStore.setServiceConnected(true)
        CaptureStore.noteForegroundPackage("com.android.launcher")
        CaptureStore.noteForegroundPackage(fraxion)
        CaptureStore.setTarget(paparazzi.context, fraxion)
        val lines = listOf(
            "08:31:12 Véhicule 203 a reçu sur la Zone 12 [Transport Adapté]",
            "08:24:40 Véhicule 409 a pris sur Zone 8 un Appel en attente vers le 1",
            "08:19:05 Véhicule 405 a reçu sur la Zone 1 vers le 6",
            "08:12:58 Véhicule 104 a reçu sur la Zone 6",
            "08:05:33 Véhicule 428 a reçu sur la Zone 1",
        )
        CaptureStore.updateSnapshot(
            CaptureSnapshot(fraxion, 1_793_621_520_000L, lines.mapIndexed { i, t -> CapturedLine(3, "journal_ligne_$i", "TextView", t) })
        )
        JournalStore.addAll(LocalDateTime.parse("2026-11-02T08:32:00"), lines)

        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides noResults) {
                MaterialTheme {
                    Surface {
                        Column {
                            AppTabs(selected = 1, onSelect = {})
                            DiagnosticScreen(scrollable = false)
                        }
                    }
                }
            }
        }
    }
}
