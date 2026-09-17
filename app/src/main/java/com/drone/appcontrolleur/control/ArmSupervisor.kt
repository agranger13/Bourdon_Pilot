package com.drone.appcontrolleur.control

import com.drone.appcontrolleur.link.DroneLink
import com.drone.appcontrolleur.link.DronePhase
import com.drone.appcontrolleur.protocol.BinaryV1Codec
import com.drone.appcontrolleur.protocol.DroneMessage
import com.drone.appcontrolleur.protocol.FlightState

/**
 * Aligne l'état de pilotage de l'app sur ce que le drone confirme (F-TEL-1, F-SEC-7, F-CON-5).
 * L'app se désarme si le drone ne répond plus, passe en failsafe ou n'arme pas ;
 * elle ne réarme jamais d'elle-même.
 */
class ArmSupervisor(private val confirmTimeoutMs: Long = CONFIRM_TIMEOUT_MS) {

    enum class Action(val apply: (ControlState) -> ControlState) {
        NONE({ it }),
        LINK_LOST(ControlState::linkLost),
        DISARM(ControlState::disarmed),
        ESTOP(ControlState::emergencyStopped),
    }

    data class Outcome(val action: Action = Action.NONE, val message: String? = null)

    private var lastPhase = DronePhase.IDLE
    private var mismatchSinceMs: Long? = null

    /** À appeler à chaque mise à jour du lien ; [armed] : l'app demande l'armement. */
    fun onLink(armed: Boolean, link: DroneLink, nowMs: Long): Outcome {
        if (link.phase != lastPhase) {
            val previous = lastPhase
            lastPhase = link.phase
            mismatchSinceMs = null
            val message = when (link.phase) {
                DronePhase.NO_RESPONSE -> "Liaison : le drone ne répond plus"
                DronePhase.INCOMPATIBLE -> "Protocole incompatible : drone en v${link.hello?.protoVersion}, " +
                    "app en v${BinaryV1Codec.VERSION}"
                else -> null
            }
            // F-SEC-6 : toute sortie de l'état « drone répond » repart désarmée, gaz à zéro
            if (previous == DronePhase.ACTIVE) return Outcome(Action.LINK_LOST, message)
            if (message != null) return Outcome(message = message)
        }

        val telemetry = link.telemetry
        if (telemetry == null || !armed || telemetry.state == FlightState.ARMED) {
            mismatchSinceMs = null
            return Outcome()
        }
        return when (telemetry.state) {
            FlightState.FAILSAFE -> Outcome(Action.DISARM, "Failsafe : le drone a coupé les moteurs")
            FlightState.ESTOP_LATCH -> Outcome(Action.ESTOP)
            else -> {
                val since = mismatchSinceMs ?: nowMs
                if (telemetry.armRefusedThrottle || nowMs - since >= confirmTimeoutMs) {
                    Outcome(Action.DISARM, "Armement refusé par le drone : ${refusalReason(telemetry)}")
                } else {
                    mismatchSinceMs = since
                    Outcome()
                }
            }
        }
    }

    companion object {
        /** Délai laissé au drone pour confirmer l'armement (télémétrie à 10 Hz). */
        const val CONFIRM_TIMEOUT_MS = 1000L

        fun refusalReason(t: DroneMessage.Telemetry) = when {
            t.armRefusedThrottle -> "gaz trop hauts"
            t.calibrating -> "calibration en cours"
            !t.imuOk -> "IMU absente ou en défaut"
            t.batteryCritical -> "batterie critique"
            else -> "raison inconnue"
        }
    }
}
