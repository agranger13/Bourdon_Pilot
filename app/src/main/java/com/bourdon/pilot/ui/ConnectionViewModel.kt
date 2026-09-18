package com.bourdon.pilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bourdon.pilot.DroneApp
import com.bourdon.pilot.link.LinkSettings
import com.bourdon.pilot.link.ProbeResult
import com.bourdon.pilot.link.UdpProbe
import com.bourdon.pilot.link.WifiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as DroneApp).container
    private val connector = container.wifi
    val wifiState: StateFlow<WifiState> = connector.state
    val settings: StateFlow<LinkSettings> = container.settings

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    private val _probeResult = MutableStateFlow<ProbeResult?>(null)
    val probeResult: StateFlow<ProbeResult?> = _probeResult.asStateFlow()

    fun updateSettings(transform: (LinkSettings) -> LinkSettings) = container.settings.update(transform)

    fun connect() = with(settings.value) { connector.connect(ssid, password) }

    fun useCurrentWifi() = connector.useCurrentWifi()

    fun disconnect() = connector.disconnect()

    fun probe() {
        if (_probing.value) return
        val network = (wifiState.value as? WifiState.Connected)?.network
        val settings = settings.value
        viewModelScope.launch {
            _probing.value = true
            _probeResult.value = UdpProbe.probe(network, settings, settings.protocol.codec())
            _probing.value = false
        }
    }
}
