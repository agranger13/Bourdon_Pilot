package com.drone.appcontrolleur.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.drone.appcontrolleur.control.ControlState
import com.drone.appcontrolleur.link.LinkSettings
import com.drone.appcontrolleur.link.LinkStats
import com.drone.appcontrolleur.link.ProtocolType
import com.drone.appcontrolleur.link.WifiState
import com.drone.appcontrolleur.ui.joystick.Joystick
import com.drone.appcontrolleur.ui.joystick.JoystickMath
import kotlinx.coroutines.launch

/** Écran de pilotage Mode 2 (étape 6). */
@Composable
fun PilotScreen(vm: PilotViewModel = viewModel()) {
    val control by vm.control.collectAsState()
    val wifi by vm.wifiState.collectAsState()
    val settings by vm.settings.collectAsState()
    val stats by vm.stats.collectAsState()
    val sending by vm.sending.collectAsState()
    val message by vm.message.collectAsState()

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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        LinkStatus(wifi, sending, stats, settings)

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Mode 2 : gaz (sans retour) et lacet à gauche
            Joystick(
                value = Offset(control.yaw, JoystickMath.throttleToY(control.throttle)),
                onValueChange = { vm.onLeftStick(yaw = it.x, throttle = JoystickMath.throttle(it.y)) },
                modifier = Modifier.weight(1f),
                returnY = false,
            )

            Column(
                modifier = Modifier.weight(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlightStateLabel(control)
                Text("Gaz ${"%.0f".format(control.throttle * 100)} %")
                when {
                    control.estop -> OutlinedButton(
                        onClick = vm::resetEmergencyStop,
                        modifier = Modifier.width(220.dp).height(56.dp),
                    ) {
                        Text("Réinitialiser", maxLines = 1)
                    }
                    control.armed -> Button(onClick = vm::disarm, modifier = Modifier.width(220.dp).height(56.dp)) {
                        Text("DÉSARMER")
                    }
                    else -> HoldToArmButton(onArm = vm::arm)
                }
                // Hauteur réservée : un message ne doit jamais déplacer ARM ni l'arrêt d'urgence
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = vm::emergencyStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.width(220.dp).height(72.dp),
                ) {
                    Text("ARRÊT D'URGENCE", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            // Tangage et roulis à droite, retour au centre
            Joystick(
                value = Offset(control.roll, control.pitch),
                onValueChange = { vm.onRightStick(roll = it.x, pitch = it.y) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LinkStatus(wifi: WifiState, sending: Boolean, stats: LinkStats, settings: LinkSettings) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val (text, color) = when {
            sending -> "Émission ${stats.rateHz} Hz · gigue max ${"%.1f".format(stats.maxJitterMs)} ms · " +
                "erreurs ${stats.sendErrors}" to Color(0xFF66BB6A)
            wifi is WifiState.Searching -> "Recherche du drone…" to Color(0xFFFFB300)
            wifi is WifiState.Lost -> "Wi-Fi perdu : moteurs non commandés" to Color(0xFFFF7043)
            else -> "Pas de liaison : se connecter dans l'onglet Connexion" to Color.Gray
        }
        Text(text, color = color)
        if (settings.protocol == ProtocolType.LEGACY_CSV) {
            Text("Legacy CSV : sans hélices uniquement", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FlightStateLabel(control: ControlState) {
    val (text, color) = when {
        control.estop -> "URGENCE" to MaterialTheme.colorScheme.error
        control.armed -> "ARMÉ" to Color(0xFFFFB300)
        else -> "DÉSARMÉ" to Color(0xFF66BB6A)
    }
    // Une seule ligne : la colonne centrale ne doit pas changer de hauteur
    Text(text, color = color, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
}

/** F-SEC-1 : geste volontaire, appui maintenu [ARM_HOLD_MS]. */
@Composable
private fun HoldToArmButton(onArm: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentOnArm by rememberUpdatedState(onArm)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind { drawRect(fill, size = size.copy(width = size.width * progress.value)) }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val hold = scope.launch {
                        progress.snapTo(0f)
                        progress.animateTo(1f, tween(ARM_HOLD_MS, easing = LinearEasing))
                        currentOnArm()
                    }
                    tryAwaitRelease()
                    hold.cancel()
                    scope.launch { progress.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("Maintenir 1 s pour armer")
    }
}

private const val ARM_HOLD_MS = 1000
