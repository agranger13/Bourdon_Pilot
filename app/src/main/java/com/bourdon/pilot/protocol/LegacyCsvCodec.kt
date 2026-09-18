package com.bourdon.pilot.protocol

import kotlin.math.floor

/**
 * Protocole du firmware actuel : `lx,ly,rx,ry,arm,emg` (CAHIER_DES_CHARGES §6.1).
 * lx = roll, ly = pitch, rx = yaw, ry = throttle (0..1023).
 * Le firmware décode les axes centrés par (raw - 511.5) / 511.5 : on encode l'inverse
 * pour couvrir exactement [-1, 1]. Il applique ensuite une zone morte |raw - 511| < 20.
 * Pas de handshake ni de télémétrie : réservé aux tests sans hélices.
 */
class LegacyCsvCodec : ProtocolCodec {

    override val supportsHandshake = false

    override fun encodeCommand(input: ControlInput, seq: Int): ByteArray {
        val lx = centered(input.roll)
        val ly = centered(input.pitch)
        val rx = centered(input.yaw)
        val ry = roundHalfUp(input.throttle.coerceIn(0f, 1f) * 1023.0)
        val arm = if (input.arm) 1 else 0
        val emg = if (input.estop) 1 else 0
        return "$lx,$ly,$rx,$ry,$arm,$emg".toByteArray(Charsets.US_ASCII)
    }

    override fun encodeHello(seq: Int): ByteArray? = null

    override fun decode(data: ByteArray, length: Int): DroneMessage? = null

    private fun centered(v: Float) = roundHalfUp(511.5 + v.coerceIn(-1f, 1f) * 511.5)

    // Arrondi explicite, identique dans le simulateur Python et les vecteurs partagés
    private fun roundHalfUp(x: Double) = floor(x + 0.5).toInt()
}
