package com.drone.appcontrolleur.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

/** Vérifie le codec CSV contre le décodage réel du firmware (UDPReceiver::mapJoystick). */
class LegacyCsvCodecTest {

    private val codec = LegacyCsvCodec()

    @Test
    fun extremesDecodeToFullRange() {
        val max = fields(ControlInput(roll = 1f, pitch = -1f, yaw = 1f, throttle = 1f))
        assertEquals(1f, firmwareAxis(max[0]), 0f)
        assertEquals(-1f, firmwareAxis(max[1]), 0f)
        assertEquals(1f, firmwareThrottle(max[3]), 0f)
    }

    @Test
    fun neutralIsInsideFirmwareDeadzone() {
        val safe = fields(ControlInput.SAFE)
        assertEquals(listOf(512, 512, 512, 0, 0, 0), safe)
        assertEquals(0f, firmwareAxis(safe[0]), 0f)
    }

    @Test
    fun roundTripOutsideDeadzone() {
        val v = fields(ControlInput(roll = 0.5f))[0]
        assertEquals(0.5f, firmwareAxis(v), 1f / 511.5f)
    }

    private fun fields(input: ControlInput) =
        String(codec.encodeCommand(input, 0), Charsets.US_ASCII).split(",").map { it.toInt() }

    // Copie du firmware : centre 511.5, zone morte |raw - 511| < 20
    private fun firmwareAxis(raw: Int): Float {
        if (kotlin.math.abs(raw - 511) < 20) return 0f
        return ((raw - 511.5f) / 511.5f).coerceIn(-1f, 1f)
    }

    private fun firmwareThrottle(raw: Int) = (raw / 1023f).coerceIn(0f, 1f)
}
