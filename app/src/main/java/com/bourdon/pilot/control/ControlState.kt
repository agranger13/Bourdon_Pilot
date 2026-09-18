package com.bourdon.pilot.control

import com.bourdon.pilot.protocol.ControlInput

enum class ArmResult { ARMED, REFUSED_THROTTLE, REFUSED_ESTOP, REFUSED_NO_LINK }

/**
 * État de pilotage côté app, immuable. Règles de sécurité F-SEC-1, F-SEC-2 et F-SEC-6.
 * roll, pitch, yaw dans [-1, 1], throttle dans [0, 1].
 */
data class ControlState(
    val roll: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val throttle: Float = 0f,
    val armed: Boolean = false,
    val estop: Boolean = false,
) {
    fun withRightStick(roll: Float, pitch: Float) =
        copy(roll = roll.coerceIn(-1f, 1f), pitch = pitch.coerceIn(-1f, 1f))

    /** Gaz bloqués à zéro tant que l'arrêt d'urgence est verrouillé. */
    fun withLeftStick(yaw: Float, throttle: Float) =
        copy(yaw = yaw.coerceIn(-1f, 1f), throttle = if (estop) 0f else throttle.coerceIn(0f, 1f))

    fun armResult(linkUp: Boolean) = when {
        estop -> ArmResult.REFUSED_ESTOP
        !linkUp -> ArmResult.REFUSED_NO_LINK
        throttle > ARM_THROTTLE_MAX -> ArmResult.REFUSED_THROTTLE
        else -> ArmResult.ARMED
    }

    fun tryArm(linkUp: Boolean) = if (armResult(linkUp) == ArmResult.ARMED) copy(armed = true) else this

    fun disarmed() = copy(armed = false)

    /** F-SEC-2 : verrouillé jusqu'à une réinitialisation explicite. */
    fun emergencyStopped() = copy(armed = false, estop = true, throttle = 0f)

    fun estopCleared() = copy(estop = false)

    /** F-SEC-6 : lancement, arrêt de l'émission ou reconnexion. */
    fun linkLost() = copy(armed = false, throttle = 0f)

    fun toInput() = ControlInput(roll, pitch, yaw, throttle, armed, estop)

    companion object {
        /** Tolérance pour « gaz à zéro » ; le firmware modifié exigera < 50/1000. */
        const val ARM_THROTTLE_MAX = 0.02f
    }
}
