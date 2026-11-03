package com.datataxi.domain

/**
 * Types d'appel. Fraxion écrit le service entre crochets, parfois plusieurs à la fois
 * (« Déverrouillage,Terrebonne »), et avec des variantes (« App. Mobile », « App Mobile »...).
 * Chaque appel reçoit donc un ou plusieurs types ; les variantes d'un même type sont regroupées,
 * et un libellé inconnu est gardé tel quel pour apparaître tout seul dans les filtres.
 */
object CallType {
    const val REGULIER = "Régulier"
    const val TRANSPORT_ADAPTE = "Transport adapté"
    const val TAXIBUS = "Taxibus EXO"
    const val APP_MOBILE = "Application mobile"
    const val TERREBONNE = "Terrebonne"
    const val CISSS = "CISSS"
    const val DEVERROUILLAGE = "Déverrouillage"
    const val SURVOLTAGE = "Survoltage"
    const val DEPANNEUR = "Commission dépanneur"
    const val STATION_VAN = "Station-Van"
    const val ANIMAUX = "Animaux"
    const val FOURNISSEUR = "Fournisseur externe"
    const val DEUX_TETES = "2 têtes"
    const val LIVRAISON = "Livraison"

    /** Ordre d'affichage quand deux types ont autant d'appels. */
    val KNOWN = listOf(
        REGULIER, TRANSPORT_ADAPTE, TAXIBUS, APP_MOBILE, TERREBONNE, CISSS,
        DEVERROUILLAGE, SURVOLTAGE, DEPANNEUR, STATION_VAN, ANIMAUX, FOURNISSEUR, DEUX_TETES, LIVRAISON,
    )

    private val APP = Regex("""app\.? ?mobile|application mobile""")
    private val REG = Regex("""^(|r[ée]gulier.*|non pr[ée]cis[ée]|auto)$""")

    /** Un libellé (sans virgule) vers son type. */
    private fun one(label: String): String? {
        val s = label.trim().lowercase()
        return when {
            "taxibus" in s -> TAXIBUS
            "transport adapt" in s -> TRANSPORT_ADAPTE
            APP.containsMatchIn(s) -> APP_MOBILE
            // « Appel en attente » n'est pas un type d'appel : c'est la façon dont l'appel a été pris.
            "attente" in s -> null
            "terrebonne" in s -> TERREBONNE
            "cisss" in s -> CISSS
            "verrouillage" in s -> DEVERROUILLAGE
            "survoltage" in s -> SURVOLTAGE
            "dépanneur" in s || "depanneur" in s -> DEPANNEUR
            "station-van" in s || s == "van" -> STATION_VAN
            "anima" in s -> ANIMAUX
            "fournisseur" in s || "coop" in s -> FOURNISSEUR
            "tête" in s || "tete" in s -> DEUX_TETES
            "livraison" in s -> LIVRAISON
            REG.matches(s) -> null // régulier : décidé plus bas
            else -> label.trim().replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * @param service le texte entre crochets (ou la colonne service_type), éventuellement plusieurs séparés par des virgules
     * @param status current_status (sans effet sur le type : un appel en attente garde son type)
     */
    fun of(service: String?, status: String?): Set<String> {
        val out = LinkedHashSet<String>()
        service.orEmpty().split(',').forEach { part -> one(part)?.let(out::add) }
        if (out.isEmpty()) out.add(REGULIER)
        return out
    }

    /** Types présents dans les appels, du plus fréquent au moins fréquent. */
    fun present(calls: List<Call>): List<String> {
        val counts = HashMap<String, Int>()
        calls.forEach { c -> c.types.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { KNOWN.indexOf(it.key).let { i -> if (i < 0) 99 else i } })
            .map { it.key }
    }
}
