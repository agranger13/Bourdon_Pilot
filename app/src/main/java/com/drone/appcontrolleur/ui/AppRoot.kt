package com.drone.appcontrolleur.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class Screen { CONNECTION, PILOT, SETTINGS }

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.CONNECTION) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.CONNECTION) }
    val connectionVm: ConnectionViewModel = viewModel()

    fun openSettings(from: Screen) {
        settingsReturn = from
        screen = Screen.SETTINGS
    }

    BackHandler(enabled = screen != Screen.CONNECTION) {
        screen = if (screen == Screen.SETTINGS) settingsReturn else Screen.CONNECTION
    }

    when (screen) {
        Screen.CONNECTION -> ConnectionScreen(
            onFly = { screen = Screen.PILOT },
            onOpenSettings = { openSettings(Screen.CONNECTION) },
            vm = connectionVm,
        )
        // Quitter l'écran retire PilotScreen : l'émission s'arrête (NF-7)
        Screen.PILOT -> PilotScreen(
            onOpenConnection = { screen = Screen.CONNECTION },
            onLeaveFlight = {
                connectionVm.disconnect()
                screen = Screen.CONNECTION
            },
            onOpenSettings = { openSettings(Screen.PILOT) },
        )
        Screen.SETTINGS -> SettingsScreen(onBack = { screen = settingsReturn }, vm = connectionVm)
    }
}
