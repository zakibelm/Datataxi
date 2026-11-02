package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalDedupTest {

    private fun d(s: String) = LocalDate.parse(s)
    private val msg = "16:40:57 Véhicule 203 a reçu Manuel vers le 6"

    @Test
    fun sameTextReadOnFollowingDaysIsSeen() {
        val dedup = JournalDedup()
        dedup.record(msg, d("2026-10-17"))
        assertTrue(dedup.isSeen(msg, d("2026-10-18")))
        assertTrue(dedup.isSeen(msg, d("2026-10-20")))
        assertFalse(dedup.isSeen("16:41:30 Véhicule 409 a pris sur Zone 12", d("2026-10-18")))
    }

    @Test
    fun sameTextLongAfterIsANewEvent() {
        val dedup = JournalDedup()
        dedup.record(msg, d("2026-10-01"))
        assertFalse(dedup.isSeen(msg, d("2026-10-17")))
    }

    @Test
    fun keepOldestRemovesLaterCopiesAndKeepsOrder() {
        // Cas réel : le même message enregistré le 17, le 18 et le 19.
        val entries = listOf(
            JournalEntry("2026-10-17", msg),
            JournalEntry("2026-10-17", "16:41:30 Véhicule 409 a pris sur Zone 12 un Appel en attente vers le 1"),
            JournalEntry("2026-10-18", msg),
            JournalEntry("2026-10-18", "08:00:00 Véhicule 1 a reçu sur la Zone 1"),
            JournalEntry("2026-10-19", msg),
        )
        val (kept, idx) = JournalDedup.keepOldest(entries)
        assertEquals(listOf(0, 1, 3), idx)
        assertEquals("2026-10-17", kept[0].date)
        assertEquals(3, kept.size)
    }

    @Test
    fun sentCounterIsRemappedToKeptLines() {
        val entries = listOf(
            JournalEntry("2026-10-17", msg),
            JournalEntry("2026-10-18", msg),
            JournalEntry("2026-10-18", "08:00:00 Véhicule 1 a reçu sur la Zone 1"),
            JournalEntry("2026-10-19", "09:00:00 Véhicule 2 a reçu sur la Zone 1"),
        )
        val (_, idx) = JournalDedup.keepOldest(entries)
        // 3 lignes envoyées avant la mise à jour → 2 lignes gardées parmi elles, la 4e reste à envoyer.
        assertEquals(2, idx.count { it < 3 })
    }
}
