package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EventDaterTest {

    private val none: (String) -> LocalDate? = { null }
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun lateEveningMessageReadNextMorningIsDatedThePreviousDay() {
        // Cas réel : 23:58 lu le 18 au matin → c'était le 17.
        val now = LocalDateTime.parse("2026-10-18T08:00:00")
        val dates = EventDater.date(listOf("23:58:24 Véhicule 203 a reçu sur la Zone 6"), now, none)
        assertEquals(listOf(d("2026-10-17")), dates)
    }

    @Test
    fun messageEarlierTodayKeepsToday() {
        val now = LocalDateTime.parse("2026-10-18T16:45:00")
        val dates = EventDater.date(listOf("16:40:57 Véhicule 203 a reçu Manuel vers le 6"), now, none)
        assertEquals(listOf(d("2026-10-18")), dates)
    }

    @Test
    fun screenCrossingMidnightGoesBackOneDay() {
        // Écran du 26 à 10:00, du plus récent au plus ancien (onglet « Essai dates 26-10 »).
        val now = LocalDateTime.parse("2026-10-26T10:00:00")
        val lines = listOf(
            "09:59:46 Véhicule 117 a reçu sur la Zone 5",
            "00:03:17 Véhicule 421 a pris sur Zone 1 un Appel en attente vers le 44",
            "23:43:16 Véhicule 426 a reçu sur la Zone 6",
            "12:57:53 Véhicule 404 a reçu sur la Zone 1 vers le 91",
            "04:09:04 Véhicule 406 a reçu sur la Zone 6",
        )
        val dates = EventDater.date(lines, now, none)
        assertEquals(
            listOf(d("2026-10-26"), d("2026-10-26"), d("2026-10-25"), d("2026-10-25"), d("2026-10-25")),
            dates,
        )
    }

    @Test
    fun knownLineIsAnAnchorForTheNextOnes() {
        // Après un défilement : la première ligne visible est déjà connue, datée du 24.
        val now = LocalDateTime.parse("2026-10-26T10:00:00")
        val anchor = "00:05:23 Véhicule 208 a reçu sur la Zone 6"
        val lines = listOf(anchor, "23:58:43 Véhicule 430 a reçu sur la Zone 1")
        val dates = EventDater.date(lines, now) { if (it == anchor) d("2026-10-24") else null }
        assertEquals(listOf(d("2026-10-24"), d("2026-10-23")), dates)
    }
}
