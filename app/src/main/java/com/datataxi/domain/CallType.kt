package com.datataxi.domain

/**
 * Types d'appel regroupés. Fraxion et les archives écrivent la même chose de plusieurs façons
 * (« App. Mobile », « App Mobile », « Application mobile »...) : 56 libellés deviennent 7 types.
 * Mêmes règles que la feuille « Calcul » du Google Sheet.
 */
object CallType {
    const val REGULIER = "Régulier"
    const val TRANSPORT_ADAPTE = "Transport adapté"
    const val TAXIBUS = "Taxibus EXO"
    const val APP_MOBILE = "Application mobile"
    const val ATTENTE = "Appel en attente"
    const val TERREBONNE = "Terrebonne"
    const val AUTRES = "Autres services"

    val ALL = listOf(REGULIER, TRANSPORT_ADAPTE, TAXIBUS, APP_MOBILE, ATTENTE, TERREBONNE, AUTRES)

    private val APP = Regex("""app\.? ?mobile|application mobile""")
    private val REG = Regex("""^(|r[ée]gulier.*|non pr[ée]cis[ée]|auto)$""")

    /** @param service le libellé entre crochets (ou la colonne service_type) ; @param status current_status. */
    fun of(service: String?, status: String?): String {
        val s = service.orEmpty().trim().lowercase()
        val st = status.orEmpty().trim()
        return when {
            "taxibus" in s -> TAXIBUS
            "transport adapt" in s -> TRANSPORT_ADAPTE
            APP.containsMatchIn(s) -> APP_MOBILE
            "attente" in s || st.startsWith("pris") -> ATTENTE
            "terrebonne" in s -> TERREBONNE
            REG.matches(s) -> REGULIER
            else -> AUTRES
        }
    }
}
