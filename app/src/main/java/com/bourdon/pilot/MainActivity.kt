package com.bourdon.pilot

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bourdon.pilot.ui.BourdonPilotTheme
import com.bourdon.pilot.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // F-SEC-8 : écran maintenu allumé pendant le pilotage
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        setContent {
            BourdonPilotTheme {
                AppRoot()
            }
        }
    }

    override fun onDestroy() {
        // §6.3 : libérer le réseau du drone en quittant l'app
        if (isFinishing) (application as DroneApp).container.wifi.disconnect()
        super.onDestroy()
    }

    // NF-5 : plein écran immersif
    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
