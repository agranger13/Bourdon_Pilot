package com.bourdon.pilot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bourdon.pilot.control.ControlState
import com.bourdon.pilot.link.DroneLink
import com.bourdon.pilot.link.DronePhase
import com.bourdon.pilot.link.LinkStats
import com.bourdon.pilot.link.ProtocolType
import com.bourdon.pilot.link.WifiState
import com.bourdon.pilot.protocol.FlightState
import com.bourdon.pilot.ui.joystick.Joystick
import com.bourdon.pilot.ui.joystick.JoystickMath
import kotlin.math.roundToInt

/** Écran de pilotage Mode 2 (étape 6), habillé selon la maquette Claude Design (étape 8). */
@Composable
fun PilotScreen(
    onOpenConnection: () -> Unit,
    onLeaveFlight: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: PilotViewModel = viewModel(),
) {
    val control by vm.control.collectAsState()
    val wifi by vm.wifiState.collectAsState()
    val settings by vm.settings.collectAsState()
    val stats by vm.stats.collectAsState()
    val sending by vm.sending.collectAsState()
    val message by vm.message.collectAsState()
    val drone by vm.drone.collectAsState()

    // F-SEC-4 / NF-7 : émission uniquement écran visible et app au premier plan
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.setActive(true)
                Lifecycle.Event.ON_STOP -> vm.setActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.setActive(false)
        }
    }

    // Un retour arrière accidentel quitterait l'écran et couperait les moteurs en vol
    BackHandler(enabled = control.armed) {}

    // Binaire v1 : il faut que le drone réponde (HELLO_ACK ou télémétrie)
    val droneReady = drone.phase == DronePhase.ACTIVE || drone.phase == DronePhase.UNCONFIRMED
    val canArm = sending && droneReady && !control.estop && control.throttle <= ControlState.ARM_THROTTLE_MAX
    val armMode = when {
        control.estop -> ArmMode.ESTOP
        control.armed -> ArmMode.ARMED
        canArm -> ArmMode.READY
        else -> ArmMode.UNAVAILABLE
    }
    val armHint = when (armMode) {
        ArmMode.ESTOP -> "Appuyer pour déverrouiller"
        ArmMode.ARMED -> "Moteurs actifs"
        ArmMode.READY -> "Maintenir pour armer"
        ArmMode.UNAVAILABLE -> when {
            !sending -> "Lien requis"
            drone.phase == DronePhase.HANDSHAKE -> "Attente du drone"
            drone.phase == DronePhase.NO_RESPONSE -> "Le drone ne répond pas"
            drone.phase == DronePhase.INCOMPATIBLE -> "Protocole incompatible"
            else -> "Gaz à zéro requis"
        }
    }

    Box(Modifier.fillMaxSize().screenBackground()) {
        val telemetry = drone.telemetry
        AttitudeHorizon(
            rollDeg = telemetry?.rollDeg ?: (control.roll * MAX_ANGLE_DEG),
            pitchDeg = telemetry?.pitchDeg ?: (control.pitch * MAX_ANGLE_DEG),
            measured = telemetry != null,
            modifier = Modifier.align(Alignment.Center),
        )
        if (control.armed) ArmedFrame()

        LinkPanel(wifi, sending, stats, drone, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp))
        StatusPanel(
            protocol = settings.protocol,
            drone = drone,
            settingsEnabled = !control.armed,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 10.dp),
        )
        if (settings.protocol == ProtocolType.LEGACY_CSV) {
            LegacyBanner(Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 66.dp))
        }

        EmergencyStopButton(vm::emergencyStop, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        val badgeModifier = Modifier.align(Alignment.TopCenter).padding(top = 104.dp)
        when {
            control.estop -> Pill("E-STOP VERROUILLÉ", DroneColors.Warn, badgeModifier, DroneColors.WarnLight)
            // F-TEL-1 : « ARMÉ » seulement une fois confirmé par la télémétrie
            control.armed -> when (drone.telemetry?.state) {
                FlightState.ARMED -> Pill("ARMÉ", DroneColors.Danger, badgeModifier, DroneColors.DangerLight)
                null -> Pill("ARMÉ · NON CONFIRMÉ", DroneColors.Danger, badgeModifier, DroneColors.DangerLight)
                else -> Pill("ARMEMENT…", DroneColors.Warn, badgeModifier, DroneColors.WarnLight)
            }
        }

        var dismissed by remember(message) { mutableStateOf(false) }
        val alert = message
        if (alert != null && !dismissed) {
            AlertToast(alert, onDismiss = { dismissed = true }, Modifier.align(Alignment.TopCenter).padding(top = 140.dp))
        }

        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 18.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Mode 2 : gaz (sans retour) et lacet à gauche
                StickPad(
                    value = Offset(control.yaw, JoystickMath.throttleToY(control.throttle)),
                    onValueChange = { vm.onLeftStick(yaw = it.x, throttle = JoystickMath.throttle(it.y)) },
                    topLabel = "GAZ ▲",
                    bottomLabel = "LACET ▶",
                    returnY = false,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Readout("T", "${(control.throttle * 100).roundToInt()}%", DroneColors.Cyan)
                    Readout("Y", signedPercent(control.yaw))
                }
            }
            ThrottleGauge(control.throttle, Modifier.padding(bottom = 26.dp))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Tangage et roulis à droite, retour au centre
            StickPad(
                value = Offset(control.roll, control.pitch),
                onValueChange = { vm.onRightStick(roll = it.x, pitch = it.y) },
                topLabel = "TANGAGE ▲",
                bottomLabel = "ROULIS ▶",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Readout("P", signedPercent(control.pitch))
                Readout("R", signedPercent(control.roll))
            }
        }

        ArmButton(
            mode = armMode,
            hint = armHint,
            onArm = vm::arm,
            onDisarm = vm::disarm,
            onResetEstop = vm::resetEmergencyStop,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )

        // Sans réseau du drone, rien n'est émis : l'écran est masqué jusqu'au retour du lien
        if (wifi !is WifiState.Connected) NoLinkOverlay(wifi, onOpenConnection, onLeaveFlight)
    }
}

private data class LinkView(val color: Color, val text: String, val bars: Int)

/** F-TEL-4 : barres sur le RSSI mesuré par le drone quand il est connu. */
private fun rssiBars(rssiDbm: Int) = when {
    rssiDbm >= -55 -> 4
    rssiDbm >= -67 -> 3
    rssiDbm >= -75 -> 2
    else -> 1
}

private fun linkView(wifi: WifiState, sending: Boolean, stats: LinkStats, drone: DroneLink) = when {
    sending && drone.phase == DronePhase.HANDSHAKE -> LinkView(DroneColors.Cyan, "Attente HELLO…", 2)
    sending && drone.phase == DronePhase.NO_RESPONSE -> LinkView(DroneColors.Danger, "Drone muet", 1)
    sending && drone.phase == DronePhase.INCOMPATIBLE -> LinkView(DroneColors.Danger, "Incompatible", 0)
    sending && stats.sendErrors > 0 -> LinkView(DroneColors.Warn, "Lien dégradé", 2)
    sending -> {
        val rssi = drone.telemetry?.rssiDbm ?: 0
        LinkView(DroneColors.Ok, "Connecté", if (rssi != 0) rssiBars(rssi) else 4)
    }
    wifi is WifiState.Connected -> LinkView(DroneColors.Cyan, "Démarrage…", 3)
    wifi is WifiState.Searching -> LinkView(DroneColors.Warn, "Recherche…", 1)
    wifi is WifiState.Lost -> LinkView(DroneColors.Danger, "Lien perdu", 0)
    else -> LinkView(DroneColors.Idle, "Non connecté", 0)
}

@Composable
private fun LinkPanel(wifi: WifiState, sending: Boolean, stats: LinkStats, drone: DroneLink, modifier: Modifier) {
    val link = linkView(wifi, sending, stats, drone)
    GlassPanel(modifier.height(48.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp).align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(link.color)
            Column {
                Text("LIEN", style = DroneType.caps(8.sp, tracking = 0.14f))
                Text(link.text, style = DroneType.body(12.sp, link.color, FontWeight.SemiBold), maxLines = 1)
            }
            PanelDivider()
            SignalBars(link.bars, link.color)
            val rssi = drone.telemetry?.rssiDbm ?: 0
            Column {
                Text(
                    if (sending) "gigue ${"%.1f".format(stats.maxJitterMs)} ms" else "gigue —",
                    style = DroneType.mono(10.sp, link.color),
                )
                Text(
                    if (rssi != 0) "$rssi dBm" else "erreurs ${if (sending) stats.sendErrors else "—"}",
                    style = DroneType.mono(8.sp, DroneColors.TextMuted, FontWeight.Normal),
                )
            }
            PanelDivider()
            Column {
                Text("ÉMISSION", style = DroneType.caps(8.sp, tracking = 0.14f))
                Text(
                    if (sending) "${stats.rateHz} Hz" else "—",
                    style = DroneType.mono(11.sp, if (sending) DroneColors.Cyan else DroneColors.Idle),
                )
                val loss = stats.lossPercent
                Text(
                    if (loss != null) "perte ${"%.1f".format(loss)} %" else "perte —",
                    style = DroneType.mono(
                        8.sp,
                        if (loss != null && loss > LOSS_WARN_PERCENT) DroneColors.Warn else DroneColors.TextMuted,
                        FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(protocol: ProtocolType, drone: DroneLink, settingsEnabled: Boolean, onOpenSettings: () -> Unit, modifier: Modifier) {
    GlassPanel(modifier.height(48.dp)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp).align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // F-TEL-2 : masquée tant que le drone ne mesure pas la tension (vbatMv = 0)
            val battery = batteryView(drone)
            BatteryGauge(battery.fraction, battery.color)
            Column {
                Text(battery.text, style = DroneType.mono(13.sp, battery.color))
                Text(battery.label, style = DroneType.caps(7.sp, tracking = 0.14f))
            }
            PanelDivider()
            val (stateText, stateColor) = droneStateView(drone)
            Column {
                Text("DRONE", style = DroneType.caps(8.sp, tracking = 0.14f))
                Text(stateText, style = DroneType.mono(11.sp, stateColor), maxLines = 1)
                val loopHz = drone.telemetry?.loopHz
                Text(
                    if (loopHz != null && loopHz > 0) "boucle $loopHz Hz" else "boucle —",
                    style = DroneType.mono(8.sp, DroneColors.TextMuted, FontWeight.Normal),
                )
            }
            PanelDivider()
            when (protocol) {
                ProtocolType.BINARY_V1 -> Pill(protocol.label, DroneColors.Cyan)
                ProtocolType.LEGACY_CSV -> Pill(protocol.label, DroneColors.Warn)
            }
            GearButton(onOpenSettings, enabled = settingsEnabled)
        }
    }
}

/** État confirmé par le drone (F-TEL-1). */
private fun droneStateView(drone: DroneLink): Pair<String, Color> {
    val telemetry = drone.telemetry
    return when {
        drone.phase == DronePhase.IDLE -> "—" to DroneColors.Idle
        drone.phase == DronePhase.UNCONFIRMED -> "SANS RETOUR" to DroneColors.Idle
        drone.phase == DronePhase.HANDSHAKE -> "HELLO…" to DroneColors.Cyan
        drone.phase == DronePhase.INCOMPATIBLE -> "INCOMPATIBLE" to DroneColors.Danger
        drone.phase == DronePhase.NO_RESPONSE -> "MUET" to DroneColors.Danger
        telemetry == null -> "NON CONFIRMÉ" to DroneColors.TextMuted
        else -> when (telemetry.state) {
            FlightState.DISARMED -> "DÉSARMÉ" to DroneColors.Ok
            FlightState.ARMED -> "ARMÉ" to DroneColors.Danger
            FlightState.FAILSAFE -> "FAILSAFE" to DroneColors.Warn
            FlightState.ESTOP_LATCH -> "E-STOP" to DroneColors.Warn
            FlightState.UNKNOWN -> "INCONNU" to DroneColors.Warn
        }
    }
}

private data class BatteryView(val fraction: Float, val color: Color, val text: String, val label: String)

/** Tension d'un LiPo 1S : 3,30 V critique, 3,50 V basse, 4,20 V pleine (SPEC_FIRMWARE §7.2). */
private fun batteryView(drone: DroneLink): BatteryView {
    val telemetry = drone.telemetry
    if (telemetry == null || telemetry.vbatMv == 0) return BatteryView(0f, DroneColors.Idle, "—", "NON MESURÉE")
    val volts = telemetry.vbatMv / 1000f
    val color = when {
        telemetry.batteryCritical -> DroneColors.Danger
        telemetry.batteryLow -> DroneColors.Warn
        else -> DroneColors.Ok
    }
    val label = when {
        telemetry.batteryCritical -> "CRITIQUE"
        telemetry.batteryLow -> "BASSE"
        else -> "BATTERIE"
    }
    return BatteryView(((volts - 3.30f) / 0.90f).coerceIn(0f, 1f), color, "%.2f V".format(volts), label)
}

@Composable
private fun BatteryGauge(fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(30.dp).height(14.dp)
                .border(1.dp, color, RoundedCornerShape(3.dp))
                .padding(2.dp)
                .drawBehind { drawRect(color, size = size.copy(width = size.width * fraction)) },
        )
        Box(Modifier.width(2.dp).height(5.dp).background(color, RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp)))
    }
}

@Composable
private fun LegacyBanner(modifier: Modifier) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(DroneColors.Warn.copy(alpha = 0.14f))
            .border(1.dp, DroneColors.Warn.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(14.dp).background(DroneColors.Warn, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", style = DroneType.body(10.sp, Color(0xFF2A1A00), FontWeight.Bold))
        }
        Text("Mode Legacy CSV : tests SANS hélices uniquement", style = DroneType.body(11.sp, DroneColors.WarnLight, FontWeight.SemiBold))
    }
}

@Composable
private fun AlertToast(message: String, onDismiss: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xF0160E10))
            .border(1.dp, DroneColors.Danger.copy(alpha = 0.5f), shape)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MarkIcon("✕", DroneColors.Danger, 20.dp)
        Column {
            Text(message.substringBefore(" : ", "Alerte").uppercase(), style = DroneType.caps(8.sp, DroneColors.DangerLight))
            Text(
                message.substringAfter(" : ").replaceFirstChar { it.uppercase() },
                style = DroneType.body(12.sp, Color(0xFFF4E9EA)),
            )
        }
        DroneButton("OK", onDismiss, kind = ButtonKind.SECONDARY, height = 36.dp, fontSize = 11.sp)
    }
}

@Composable
private fun StickPad(
    value: Offset,
    onValueChange: (Offset) -> Unit,
    topLabel: String,
    bottomLabel: String,
    returnY: Boolean = true,
) {
    Box(Modifier.size(180.dp)) {
        Joystick(value, onValueChange, Modifier.fillMaxSize(), returnY = returnY)
        val labelStyle = DroneType.caps(8.sp, DroneColors.TextFaint)
        Text(topLabel, style = labelStyle, modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 6.dp))
        Text(bottomLabel, style = labelStyle, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 6.dp))
    }
}

@Composable
private fun Readout(letter: String, value: String, valueColor: Color = DroneColors.TextPrimary) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(DroneColors.Glass)
            .border(1.dp, DroneColors.StrokeSoft, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(letter, style = DroneType.caps(8.sp, tracking = 0.14f))
        // Largeur fixe : les chiffres qui changent ne font pas bouger le reste
        Text(value, style = DroneType.mono(12.sp, valueColor), modifier = Modifier.width(38.dp), maxLines = 1)
    }
}

@Composable
private fun ThrottleGauge(throttle: Float, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val shape = RoundedCornerShape(7.dp)
        Box(
            Modifier.width(24.dp).height(166.dp)
                .clip(shape)
                .background(Color(0xB30A1422))
                .border(1.dp, DroneColors.StrokeHud, shape)
                .drawBehind {
                    val step = size.height / 12
                    for (i in 1 until 12) {
                        drawLine(Color(0x4D78B4D2), Offset(0f, i * step), Offset(size.width, i * step))
                    }
                    val fillHeight = size.height * throttle
                    drawRect(
                        Brush.verticalGradient(listOf(DroneColors.Cyan, DroneColors.CyanDim), startY = size.height - fillHeight, endY = size.height),
                        topLeft = Offset(0f, size.height - fillHeight),
                        size = Size(size.width, fillHeight),
                    )
                },
        )
        Text("GAZ", style = DroneType.caps(8.sp, DroneColors.TextFaint))
    }
}

/**
 * Horizon artificiel (F-TEL-3) : attitude mesurée dès que la télémétrie arrive, sinon consignes.
 */
@Composable
private fun AttitudeHorizon(rollDeg: Float, pitchDeg: Float, measured: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // Traits seuls, sans aplats dégradés qui brouillaient le centre de l'écran
        Canvas(Modifier.size(400.dp).alpha(0.22f)) {
            val r = size.minDimension / 2
            val pitchShift = (pitchDeg / MAX_ANGLE_DEG).coerceIn(-1.5f, 1.5f) * 36.dp.toPx()
            clipPath(Path().apply { addOval(Rect(center, r)) }) {
                withTransform({
                    rotate(-rollDeg, center)
                    translate(top = pitchShift)
                }) {
                    val ext = r * 1.5f
                    drawLine(DroneColors.Cyan, Offset(center.x - ext, center.y), Offset(center.x + ext, center.y), strokeWidth = 1.5.dp.toPx())
                    listOf(-0.72f to 0.32f, -0.36f to 0.19f, 0.36f to 0.19f, 0.72f to 0.32f).forEach { (dy, halfWidth) ->
                        val y = center.y + dy * r
                        drawLine(DroneColors.CyanLight, Offset(center.x - halfWidth * r, y), Offset(center.x + halfWidth * r, y), strokeWidth = 1.dp.toPx())
                    }
                }
            }
        }
        // Contour hors du calque translucide : la bulle garde un bord net
        Canvas(Modifier.size(400.dp)) {
            val w = 1.5.dp.toPx()
            drawCircle(DroneColors.Cyan.copy(alpha = 0.4f), size.minDimension / 2 - w / 2, style = Stroke(w))
        }
        // Attitude mesurée : valeurs en degrés, pour lever toute ambiguïté de signe
        if (measured) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Readout("R°", signedDegrees(rollDeg), DroneColors.CyanLight)
                Readout("P°", signedDegrees(pitchDeg), DroneColors.CyanLight)
            }
        }
        // Réticule fixe
        Canvas(Modifier.width(116.dp).height(8.dp).alpha(0.5f)) {
            val line = 42.dp.toPx()
            val w = 1.dp.toPx()
            drawLine(DroneColors.Cyan, Offset(0f, center.y), Offset(line, center.y), w)
            drawLine(DroneColors.Cyan, Offset(size.width - line, center.y), Offset(size.width, center.y), w)
            drawCircle(DroneColors.Cyan, 3.dp.toPx(), style = Stroke(w))
        }
    }
}

/** Bordure rouge pulsante tant que le drone est armé. */
@Composable
private fun ArmedFrame() {
    val pulse by rememberInfiniteTransition(label = "armed").animateFloat(
        initialValue = 0.95f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "armedPulse",
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = 2.dp.toPx()
        drawRect(DroneColors.Danger.copy(alpha = pulse), topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w))
        val glow = DroneColors.Danger.copy(alpha = 0.10f + (0.95f - pulse) * 0.1f)
        val g = 14.dp.toPx()
        drawRect(Brush.horizontalGradient(listOf(glow, Color.Transparent), endX = g), size = Size(g, size.height))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, glow), startX = size.width - g, endX = size.width), topLeft = Offset(size.width - g, 0f), size = Size(g, size.height))
        drawRect(Brush.verticalGradient(listOf(glow, Color.Transparent), endY = g), size = Size(size.width, g))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = size.height - g, endY = size.height), topLeft = Offset(0f, size.height - g), size = Size(size.width, g))
    }
}

@Composable
private fun NoLinkOverlay(wifi: WifiState, onOpenConnection: () -> Unit, onLeaveFlight: () -> Unit) {
    val lost = wifi is WifiState.Lost
    val (title, detail) = when (wifi) {
        WifiState.Lost -> "Lien perdu : moteurs coupés" to "Reconnexion en cours… aucun réarmement automatique"
        is WifiState.Searching -> "Recherche du drone…" to "Acceptez la fenêtre de connexion Android"
        WifiState.Unavailable -> "Drone introuvable" to "Vérifiez que le drone est allumé et à portée"
        else -> "Pas de liaison" to "Connectez-vous au Wi-Fi du drone pour piloter"
    }
    val color = when (wifi) {
        WifiState.Lost, WifiState.Unavailable -> DroneColors.Danger
        is WifiState.Searching -> DroneColors.Cyan
        else -> DroneColors.TextMuted
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xDB04070D))
            // Absorbe les appuis : rien sous le voile ne doit réagir
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (lost || wifi is WifiState.Searching) Spinner(color, Modifier.fillMaxSize(), strokeWidth = 2.5.dp)
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.5.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", style = DroneType.body(16.sp, color, FontWeight.Bold))
            }
        }
        Text(title, style = DroneType.display.copy(color = if (color == DroneColors.Danger) Color(0xFFFF6B74) else DroneColors.TextPrimary))
        Text(detail, style = DroneType.body(13.sp, Color(0xFFBCD0DE)))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DroneButton(if (lost || wifi == WifiState.Unavailable) "RECONNECTER" else "CONNEXION", onOpenConnection)
            DroneButton("QUITTER LE VOL", onLeaveFlight, kind = ButtonKind.SECONDARY)
        }
    }
}

/** Angle max des consignes, côté firmware (MAX_ROLL_ANGLE). */
private const val MAX_ANGLE_DEG = 30f

/** Au-delà, la perte de commandes est signalée en orange. */
private const val LOSS_WARN_PERCENT = 5f

private fun signedDegrees(deg: Float) = deg.roundToInt().let { if (it > 0) "+$it°" else "$it°" }

private fun signedPercent(value: Float) = (value * 100).roundToInt().let { if (it > 0) "+$it%" else "$it%" }
