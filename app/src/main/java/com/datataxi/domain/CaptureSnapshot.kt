package com.datataxi.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedLine(
    val depth: Int,
    val viewId: String?,
    val className: String?,
    val text: String,
)

data class CaptureSnapshot(
    val packageName: String,
    val capturedAtMillis: Long,
    val lines: List<CapturedLine>,
) {
    /** Texte brut partageable : une ligne par élément visible, avec son identifiant de vue. */
    fun toPlainText(maxLines: Int = Int.MAX_VALUE): String = buildString {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CANADA)
            .format(Date(capturedAtMillis))
        appendLine("DataTaxi - capture de $packageName")
        appendLine("Heure : $stamp")
        appendLine("Éléments lus : ${lines.size}")
        appendLine()
        lines.take(maxLines).forEach { line ->
            val indent = "  ".repeat(line.depth.coerceAtMost(10))
            appendLine("$indent${line.text}   [${line.viewId ?: "-"}]")
        }
    }
}
