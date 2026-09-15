package com.drone.appcontrolleur.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Écran de pilotage : squelette, joysticks et lien à venir (étapes 5 et 6). */
@Composable
fun PilotScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text("AppControlleur : désarmé", color = MaterialTheme.colorScheme.primary)
    }
}
