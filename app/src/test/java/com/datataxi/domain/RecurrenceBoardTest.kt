package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class RecurrenceBoardTest {

    private fun call(date: String, time: String, zone: String, vehicle: String = "1") =
        Call(LocalDate.parse(date), LocalTime.parse(time).toSecondOfDay(), zone, vehicle)

    // Trois lundis : 2026-10-12, 2026-10-19, 2026-10-26
    private val calls = listOf(
        call("2026-10-12", "08:12:00", "12"),
        call("2026-10-19", "08:19:30", "12"),
        call("2026-10-26", "08:21:00", "12"), // tranche voisine : même habitude (± 10 min)
        call("2026-10-12", "08:15:00", "4"),
        call("2026-10-19", "08:11:00", "4"),
        call("2026-10-26", "08:13:00", "9"),   // une seule fois : pas une récurrence
        call("2026-10-27", "08:14:00", "9"),   // un mardi : ne compte pas pour lundi
        call("2026-10-12", "08:16:00", "12", "2"), // deux appels le même jour = une seule journée
    )

    @Test
    fun slotsAndLabels() {
        assertEquals(49, RecurrenceBoard.slotOf(LocalTime.parse("08:14:59")))
        assertEquals("08:10–08:20", RecurrenceBoard.label(49))
        assertEquals("23:50–00:00", RecurrenceBoard.label(143))
    }

    @Test
    fun onlyZonesSeenOnTwoDaysOrMoreAreShownWithTheirWeight() {
        val r = RecurrenceBoard.recurrences(RecurrenceBoard.Index(calls), DayOfWeek.MONDAY, 49)
        assertEquals(listOf("12", "4"), r.zones.map { it.zone })
        assertEquals(3, r.of("12")!!.days)
        assertEquals(Weight.MOYEN, r.of("12")!!.weight)
        assertEquals(Weight.FAIBLE, r.of("4")!!.weight)
        assertEquals(3, r.of("12")!!.observedDays)
        assertNull(r.of("9"))
    }

    @Test
    fun typesOfCallsAreCountedPerZone() {
        val withTypes = listOf(
            Call(LocalDate.parse("2026-10-12"), LocalTime.parse("08:12:00").toSecondOfDay(), "12", "1", setOf(CallType.TAXIBUS)),
            Call(LocalDate.parse("2026-10-19"), LocalTime.parse("08:14:00").toSecondOfDay(), "12", "2", setOf(CallType.TAXIBUS)),
            Call(LocalDate.parse("2026-10-19"), LocalTime.parse("08:15:00").toSecondOfDay(), "12", "3", setOf(CallType.REGULIER)),
        )
        val r = RecurrenceBoard.recurrences(RecurrenceBoard.Index(withTypes), DayOfWeek.MONDAY, 49)
        assertEquals(listOf(CallType.TAXIBUS to 2, CallType.REGULIER to 1), r.of("12")!!.types)
    }

    @Test
    fun weightScale() {
        assertNull(Weight.of(1))
        assertEquals(Weight.FAIBLE, Weight.of(2))
        assertEquals(Weight.MOYEN, Weight.of(3))
        assertEquals(Weight.FORT, Weight.of(4))
        assertEquals(Weight.TRES_FORT, Weight.of(5))
        assertEquals(Weight.TRES_FORT, Weight.of(12))
    }

    @Test
    fun mergeCountsTheSameCallOnce() {
        val history = listOf(call("2026-10-12", "08:12:00", "12", "203"))
        val local = listOf(call("2026-10-12", "08:12:00", "12", "203"), call("2026-10-12", "08:30:00", "6", "101"))
        assertEquals(2, RecurrenceBoard.merge(history, local).size)
    }

    @Test
    fun journalLinesBecomeCalls() {
        val c = RecurrenceBoard.callsFromJournal(
            listOf(
                JournalEntry("2026-10-26", "08:12:00 Véhicule 203 a reçu sur la Zone 6"),
                JournalEntry("2026-10-26", "08:13:00 Véhicule 409 a pris sur Zone 12 un Appel en attente vers le 1"),
                JournalEntry("2026-10-26", "08:14:00 Véhicule 203 a reçu Manuel vers le 6"),
                JournalEntry("2026-10-26", "08:15:00 Véhicule 208 est NoLoad sur Zone 4"),
            )
        )
        assertEquals(listOf("6", "12"), c.map { it.zone })
        assertEquals("203", c[0].vehicle)
        assertTrue(c.all { it.slot == 49 })
    }
}
