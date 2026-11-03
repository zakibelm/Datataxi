package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FiltersTest {
    private val now = LocalDateTime.parse("2026-11-03T08:14:30")

    @Test
    fun defaultRangeStartsNowForTwoHours() {
        val f = Filters()
        assertEquals(LocalDateTime.parse("2026-11-03T08:10"), f.start(now))
        assertEquals(12, f.slotCount(now))
    }

    @Test
    fun chosenHoursGiveTheRange() {
        val f = Filters(startMinute = 7 * 60, endMinute = 10 * 60)
        assertEquals(LocalDateTime.parse("2026-11-03T07:00"), f.start(now))
        assertEquals(18, f.slotCount(now))
        // Au plus 6 heures, et une fin avant le début passe minuit.
        assertEquals(36, Filters(startMinute = 6 * 60, endMinute = 23 * 60).slotCount(now))
        assertEquals(12, Filters(startMinute = 23 * 60, endMinute = 60).slotCount(now))
    }

    @Test
    fun keepsChosenZonesAndType() {
        val c = Call(LocalDate.parse("2026-11-02"), 30000, "12", "1", setOf(CallType.TRANSPORT_ADAPTE, CallType.TERREBONNE))
        assertTrue(Filters().keep(c))
        assertTrue(Filters(zones = setOf("12", "41"), types = setOf(CallType.TRANSPORT_ADAPTE)).keep(c))
        assertTrue(Filters(types = setOf(CallType.TAXIBUS, CallType.TERREBONNE)).keep(c))
        assertFalse(Filters(zones = setOf("41")).keep(c))
        assertFalse(Filters(types = setOf(CallType.TAXIBUS, CallType.CISSS)).keep(c))
        assertEquals(2, Filters(zones = setOf("1"), startMinute = 0).activeCount)
    }
}
