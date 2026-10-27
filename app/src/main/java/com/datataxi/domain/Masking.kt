package com.datataxi.domain

/** Masque les données personnelles reconnaissables avant tout envoi hors de la tablette (Loi 25). */
object Masking {
    private val email = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
    private val phone = Regex("(?<!\\d)(\\+?1[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}(?!\\d)")
    private val longDigits = Regex("(?<!\\d)\\d{6,}(?!\\d)")

    fun mask(text: String): String = text
        .replace(email, "[courriel]")
        .replace(phone, "[téléphone]")
        .replace(longDigits, "[numéro]")
}
