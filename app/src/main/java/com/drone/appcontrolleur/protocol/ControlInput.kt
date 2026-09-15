package com.drone.appcontrolleur.protocol

/**
 * Consigne normalisée produite par l'app, indépendante du protocole.
 * roll, pitch, yaw dans [-1, 1] (pitch +1 = avant, yaw +1 = droite), throttle dans [0, 1].
 */
data class ControlInput(
    val roll: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val throttle: Float = 0f,
    val arm: Boolean = false,
    val estop: Boolean = false,
) {
    companion object {
        /** F-SEC-6 : état au lancement et après reconnexion. */
        val SAFE = ControlInput()
    }
}
