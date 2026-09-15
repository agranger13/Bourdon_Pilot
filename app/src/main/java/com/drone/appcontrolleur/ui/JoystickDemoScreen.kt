package com.drone.appcontrolleur.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.drone.appcontrolleur.protocol.ControlInput
import com.drone.appcontrolleur.protocol.LegacyCsvCodec
import com.drone.appcontrolleur.ui.joystick.Joystick
import com.drone.appcontrolleur.ui.joystick.JoystickMath

/** Écran de démo de l'étape 5 : joysticks Mode 2, sans émission réseau. */
@Composable
fun JoystickDemoScreen() {
    var left by remember { mutableStateOf(JoystickMath.THROTTLE_ZERO) }
    var right by remember { mutableStateOf(Offset.Zero) }
    val codec = remember { LegacyCsvCodec() }

    val input = ControlInput(
        roll = right.x,
        pitch = right.y,
        yaw = left.x,
        throttle = JoystickMath.throttle(left.y),
    )

    Row(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Mode 2 : gaz (sans retour) et lacet à gauche
        Joystick(value = left, onValueChange = { left = it }, modifier = Modifier.weight(1f), returnY = false)

        Column(
            modifier = Modifier.weight(0.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Gaz ${"%.0f".format(input.throttle * 100)} %", style = MaterialTheme.typography.titleMedium)
            Text("Lacet ${"%+.2f".format(input.yaw)}")
            Text("Tangage ${"%+.2f".format(input.pitch)}")
            Text("Roulis ${"%+.2f".format(input.roll)}")
            Text(
                String(codec.encodeCommand(input, 0), Charsets.US_ASCII),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Tangage et roulis à droite, retour au centre
        Joystick(value = right, onValueChange = { right = it }, modifier = Modifier.weight(1f))
    }
}
