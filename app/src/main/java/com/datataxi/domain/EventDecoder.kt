package com.datataxi.domain

/** Champs décodés d'une ligne du journal Fraxion. Tout champ absent du texte reste null. */
data class DecodedEvent(
    val vehicleId: String?,
    val status: String?,
    val departureZone: String?,
    val destinationZone: String?,
    val serviceType: String?,
    val confidence: Double?,
)

/**
 * Décodage strict, fondé sur des lignes réelles de Fraxion, par exemple :
 *  "17:26:02 Véhicule 203 a reçu sur la Zone 12 [Transport Adapté]"
 *  "17:16:01 Véhicule 408 a reçu sur la Zone 1 vers le 5"
 *  "16:41:30 Véhicule 409 a pris sur Zone 12 un Appel en attente vers le 1"
 *  "16:40:57 Véhicule 203 a reçu Manuel vers le 6"
 * Une ligne qui ne correspond pas n'est jamais devinée : tous les champs restent vides.
 */
object EventDecoder {

    private val MAIN = Regex("""^\d{2}:\d{2}:\d{2}\s+Véhicule\s+(\d+)\s+a\s+(reçu|pris)\b(.*)$""")
    private val ZONE = Regex("""\bsur\s+(?:la\s+)?Zone\s+(\d+)""")
    private val DEST = Regex("""\bvers\s+le\s+(\d+)""")
    private val SERVICE = Regex("""\[([^\]]+)]""")

    fun decode(text: String): DecodedEvent {
        val m = MAIN.find(text.trim())
            ?: return DecodedEvent(null, null, null, null, null, null)
        val vehicle = m.groupValues[1]
        val verb = m.groupValues[2]
        val rest = m.groupValues[3]

        val zone = ZONE.find(rest)?.groupValues?.get(1)
        val dest = DEST.find(rest)?.groupValues?.get(1)
        val service = SERVICE.find(rest)?.groupValues?.get(1)?.trim()
        val manual = Regex("""\bManuel\b""").containsMatchIn(rest)
        val waiting = rest.contains("Appel en attente")

        val status = buildString {
            append(if (verb == "reçu") "recu" else "pris")
            if (manual) append("_manuel")
            if (waiting) append("_appel_en_attente")
        }
        // Confiance : 1.0 si le motif principal est reconnu et que le reste est entièrement expliqué.
        val leftover = rest
            .replace(ZONE, "").replace(DEST, "").replace(SERVICE, "")
            .replace("Manuel", "").replace("Appel en attente", "")
            .replace(Regex("""\b(un|une|la)\b"""), "").trim()
        val confidence = if (leftover.isEmpty()) 1.0 else 0.6

        return DecodedEvent(vehicle, status, zone, dest, service, confidence)
    }
}
