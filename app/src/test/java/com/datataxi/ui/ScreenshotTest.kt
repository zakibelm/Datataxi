package com.datataxi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.datataxi.domain.Call
import com.datataxi.domain.RecurrenceBoard
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Captures d'écran de l'interface, rendues par le vrai code de l'application (sans téléphone).
 * Données d'exemple inventées, calquées sur les habitudes observées : aucune donnée réelle.
 */
abstract class ScreenshotBase(device: DeviceConfig) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = device,
        renderingMode = SessionParams.RenderingMode.V_SCROLL,
        showSystemUi = false,
    )

    /** Lundi 2 novembre 2026, 08:12 : 7 lundis d'historique. */
    private val now = LocalDateTime.parse("2026-11-02T08:12:00")

    private fun sampleIndex(): RecurrenceBoard.Index {
        val mondays = (1..7).map { LocalDate.parse("2026-11-02").minusWeeks(it.toLong()) }
        val calls = ArrayList<Call>()
        var vehicle = 100
        fun add(day: LocalDate, time: String, zone: String) {
            calls.add(Call(day, LocalTime.parse(time).toSecondOfDay(), zone, (vehicle++).toString()))
        }
        mondays.forEachIndexed { i, day ->
            // Zones 1 et 6 : actives presque tout le temps.
            listOf("08:05", "08:14", "08:25", "08:41", "08:55", "09:12", "09:33", "09:52").forEach { add(day, it, "1") }
            if (i % 2 == 0) listOf("08:11", "08:38", "09:05", "09:44").forEach { add(day, it, "6") }
            // Zone 12 : 5 lundis sur 7 vers 08:30.
            if (i < 5) add(day, "08:3${i}", "12")
            // Zone 8 : 3 lundis sur 7 vers 08:20.
            if (i in listOf(0, 2, 5)) add(day, "08:2${i}", "8")
            // Zone 41 : 2 lundis sur 7 vers 09:00.
            if (i in listOf(1, 4)) add(day, "09:0${i}", "41")
            // Zone 4 : 4 lundis sur 7 vers 09:40.
            if (i in listOf(0, 1, 3, 6)) add(day, "09:4${i}", "4")
        }
        return RecurrenceBoard.Index(calls)
    }

    @Test
    fun recurrences() {
        paparazzi.snapshot {
            MaterialTheme {
                Surface {
                    Column {
                        AppTabs(selected = 0, onSelect = {})
                        RecurrencesContent(
                            index = sampleIndex(),
                            now = now,
                            hasFullHistory = true,
                            historyStatus = "Historique complet mis à jour le 2026-11-02 08:00.",
                            refreshing = false,
                            onRefresh = {},
                        )
                    }
                }
            }
        }
    }
}

class TabletScreenshotTest : ScreenshotBase(DeviceConfig.PIXEL_C)

class PhoneScreenshotTest : ScreenshotBase(DeviceConfig.PIXEL_5)
