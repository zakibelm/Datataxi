package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReliabilityTest {

    private fun call(d: String, t: String, z: String) =
        Call(LocalDate.parse(d), LocalTime.parse(t).toSecondOfDay(), z, "1")

    // Quatre lundis et un mardi. Résultats attendus calculés par l'implémentation de référence
    // (même rejeu que le script du Google Sheet, vérifié sur l'historique réel).
    private val calls = listOf(
        call("2026-10-05", "08:12", "12"), call("2026-10-05", "08:15", "1"),
        call("2026-10-12", "08:20", "12"), call("2026-10-12", "09:00", "4"), call("2026-10-12", "08:15", "1"),
        call("2026-10-19", "08:14", "12"), call("2026-10-19", "08:16", "1"), call("2026-10-19", "09:05", "4"), call("2026-10-19", "09:30", "6"),
        call("2026-10-26", "08:13", "12"), call("2026-10-26", "08:40", "1"), call("2026-10-26", "09:02", "4"), call("2026-10-26", "09:31", "8"),
        call("2026-10-20", "08:12", "12"),
    )

    @Test
    fun dailyMatchesReference() {
        val d = Reliability.daily(calls)
        assertEquals(listOf("2026-10-19", "2026-10-26"), d.map { it.date.toString() })
        assertEquals(listOf(9, 11), d.map { it.judgedSlots })
        assertEquals(listOf(5, 9), d.map { it.predicted })
        assertEquals(listOf(5, 6), d.map { it.realized })
        assertEquals(listOf(22, 35), d.map { it.baselineCount })
        assertEquals(listOf(4, 3), d.map { it.baselineRealized })
    }

    @Test
    fun groupsByWeekAndMonth() {
        val d = Reliability.daily(calls)
        val weeks = Reliability.group(d, Reliability.Period.WEEK)
        assertEquals(listOf("2026-10-19", "2026-10-26"), weeks.map { it.start.toString() })
        assertEquals(1.0, weeks[0].rate, 1e-9)
        val months = Reliability.group(d, Reliability.Period.MONTH)
        assertEquals(1, months.size)
        assertEquals(14, months[0].predicted)
        assertEquals(11, months[0].realized)
        assertEquals(7.0 / 57.0, months[0].baseline!!, 1e-9)
    }

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.parse("2026-10-26"), Reliability.startOf(LocalDate.parse("2026-11-01"), Reliability.Period.WEEK))
        assertEquals(LocalDate.parse("2026-11-01"), Reliability.startOf(LocalDate.parse("2026-11-17"), Reliability.Period.MONTH))
    }
}
