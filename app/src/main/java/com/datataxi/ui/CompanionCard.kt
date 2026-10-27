package com.datataxi.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.datataxi.companion.CompanionService
import com.datataxi.companion.CompanionSettings

@Composable
fun CompanionCard() {
    val context = LocalContext.current
    var url by remember { mutableStateOf(CompanionSettings.relayUrl(context)) }
    var token by remember { mutableStateOf(CompanionSettings.token(context)) }
    var speak by remember { mutableStateOf(CompanionSettings.speak(context)) }
    var mask by remember { mutableStateOf(CompanionSettings.maskData(context)) }
    var info by remember { mutableStateOf("") }

    fun save() = CompanionSettings.save(context, url, token, speak, mask)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Avatar flottant (IA)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Une bulle reste à l'écran. Touche-la pour poser une question sur ce que tu vois. " +
                    "Les réponses viennent de Claude Code sur ton serveur (relay/relay.js).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(url, { url = it }, label = { Text("Adresse du relais (https://…)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Jeton") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(speak, { speak = it }); Text("Lire les réponses à voix haute")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(mask, { mask = it }); Text("Masquer téléphones et courriels avant l'envoi")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    save()
                    if (!Settings.canDrawOverlays(context)) {
                        info = "Autorise « Afficher par-dessus les autres applications », puis reviens."
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        CompanionService.start(context); info = "Avatar affiché."
                    }
                }) { Text("Afficher l'avatar") }
                OutlinedButton(onClick = { CompanionService.stop(context); info = "Avatar masqué." }) { Text("Masquer") }
            }
            if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall)
        }
    }
}
