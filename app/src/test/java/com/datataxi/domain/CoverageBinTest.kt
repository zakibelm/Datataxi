package com.datataxi.domain

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageBinTest {
    @Test fun arrondit_aux_cinq_minutes() {
        assertEquals("2026-11-24T18:35:00", CoverageBin.start(LocalDateTime.parse("2026-11-24T18:39:59")))
        assertEquals("2026-11-24T18:00:00", CoverageBin.start(LocalDateTime.parse("2026-11-24T18:04:10")))
    }

    @Test fun minuit_reste_dans_la_bonne_journee() {
        assertEquals("2026-11-24T23:55:00", CoverageBin.start(LocalDateTime.parse("2026-11-24T23:59:59")))
        assertEquals("2026-11-25T00:00:00", CoverageBin.start(LocalDateTime.parse("2026-11-25T00:00:01")))
    }
}
