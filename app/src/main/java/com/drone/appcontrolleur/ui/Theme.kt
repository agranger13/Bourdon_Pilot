package com.drone.appcontrolleur.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DroneColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    error = Color(0xFFE53935),
    background = Color.Black,
    surface = Color(0xFF121212),
)

@Composable
fun AppControlleurTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DroneColors) {
        // Surface fournit la couleur de texte par défaut (sinon noir sur fond noir)
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
