package com.drone.appcontrolleur.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drone.appcontrolleur.link.LinkSettings
import com.drone.appcontrolleur.link.ProbeResult
import com.drone.appcontrolleur.link.ProtocolType
import com.drone.appcontrolleur.link.WifiState
import com.drone.appcontrolleur.protocol.DroneMessage

/** Connexion au Wi-Fi du drone et test UDP (étape 4), habillé selon la maquette Claude Design (étape 8). */
@Composable
fun ConnectionScreen(
    onFly: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ConnectionViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsState()
    val wifi by vm.wifiState.collectAsState()
    val probing by vm.probing.collectAsState()
    val probeResult by vm.probeResult.collectAsState()
    val context = LocalContext.current

    // Permission à valider sur le téléphone (§6.3) : on tente la connexion même si elle est refusée
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.connect()
    }
    val connect = {
        val permission = wifiPermission()
        if (context.hasPermission(permission)) vm.connect() else permissionLauncher.launch(permission)
    }

    Box(Modifier.fillMaxSize().screenBackground()) {
        Row(Modifier.fillMaxSize()) {
            SearchIllustration(wifi, Modifier.weight(0.42f).fillMaxHeight())
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 40.dp, top = 44.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column {
                    Text("Connexion au drone", style = DroneType.display)
                    Text(
                        "Rejoignez le point d'accès du drone, puis vérifiez la réponse de l'ESP32.",
                        style = DroneType.body(12.sp, DroneColors.TextMuted, FontWeight.Normal),
                    )
                }

                GlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        StepRow(wifiStep(wifi, settings.ssid))
                        StepDivider()
                        StepRow(replyStep(probing, probeResult, settings)) {
                            DroneButton("TESTER", vm::probe, kind = ButtonKind.SECONDARY, enabled = !probing, height = 34.dp, fontSize = 11.sp)
                        }
                        StepDivider()
                        StepRow(telemetryStep(probeResult))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoCard("ADRESSE", onOpenSettings) {
                        Text("${settings.host}:${settings.port}", style = DroneType.mono(13.sp, DroneColors.TextSoft), maxLines = 1)
                    }
                    val legacy = settings.protocol == ProtocolType.LEGACY_CSV
                    val accent = if (legacy) DroneColors.Warn else DroneColors.Cyan
                    InfoCard("PROTOCOLE", onOpenSettings, accent) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                            Text(
                                if (legacy) "${settings.protocol.label} · sans hélices" else settings.protocol.label,
                                style = DroneType.body(13.sp, if (legacy) DroneColors.WarnLight else DroneColors.CyanLight, FontWeight.Bold),
                                maxLines = 1,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (wifi) {
                        is WifiState.Connected -> DroneButton("PILOTER", onFly, Modifier.weight(1f), fontSize = 16.sp)
                        is WifiState.Searching -> DroneButton("RECHERCHE…", {}, Modifier.weight(1f), enabled = false, fontSize = 16.sp)
                        else -> DroneButton("SE CONNECTER", connect, Modifier.weight(1f), fontSize = 16.sp)
                    }
                    DroneButton("CONNEXION MANUELLE", { context.openWifiSettings() }, Modifier.weight(1f), ButtonKind.SECONDARY, fontSize = 14.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Connexion manuelle : rejoindre ${settings.ssid} dans Android, puis",
                        style = DroneType.body(10.sp, DroneColors.TextFaint, FontWeight.Normal),
                        modifier = Modifier.weight(1f),
                    )
                    DroneButton("UTILISER LE WI-FI ACTUEL", vm::useCurrentWifi, kind = ButtonKind.SECONDARY, height = 34.dp, fontSize = 10.sp)
                    if (wifi != WifiState.Idle) {
                        DroneButton("DÉCONNECTER", vm::disconnect, kind = ButtonKind.SECONDARY, height = 34.dp, fontSize = 10.sp)
                    }
                }
            }
        }

        Brand(Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 14.dp))
        GearButton(onOpenSettings, Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 10.dp))
        LinkChip(wifi, Modifier.align(Alignment.BottomStart).padding(start = 18.dp, bottom = 14.dp))
    }
}

private enum class StepState { OK, INFO, RUNNING, ERROR, IDLE }

private data class Step(val state: StepState, val title: AnnotatedString, val detail: String, val status: String)

private fun wifiStep(wifi: WifiState, ssid: String): Step {
    fun titled(prefix: String, name: String?) = buildAnnotatedString {
        append(prefix)
        if (name != null) withStyle(SpanStyle(fontFamily = JetBrainsMono, color = DroneColors.CyanLight, fontSize = 12.sp)) { append(name) }
    }
    return when (wifi) {
        is WifiState.Connected -> Step(StepState.OK, titled("Connecté au Wi-Fi ", ssid), "Réseau du drone lié à l'app", "OK")
        is WifiState.Searching -> Step(
            StepState.RUNNING,
            if (wifi.ssid != null) titled("Connexion au Wi-Fi ", wifi.ssid) else AnnotatedString("Wi-Fi actuel du téléphone"),
            "Acceptez la fenêtre de connexion Android",
            "EN COURS",
        )
        WifiState.Lost -> Step(StepState.ERROR, AnnotatedString("Wi-Fi perdu"), "Android tente de se reconnecter", "PERDU")
        WifiState.Unavailable -> Step(StepState.ERROR, AnnotatedString("Drone introuvable"), "Hors de portée ou connexion refusée", "ÉCHEC")
        WifiState.Idle -> Step(StepState.IDLE, titled("Wi-Fi ", ssid), "Non connecté", "INACTIF")
    }
}

private fun replyStep(probing: Boolean, result: ProbeResult?, settings: LinkSettings): Step {
    val title = AnnotatedString("Le drone répond")
    val address = "${settings.host}:${settings.port}"
    if (probing) return Step(StepState.RUNNING, title, "Test sur $address", "EN COURS")
    return when (result) {
        null -> Step(StepState.IDLE, title, "Test non lancé · $address", "INACTIF")
        is ProbeResult.Sent -> Step(StepState.INFO, title, "Envoyé « ${result.payload} » : vérifier les logs série", "ENVOYÉ")
        ProbeResult.NoReply -> Step(StepState.ERROR, title, "Aucune réponse en 1 s sur $address", "ÉCHEC")
        is ProbeResult.Failed -> Step(StepState.ERROR, title, "Échec : ${result.reason}", "ÉCHEC")
        is ProbeResult.Reply -> when (val m = result.message) {
            is DroneMessage.HelloAck -> Step(
                StepState.OK, title,
                "HELLO_ACK en ${result.rttMs} ms · firmware ${m.fwMajor}.${m.fwMinor}.${m.fwPatch} · protocole v${m.protoVersion} · carte ${m.boardId}",
                "OK",
            )
            is DroneMessage.Telemetry -> Step(StepState.OK, title, "Réponse en ${result.rttMs} ms", "OK")
        }
    }
}

private fun telemetryStep(result: ProbeResult?): Step {
    val title = AnnotatedString("Télémétrie reçue")
    val telemetry = ((result as? ProbeResult.Reply)?.message as? DroneMessage.Telemetry)
    return if (telemetry != null) {
        Step(StepState.OK, title, "État ${telemetry.state}", "OK")
    } else {
        Step(StepState.IDLE, title, "En attente du premier paquet", "INACTIF")
    }
}

private fun StepState.color() = when (this) {
    StepState.OK -> DroneColors.Ok
    StepState.INFO, StepState.RUNNING -> DroneColors.Cyan
    StepState.ERROR -> DroneColors.Danger
    StepState.IDLE -> DroneColors.Idle
}

@Composable
private fun StepRow(step: Step, action: @Composable RowScope.() -> Unit = {}) {
    val idle = step.state == StepState.IDLE
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).alpha(if (idle) 0.55f else 1f), contentAlignment = Alignment.Center) {
            when (step.state) {
                StepState.OK -> MarkIcon("✓", DroneColors.Ok, 20.dp, filled = true)
                StepState.INFO -> MarkIcon("✓", DroneColors.Cyan, 20.dp)
                StepState.RUNNING -> Spinner(DroneColors.Cyan, Modifier.fillMaxSize())
                StepState.ERROR -> MarkIcon("✕", DroneColors.Danger, 20.dp)
                StepState.IDLE -> DashedCircle(Color(0x6678B4D2), Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f).alpha(if (idle) 0.7f else 1f)) {
            Text(step.title, style = DroneType.body(13.sp, if (idle) DroneColors.TextMuted else DroneColors.TextPrimary, FontWeight.SemiBold), maxLines = 1)
            Text(step.detail, style = DroneType.body(10.sp, DroneColors.TextMuted, FontWeight.Normal), maxLines = 1)
        }
        action()
        Text(step.status, style = DroneType.caps(9.sp, step.state.color(), tracking = 0.14f))
    }
}

@Composable
private fun StepDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x2478B4D2)))

@Composable
private fun RowScope.InfoCard(
    label: String,
    onClick: () -> Unit,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(accent?.copy(alpha = 0.08f) ?: Color(0x08FFFFFF))
            .border(1.dp, accent?.copy(alpha = 0.4f) ?: DroneColors.StrokeSoft, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = DroneType.caps(8.sp, DroneColors.TextFaint))
        content()
    }
}

@Composable
private fun Brand(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(22.dp).background(DroneColors.Cyan, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text("D", style = DroneType.caps(13.sp, Color(0xFF04222B), FontWeight.ExtraBold, tracking = 0f))
        }
        Text("DRONE RC", style = DroneType.caps(13.sp, DroneColors.TextSoft, FontWeight.Bold, tracking = 0.2f))
    }
}

@Composable
private fun LinkChip(wifi: WifiState, modifier: Modifier) {
    data class Chip(val color: Color, val light: Color, val title: String, val detail: String)
    val chip = when (wifi) {
        is WifiState.Connected -> Chip(DroneColors.Ok, DroneColors.OkLight, "Lien Wi-Fi établi", "Réseau du drone actif")
        is WifiState.Searching -> Chip(DroneColors.Cyan, DroneColors.CyanLight, "Recherche", "Connexion en cours")
        WifiState.Lost -> Chip(DroneColors.Danger, DroneColors.DangerLight, "Lien perdu", "Aucune réponse · nouvelle recherche")
        WifiState.Unavailable -> Chip(DroneColors.Danger, DroneColors.DangerLight, "Drone introuvable", "Nouvel essai possible")
        WifiState.Idle -> Chip(DroneColors.Idle, DroneColors.TextMuted, "Inactif", "Wi-Fi non connecté")
    }
    val shape = RoundedCornerShape(9.dp)
    val tinted = wifi != WifiState.Idle
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (tinted) chip.color.copy(alpha = 0.12f) else Color(0x08FFFFFF))
            .border(1.dp, if (tinted) chip.color.copy(alpha = 0.45f) else DroneColors.StrokeSoft, shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (wifi) {
            is WifiState.Connected -> SignalBars(4, chip.color)
            is WifiState.Searching -> Spinner(chip.color, Modifier.size(16.dp))
            WifiState.Lost, WifiState.Unavailable -> MarkIcon("✕", chip.color, 16.dp)
            WifiState.Idle -> DashedCircle(chip.color, Modifier.size(16.dp))
        }
        Column {
            Text(chip.title, style = DroneType.body(12.sp, chip.light, FontWeight.SemiBold))
            Text(chip.detail, style = DroneType.body(9.sp, DroneColors.TextMuted, FontWeight.Normal))
        }
    }
}

/** Ondes de recherche autour d'un quadricoptère stylisé. */
@Composable
private fun SearchIllustration(wifi: WifiState, modifier: Modifier) {
    val searching = wifi is WifiState.Searching
    val phase by rememberInfiniteTransition(label = "search").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ping",
    )
    val (caption, dot) = when (wifi) {
        is WifiState.Connected -> "LIEN WI-FI ÉTABLI" to DroneColors.Ok
        is WifiState.Searching -> "RECHERCHE DU DRONE…" to DroneColors.Cyan
        WifiState.Lost -> "WI-FI PERDU" to DroneColors.Danger
        WifiState.Unavailable -> "DRONE INTROUVABLE" to DroneColors.Danger
        WifiState.Idle -> "EN ATTENTE DE CONNEXION" to DroneColors.Idle
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(240.dp)) {
            val ringRadius = 100.dp.toPx()
            val ringColor = DroneColors.Cyan.copy(alpha = 0.45f)
            if (searching) {
                repeat(3) { i ->
                    val p = (phase + i / 3f) % 1f
                    drawCircle(
                        ringColor.copy(alpha = 0.45f * (1f - p)),
                        radius = ringRadius * (0.55f + 0.8f * p),
                        style = Stroke(1.dp.toPx()),
                    )
                }
            } else {
                drawCircle(ringColor.copy(alpha = 0.18f), radius = ringRadius * 0.95f, style = Stroke(1.dp.toPx()))
            }

            val s = 116.dp.toPx()
            val origin = center - Offset(s / 2, s / 2)
            val rotor = 0.28f * s
            val inset = 0.073f * s
            listOf(
                Offset(inset, inset) to 0.75f,
                Offset(s - inset - rotor, inset) to 0.75f,
                Offset(inset, s - inset - rotor) to 0.4f,
                Offset(s - inset - rotor, s - inset - rotor) to 0.4f,
            ).forEach { (topLeft, alpha) ->
                val c = origin + topLeft + Offset(rotor / 2, rotor / 2)
                drawCircle(DroneColors.Cyan.copy(alpha = 0.07f), rotor / 2, c)
                drawCircle(DroneColors.Cyan.copy(alpha = alpha), rotor / 2, c, style = Stroke(1.2.dp.toPx()))
            }
            val body = Size(0.373f * s, 0.24f * s)
            val bodyTopLeft = center - Offset(body.width / 2, body.height / 2)
            drawRoundRect(
                Brush.verticalGradient(listOf(DroneColors.Cyan.copy(alpha = 0.30f), Color(0xD90A3C4C)), startY = bodyTopLeft.y, endY = bodyTopLeft.y + body.height),
                topLeft = bodyTopLeft,
                size = body,
                cornerRadius = CornerRadius(0.06f * s),
            )
            drawRoundRect(Color(0x99A0F0FF), bodyTopLeft, body, CornerRadius(0.06f * s), style = Stroke(1.dp.toPx()))
            drawCircle(dot.copy(alpha = 0.35f), 0.04f * s, center)
            drawCircle(dot, 0.02f * s, center)
        }
        Text(caption, style = DroneType.caps(10.sp, DroneColors.TextFaint, tracking = 0.22f))
    }
}

private fun wifiPermission() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES
    else Manifest.permission.ACCESS_FINE_LOCATION

private fun Context.hasPermission(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openWifiSettings() =
    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
