package com.datataxi.companion

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Un fournisseur d'IA. D'autres (OpenRouter, clé API…) pourront remplacer le relais sans toucher à l'avatar. */
interface ChatProvider {
    /** Appel bloquant : à lancer hors du fil principal. */
    fun ask(question: String, screenApp: String, screenLines: List<String>): Result<String>
}

/** Parle à `relay/relay.js` sur le VPS, qui interroge Claude Code connecté au plan de l'utilisateur. */
class RelayProvider(private val url: String, private val token: String) : ChatProvider {
    override fun ask(question: String, screenApp: String, screenLines: List<String>): Result<String> = runCatching {
        require(url.startsWith("https://")) { "L'adresse du relais doit commencer par https://" }
        require(token.isNotBlank()) { "Jeton manquant" }
        val body = JSONObject()
            .put("question", question)
            .put("app", screenApp)
            .put("screen", JSONArray(screenLines))
            .toString()
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) error("Relais : erreur $code ${text.take(200)}")
            JSONObject(text).getString("answer")
        } finally {
            conn.disconnect()
        }
    }
}
