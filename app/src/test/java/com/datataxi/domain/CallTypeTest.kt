package com.datataxi.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CallTypeTest {
    @Test
    fun labelsFromFraxionAndArchives() {
        assertEquals(setOf(CallType.REGULIER), CallType.of(null, "recu"))
        assertEquals(setOf(CallType.REGULIER), CallType.of("Régulier / autre", "historique_xlsx"))
        assertEquals(setOf(CallType.REGULIER), CallType.of("Non précisé", "historique_xlsx"))
        assertEquals(setOf(CallType.TRANSPORT_ADAPTE), CallType.of("Transport Adapté", "recu"))
        assertEquals(setOf(CallType.TAXIBUS), CallType.of("4 Pers Taxibus EXO", "recu"))
        assertEquals(setOf(CallType.APP_MOBILE), CallType.of("App Mobile", "historique_xlsx"))
        assertEquals(setOf(CallType.ATTENTE), CallType.of(null, "pris_appel_en_attente"))
        assertEquals(setOf(CallType.CISSS), CallType.of("CISSS", "recu"))
        assertEquals(setOf(CallType.FOURNISSEUR), CallType.of("Fournisseur Externe - CoopLaval", "recu"))
        assertEquals(setOf(CallType.ANIMAUX), CallType.of("Animal en cage", "recu"))
    }

    @Test
    fun severalLabelsGiveSeveralTypes() {
        assertEquals(setOf(CallType.DEVERROUILLAGE, CallType.TERREBONNE), CallType.of("Déverrouillage,Terrebonne", "recu"))
        assertEquals(setOf(CallType.SURVOLTAGE, CallType.TERREBONNE), CallType.of("Survoltage câble,Survoltage pack,Terrebonne", "recu"))
    }

    @Test
    fun unknownLabelIsKeptAsItsOwnType() {
        assertEquals(setOf("Nouveau service"), CallType.of("nouveau service", "recu"))
    }

    @Test
    fun journalLineGetsItsTypes() {
        val c = RecurrenceBoard.callsFromJournal(
            listOf(JournalEntry("2026-10-26", "09:02:24 Véhicule 130 a reçu sur la Zone 1 vers le 2 [2 Pers Taxibus EXO]"))
        )
        assertEquals(setOf(CallType.TAXIBUS), c.single().types)
    }

    @Test
    fun presentTypesAreSortedByCount() {
        val d = java.time.LocalDate.parse("2026-11-02")
        val calls = listOf(
            Call(d, 1, "1", "1", setOf(CallType.CISSS)),
            Call(d, 2, "1", "2", setOf(CallType.REGULIER)),
            Call(d, 3, "1", "3", setOf(CallType.REGULIER, CallType.TERREBONNE)),
        )
        assertEquals(listOf(CallType.REGULIER, CallType.TERREBONNE, CallType.CISSS), CallType.present(calls))
    }
}
