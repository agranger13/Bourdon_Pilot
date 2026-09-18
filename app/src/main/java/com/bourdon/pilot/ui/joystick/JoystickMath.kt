package com.bourdon.pilot.ui.joystick

import androidx.compose.ui.geometry.Offset

/**
 * Géométrie des joysticks, sans dépendance à l'UI.
 * Position normalisée : x vers la droite, y vers le haut, chaque axe dans [-1, 1] (course carrée).
 */
object JoystickMath {

    /**
     * Mouvement relatif : poser le doigt ne change rien, seul le glissement déplace le manche.
     * Évite un saut des gaz quand on touche le joystick gauche (axe sans retour au centre).
     */
    fun drag(start: Offset, downPx: Offset, currentPx: Offset, travelPx: Float): Offset {
        val x = start.x + (currentPx.x - downPx.x) / travelPx
        val y = start.y - (currentPx.y - downPx.y) / travelPx
        return Offset(x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))
    }

    /** Doigt levé : retour au centre des axes concernés (F-PIL-1, F-PIL-2). */
    fun release(value: Offset, returnX: Boolean, returnY: Boolean) =
        Offset(if (returnX) 0f else value.x, if (returnY) 0f else value.y)

    /** Axe vertical sans retour au centre vers les gaz : bas = 0, haut = 1. */
    fun throttle(y: Float) = ((y + 1f) / 2f).coerceIn(0f, 1f)

    /** Inverse de [throttle] : position verticale du manche pour des gaz donnés. */
    fun throttleToY(throttle: Float) = throttle.coerceIn(0f, 1f) * 2f - 1f

    /** Position du manche gauche pour des gaz à zéro (F-SEC-6). */
    val THROTTLE_ZERO = Offset(0f, -1f)
}
