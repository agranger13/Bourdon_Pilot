package com.drone.appcontrolleur.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drone.appcontrolleur.link.ProbeResult
import com.drone.appcontrolleur.link.ProtocolType
import com.drone.appcontrolleur.link.WifiState
import com.drone.appcontrolleur.protocol.DroneMessage

/** Écran de validation de l'étape 4 : connexion au Wi-Fi du drone et test UDP. */
@Composable
fun ConnectionScreen(vm: ConnectionViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val wifi by vm.wifiState.collectAsState()
    val probing by vm.probing.collectAsState()
    val probeResult by vm.probeResult.collectAsState()
    val context = LocalContext.current

    // Permission à valider sur le téléphone (§6.3) : on tente la connexion même si elle est refusée
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.connect()
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Paramètres", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = settings.ssid,
                onValueChange = { v -> vm.updateSettings { it.copy(ssid = v) } },
                label = { Text("SSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.password,
                onValueChange = { v -> vm.updateSettings { it.copy(password = v) } },
                label = { Text("Mot de passe") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = settings.host,
                    onValueChange = { v -> vm.updateSettings { it.copy(host = v.trim()) } },
                    label = { Text("IP du drone") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = settings.port.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { p -> vm.updateSettings { it.copy(port = p) } } },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProtocolType.entries.forEach { type ->
                    FilterChip(
                        selected = settings.protocol == type,
                        onClick = { vm.updateSettings { it.copy(protocol = type) } },
                        label = { Text(type.label) },
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Liaison", style = MaterialTheme.typography.titleMedium)
            val (label, color) = wifi.describe()
            Text(label, color = color, style = MaterialTheme.typography.bodyLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val permission = wifiPermission()
                    if (context.hasPermission(permission)) vm.connect() else permissionLauncher.launch(permission)
                }) { Text("Connexion auto") }
                OutlinedButton(onClick = vm::disconnect) { Text("Déconnecter") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { context.openWifiSettings() }) { Text("Réglages Wi-Fi") }
                OutlinedButton(onClick = vm::useCurrentWifi) { Text("Utiliser le Wi-Fi actuel") }
            }

            Button(onClick = vm::probe, enabled = !probing) {
                Text(if (probing) "Test en cours…" else "Tester le drone")
            }
            probeResult?.let { Text(it.describe()) }
            if (settings.protocol == ProtocolType.LEGACY_CSV) {
                Text(
                    "Legacy CSV : tests sans hélices uniquement",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun wifiPermission() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES
    else Manifest.permission.ACCESS_FINE_LOCATION

private fun Context.hasPermission(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openWifiSettings() =
    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun WifiState.describe(): Pair<String, Color> = when (this) {
    WifiState.Idle -> "Déconnecté" to Color.Gray
    is WifiState.Searching -> "Recherche de ${ssid ?: "Wi-Fi"}…" to Color(0xFFFFB300)
    is WifiState.Connected -> "Connecté au Wi-Fi du drone" to Color(0xFF66BB6A)
    WifiState.Lost -> "Wi-Fi perdu, reconnexion en attente" to Color(0xFFFF7043)
    WifiState.Unavailable -> "Drone introuvable ou connexion refusée" to Color(0xFFE53935)
}

private fun ProbeResult.describe(): String = when (this) {
    is ProbeResult.Sent -> "Envoyé « $payload » : vérifier les logs série du drone"
    ProbeResult.NoReply -> "Aucune réponse en 1 s"
    is ProbeResult.Failed -> "Échec : $reason"
    is ProbeResult.Reply -> when (val m = message) {
        is DroneMessage.HelloAck ->
            "HELLO_ACK en $rttMs ms : firmware ${m.fwMajor}.${m.fwMinor}.${m.fwPatch}, protocole v${m.protoVersion}, carte ${m.boardId}"
        is DroneMessage.Telemetry -> "Télémétrie reçue en $rttMs ms : état ${m.state}"
    }
}
