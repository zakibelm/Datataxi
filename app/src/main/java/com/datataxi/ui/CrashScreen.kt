package com.datataxi.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Affiché au lancement qui suit un plantage, avant tout le reste. */
@Composable
fun CrashScreen(report: String, onContinue: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("DataTaxi s'est arrêtée", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Au dernier lancement, l'application a planté. Copie ou partage ce rapport pour qu'on corrige l'erreur. Ton journal n'a pas été touché.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copier") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Plantage DataTaxi")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(send, "Partager le rapport").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Partager") }
            OutlinedButton(onClick = onContinue) { Text("Relancer l'application") }
        }
        SelectionContainer {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}
