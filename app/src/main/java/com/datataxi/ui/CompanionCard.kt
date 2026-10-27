package com.datataxi.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
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
    var voice by remember { mutableStateOf(CompanionSettings.voiceMode(context)) }
    var provider by remember { mutableStateOf(CompanionSettings.provider(context)) }
    var model by remember { mutableStateOf(CompanionSettings.model(context)) }
    var info by remember { mutableStateOf("") }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        info = if (ok) "Micro autorisé. Touche l'avatar et parle." else "Micro refusé : la voix ne marchera pas."
        // Relance le service pour qu'il prenne le type « microphone » en arrière-plan.
        if (Settings.canDrawOverlays(context)) { CompanionService.stop(context); CompanionService.start(context) }
    }
    var waitingPermission by remember { mutableStateOf(false) }

    // Au retour des réglages, si l'autorisation est donnée, l'avatar démarre tout seul.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && waitingPermission) {
                if (Settings.canDrawOverlays(context)) {
                    waitingPermission = false; CompanionService.start(context)
                    info = "Autorisation reçue : l'avatar est affiché (cherche-le sur le bord gauche de l'écran)."
                } else info = "L'autorisation « Afficher par-dessus les autres applications » n'est pas encore donnée."
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun save() { CompanionSettings.save(context, url, token, speak, mask); CompanionSettings.saveProvider(context, provider, model) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Avatar flottant (IA)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Une bulle reste à l'écran. Touche-la pour poser une question sur ce que tu vois. " +
                    "Les réponses viennent de Claude Code sur ton serveur (relay/relay.js).",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { provider = "omniroute" }, enabled = provider != "omniroute") { Text("OmniRoute") }
                Button(onClick = { provider = "relay" }, enabled = provider != "relay") { Text("Claude Code (VPS)") }
            }
            Text(
                if (provider == "omniroute") "Fournisseur : OmniRoute (compatible OpenAI). Adresse = ton OmniRoute en https, jeton = sa clé API."
                else "Fournisseur : relais Claude Code sur le VPS (plan Claude).",
                style = MaterialTheme.typography.bodySmall,
            )
            if (provider == "omniroute") {
                OutlinedTextField(model, { model = it }, label = { Text("Modèle (gratuit ou local, ex. nom exact dans OmniRoute)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(url, { url = it }, label = { Text("Adresse (https://…)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it }, label = { Text("Jeton") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(voice, { voice = it; CompanionSettings.setVoiceMode(context, it) })
                Text("Mode voix mains libres (touche = il écoute, il répond, il réécoute)")
            }
            OutlinedButton(onClick = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text("Autoriser le micro") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(speak, { speak = it }); Text("Lire les réponses à voix haute (mode texte)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(mask, { mask = it }); Text("Masquer téléphones et courriels avant l'envoi")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    save()
                    if (!Settings.canDrawOverlays(context)) {
                        waitingPermission = true
                        info = "Active « Afficher par-dessus les autres applications » pour DataTaxi, puis reviens ici."
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        CompanionService.start(context); info = "Avatar affiché sur le bord gauche de l'écran, sous le haut."
                    }
                }) { Text("Afficher l'avatar") }
                OutlinedButton(onClick = { CompanionService.stop(context); info = "Avatar masqué." }) { Text("Masquer") }
            }
            if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodySmall)
        }
    }
}
