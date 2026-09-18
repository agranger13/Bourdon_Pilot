package com.bourdon.pilot.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface WifiState {
    data object Idle : WifiState
    data class Searching(val ssid: String?) : WifiState
    data class Connected(val network: Network) : WifiState
    /** Réseau perdu : la requête reste active, Android peut s'y reconnecter (F-CON-5). */
    data object Lost : WifiState
    /** Drone introuvable, délai dépassé ou fenêtre système refusée. */
    data object Unavailable : WifiState
}

/**
 * Obtient le réseau Wi-Fi du drone pour l'app (CAHIER_DES_CHARGES §6.3).
 * Le [Network] obtenu sert à lier les sockets : sans `bindSocket`, les paquets partent en 4G/5G.
 */
class WifiConnector(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _state = MutableStateFlow<WifiState>(WifiState.Idle)
    val state: StateFlow<WifiState> = _state.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    /** F-CON-1 : connexion automatique. Android affiche une fenêtre de confirmation. */
    fun connect(ssid: String, passphrase: String) {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply { if (passphrase.isNotEmpty()) setWpa2Passphrase(passphrase) }
            .build()
        request(wifiRequest().setNetworkSpecifier(specifier).build(), ssid)
    }

    /** Repli : utilise le Wi-Fi auquel le téléphone est déjà connecté (réglages Android). */
    fun useCurrentWifi() = request(wifiRequest().build(), ssid = null)

    fun disconnect() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
        _state.value = WifiState.Idle
    }

    private fun wifiRequest() = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        // Le point d'accès du drone n'a pas d'Internet
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    private fun request(request: NetworkRequest, ssid: String?) {
        disconnect()
        _state.value = WifiState.Searching(ssid)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _state.value = WifiState.Connected(network)
            }

            override fun onLost(network: Network) {
                _state.value = WifiState.Lost
            }

            override fun onUnavailable() {
                callback = null
                _state.value = WifiState.Unavailable
            }
        }
        callback = cb
        connectivity.requestNetwork(request, cb, REQUEST_TIMEOUT_MS)
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 30_000
    }
}
