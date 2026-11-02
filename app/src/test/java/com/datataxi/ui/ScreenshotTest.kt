package com.datataxi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.datataxi.domain.Call
import com.datataxi.domain.CallType
import com.datataxi.domain.Filters
import com.datataxi.domain.Reliability
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

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

    /** Lundi 2 novembre 2026, 08:12. */
    protected val now: LocalDateTime = LocalDateTime.parse("2026-11-02T08:12:00")

    /** 8 semaines d'appels d'exemple : zones 1 et 6 très actives, d'autres zones avec des habitudes. */
    protected val calls: List<Call> by lazy {
        val rnd = Random(42)
        val out = ArrayList<Call>()
        var vehicle = 100
        val habits = listOf(
            // zone, minute de la journée, chance par jour, type
            Triple("12", 8 * 60 + 30, 0.75) to CallType.TRANSPORT_ADAPTE,
            Triple("8", 8 * 60 + 20, 0.45) to CallType.REGULIER,
            Triple("41", 9 * 60 + 0, 0.35) to CallType.TAXIBUS,
            Triple("4", 9 * 60 + 40, 0.6) to CallType.REGULIER,
            Triple("9", 17 * 60 + 10, 0.55) to CallType.APP_MOBILE,
            Triple("3", 16 * 60 + 30, 0.5) to CallType.REGULIER,
        )
        var day = LocalDate.parse("2026-09-07")
        while (!day.isAfter(LocalDate.parse("2026-11-02"))) {
            // Zones 1 et 6 : un appel toutes les 10 à 40 minutes de 6 h à 20 h.
            for (zone in listOf("1", "6")) {
                var m = 6 * 60 + rnd.nextInt(30)
                while (m < 20 * 60) {
                    out.add(Call(day, m * 60, zone, (vehicle++).toString(), if (rnd.nextInt(5) == 0) CallType.TRANSPORT_ADAPTE else CallType.REGULIER))
                    m += if (zone == "1") 10 + rnd.nextInt(20) else 20 + rnd.nextInt(30)
                }
            }
            // La confiance progresse avec les semaines : les habitudes deviennent plus régulières.
            val maturity = 0.6 + 0.4 * (day.toEpochDay() - LocalDate.parse("2026-09-07").toEpochDay()) / 56.0
            habits.forEach { (h, type) ->
                val (zone, minute, chance) = h
                if (rnd.nextDouble() < chance * maturity) {
                    out.add(Call(day, (minute + rnd.nextInt(-8, 9)) * 60, zone, (vehicle++).toString(), type))
                }
            }
            day = day.plusDays(1)
        }
        out
    }

    protected fun shot(filters: Filters, filtersOpen: Boolean, period: Reliability.Period) {
        val reliability = Reliability.daily(calls)
        paparazzi.snapshot {
            DataTaxiTheme {
                Surface {
                    Column {
                        AppTabs(selected = 0, onSelect = {})
                        RecurrencesContent(
                            calls = calls,
                            reliability = reliability,
                            now = now,
                            hasFullHistory = true,
                            historyStatus = "Historique complet mis à jour le 2026-11-02 08:00.",
                            refreshing = false,
                            onRefresh = {},
                            initialFilters = filters,
                            initialFiltersOpen = filtersOpen,
                            initialPeriod = period,
                        )
                    }
                }
            }
        }
    }
}

class TabletScreenshotTest : ScreenshotBase(DeviceConfig.PIXEL_C) {
    @Test
    fun recurrences() = shot(Filters(), filtersOpen = true, period = Reliability.Period.WEEK)
}

class PhoneScreenshotTest : ScreenshotBase(DeviceConfig.PIXEL_5) {
    @Test
    fun recurrences() = shot(Filters(), filtersOpen = false, period = Reliability.Period.WEEK)

    @Test
    fun filtresEtConfianceParJour() = shot(
        Filters(zones = setOf("1", "12", "41"), type = CallType.TRANSPORT_ADAPTE, startMinute = 7 * 60, endMinute = 10 * 60),
        filtersOpen = true,
        period = Reliability.Period.DAY,
    )
}
