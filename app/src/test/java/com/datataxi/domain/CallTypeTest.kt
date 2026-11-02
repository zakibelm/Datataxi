package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CallTypeTest {
    @Test
    fun labelsFromFraxionAndArchivesAreGrouped() {
        assertEquals(CallType.REGULIER, CallType.of(null, "recu"))
        assertEquals(CallType.REGULIER, CallType.of("Régulier / autre", "historique_xlsx"))
        assertEquals(CallType.REGULIER, CallType.of("Regulier", "historique_xlsx"))
        assertEquals(CallType.TRANSPORT_ADAPTE, CallType.of("Transport Adapté", "recu"))
        assertEquals(CallType.TAXIBUS, CallType.of("4 Pers Taxibus EXO", "recu"))
        assertEquals(CallType.TAXIBUS, CallType.of("Taxibus EXO — 2 personnes", "historique_xlsx"))
        assertEquals(CallType.APP_MOBILE, CallType.of("App. Mobile", "recu"))
        assertEquals(CallType.APP_MOBILE, CallType.of("App Mobile", "historique_xlsx"))
        assertEquals(CallType.ATTENTE, CallType.of(null, "pris_appel_en_attente"))
        assertEquals(CallType.ATTENTE, CallType.of("Appel en attente pris", "historique_xlsx"))
        assertEquals(CallType.TERREBONNE, CallType.of("Régulier Terrebonne", "historique_xlsx"))
        assertEquals(CallType.AUTRES, CallType.of("Déverrouillage", "recu"))
    }

    @Test
    fun journalLineGetsItsType() {
        val c = RecurrenceBoard.callsFromJournal(
            listOf(JournalEntry("2026-10-26", "09:02:24 Véhicule 130 a reçu sur la Zone 1 vers le 2 [2 Pers Taxibus EXO]"))
        )
        assertEquals(CallType.TAXIBUS, c.single().type)
    }
}
