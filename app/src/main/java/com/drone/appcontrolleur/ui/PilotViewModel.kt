package com.drone.appcontrolleur.ui

import android.app.Application
import android.net.Network
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drone.appcontrolleur.DroneApp
import com.drone.appcontrolleur.control.ArmResult
import com.drone.appcontrolleur.control.ArmSupervisor
import com.drone.appcontrolleur.control.ControlState
import com.drone.appcontrolleur.link.DroneLink
import com.drone.appcontrolleur.link.DronePhase
import com.drone.appcontrolleur.link.LinkManager
import com.drone.appcontrolleur.link.LinkSettings
import com.drone.appcontrolleur.link.LinkStats
import com.drone.appcontrolleur.link.WifiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PilotViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as DroneApp).container
    private val linkManager = LinkManager()

    val wifiState: StateFlow<WifiState> = container.wifi.state
    val settings: StateFlow<LinkSettings> = container.settings
    val stats: StateFlow<LinkStats> = linkManager.stats
    val drone: StateFlow<DroneLink> = linkManager.drone
    private val supervisor = ArmSupervisor()

    private val _control = MutableStateFlow(ControlState())
    val control: StateFlow<ControlState> = _control.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Écran de pilotage visible et app au premier plan (F-SEC-4, NF-7). */
    private val active = MutableStateFlow(false)

    private data class Session(val active: Boolean, val network: Network?, val settings: LinkSettings)

    init {
        viewModelScope.launch {
            combine(active, wifiState, settings) { isActive, wifi, s ->
                Session(isActive, (wifi as? WifiState.Connected)?.network, s)
            }
                .distinctUntilChanged()
                .collectLatest { session ->
                    // F-SEC-6 : chaque démarrage ou reconnexion repart désarmé, gaz à zéro
                    _control.update { it.linkLost() }
                    if (!session.active || session.network == null) return@collectLatest
                    // Une erreur réseau (ex. bindSocket refusé pendant un changement de Wi-Fi)
                    // ne doit pas figer l'écran : nouvel essai tant que la session est la même
                    while (true) {
                        _sending.value = true
                        try {
                            linkManager.run(session.network, session.settings, session.settings.protocol.codec()) {
                                _control.value.toInput()
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _message.value = "Liaison interrompue : ${e.message}"
                        } finally {
                            _sending.value = false
                            _control.update { it.linkLost() }
                        }
                        delay(RETRY_DELAY_MS)
                    }
                }
        }
        viewModelScope.launch {
            drone.collect { link ->
                val outcome = supervisor.onLink(_control.value.armed, link, SystemClock.elapsedRealtime())
                if (outcome.action != ArmSupervisor.Action.NONE) _control.update(outcome.action.apply)
                outcome.message?.let { _message.value = it }
            }
        }
    }

    /** Binaire v1 : armement possible seulement si le drone répond. */
    private fun linkUp() = _sending.value && drone.value.phase.let { it == DronePhase.ACTIVE || it == DronePhase.UNCONFIRMED }

    fun setActive(value: Boolean) {
        active.value = value
    }

    fun onLeftStick(yaw: Float, throttle: Float) = _control.update { it.withLeftStick(yaw, throttle) }

    fun onRightStick(roll: Float, pitch: Float) = _control.update { it.withRightStick(roll, pitch) }

    fun arm() {
        var result = ArmResult.ARMED
        _control.update { state ->
            result = state.armResult(linkUp = linkUp())
            state.tryArm(linkUp = linkUp())
        }
        _message.value = when (result) {
            ArmResult.ARMED -> null
            ArmResult.REFUSED_THROTTLE -> "Armement refusé : gaz à zéro requis"
            ArmResult.REFUSED_ESTOP -> "Armement refusé : réinitialiser l'arrêt d'urgence"
            ArmResult.REFUSED_NO_LINK -> "Armement refusé : pas de liaison"
        }
    }

    fun disarm() = _control.update { it.disarmed() }

    fun emergencyStop() {
        _control.update { it.emergencyStopped() }
        _message.value = null
    }

    fun resetEmergencyStop() = _control.update { it.estopCleared() }

    private companion object {
        const val RETRY_DELAY_MS = 1000L
    }
}
