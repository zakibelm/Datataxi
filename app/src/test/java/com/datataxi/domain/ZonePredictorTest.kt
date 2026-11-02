package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ZonePredictorTest {

    private val monday = LocalDate.parse("2026-10-26")
    private val tuesday = LocalDate.parse("2026-10-27")

    @Test
    fun slotsAreTenMinutes() {
        assertEquals(49, ZonePredictor.slotOf(LocalTime.parse("08:14:59")))
        assertEquals("08:10–08:20", ZonePredictor.label(49))
        assertEquals("23:50–00:00", ZonePredictor.label(143))
    }

    @Test
    fun onlyCallsWithADepartureZoneAreUsed() {
        val obs = ZonePredictor.observations(
            listOf(
                JournalEntry("2026-10-26", "08:12:00 Véhicule 203 a reçu sur la Zone 6"),
                JournalEntry("2026-10-26", "08:13:00 Véhicule 409 a pris sur Zone 12 un Appel en attente vers le 1"),
                JournalEntry("2026-10-26", "08:14:00 Véhicule 203 a reçu Manuel vers le 6"), // pas de zone de départ
                JournalEntry("2026-10-26", "08:15:00 Véhicule 208 est NoLoad sur Zone 4"),   // pas un appel
            )
        )
        assertEquals(listOf("6", "12"), obs.map { it.zone })
        assertEquals(49, obs[0].slot)
    }

    @Test
    fun sameWeekdayIsPreferredWhenThereIsEnoughHistory() {
        val obs = List(6) { Observation(monday, 49, "1") } +
            List(3) { Observation(monday, 49, "6") } +
            List(20) { Observation(tuesday, 49, "12") }
        val p = ZonePredictor.predict(obs, DayOfWeek.MONDAY, 49)
        assertTrue(p.sameWeekday)
        assertEquals("1", p.chances.first().zone)
        assertEquals(6.0 / 9.0, p.probabilityOf("1"), 1e-9)
        assertEquals(0.0, p.probabilityOf("12"), 1e-9)
        assertEquals(1, p.days)
    }

    @Test
    fun fallsBackToAllDaysWhenWeekdayHistoryIsThin() {
        val obs = List(2) { Observation(monday, 49, "1") } + List(20) { Observation(tuesday, 49, "12") }
        val p = ZonePredictor.predict(obs, DayOfWeek.MONDAY, 49)
        assertFalse(p.sameWeekday)
        assertEquals("12", p.chances.first().zone)
        assertEquals(2, p.days)
    }

    @Test
    fun neighbouringSlotsCountLess() {
        val obs = List(5) { Observation(monday, 49, "1") } + // même tranche : poids 3
            List(5) { Observation(monday, 51, "6") }         // à 20 min : poids 1
        val p = ZonePredictor.predict(obs, DayOfWeek.MONDAY, 49)
        assertEquals(0.75, p.probabilityOf("1"), 1e-9)
        assertEquals(0.25, p.probabilityOf("6"), 1e-9)
        assertEquals(10, p.samples)
    }
}
