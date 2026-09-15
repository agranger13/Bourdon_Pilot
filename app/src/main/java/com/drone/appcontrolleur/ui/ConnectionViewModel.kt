package com.drone.appcontrolleur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drone.appcontrolleur.link.LinkSettings
import com.drone.appcontrolleur.link.ProbeResult
import com.drone.appcontrolleur.link.UdpProbe
import com.drone.appcontrolleur.link.WifiConnector
import com.drone.appcontrolleur.link.WifiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val connector = WifiConnector(app)
    val wifiState: StateFlow<WifiState> = connector.state

    private val _settings = MutableStateFlow(LinkSettings())
    val settings: StateFlow<LinkSettings> = _settings.asStateFlow()

    private val _probing = MutableStateFlow(false)
    val probing: StateFlow<Boolean> = _probing.asStateFlow()

    private val _probeResult = MutableStateFlow<ProbeResult?>(null)
    val probeResult: StateFlow<ProbeResult?> = _probeResult.asStateFlow()

    fun updateSettings(transform: (LinkSettings) -> LinkSettings) = _settings.update(transform)

    fun connect() = with(_settings.value) { connector.connect(ssid, password) }

    fun useCurrentWifi() = connector.useCurrentWifi()

    fun disconnect() = connector.disconnect()

    fun probe() {
        if (_probing.value) return
        val network = (wifiState.value as? WifiState.Connected)?.network
        val settings = _settings.value
        viewModelScope.launch {
            _probing.value = true
            _probeResult.value = UdpProbe.probe(network, settings, settings.protocol.codec())
            _probing.value = false
        }
    }

    // §6.3 : libérer le réseau du drone en quittant l'écran
    override fun onCleared() = connector.disconnect()
}
