package com.bourdon.pilot

import android.app.Application
import android.content.Context
import com.bourdon.pilot.link.LinkSettings
import com.bourdon.pilot.link.WifiConnector
import kotlinx.coroutines.flow.MutableStateFlow

class DroneApp : Application() {
    val container by lazy { AppContainer(this) }
}

/** Injection manuelle (§7.1) : objets partagés entre les écrans. */
class AppContainer(context: Context) {
    val wifi = WifiConnector(context.applicationContext)
    val settings = MutableStateFlow(LinkSettings())
}
