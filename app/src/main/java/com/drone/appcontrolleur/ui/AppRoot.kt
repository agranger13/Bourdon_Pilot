package com.drone.appcontrolleur.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private val screens = listOf("Connexion", "Pilotage")

@Composable
fun AppRoot() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected, modifier = Modifier.fillMaxWidth()) {
            screens.forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (selected) {
                0 -> ConnectionScreen()
                // Quitter l'onglet retire PilotScreen : l'émission s'arrête (NF-7)
                else -> PilotScreen()
            }
        }
    }
}
