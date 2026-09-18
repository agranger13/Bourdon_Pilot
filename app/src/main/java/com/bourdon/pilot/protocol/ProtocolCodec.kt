package com.bourdon.pilot.protocol

/** Messages reçus du drone. */
sealed interface DroneMessage {
    data class HelloAck(
        val protoVersion: Int,
        val fwMajor: Int,
        val fwMinor: Int,
        val fwPatch: Int,
        val boardId: Int,
    ) : DroneMessage

    data class Telemetry(
        val seq: Int,
        val ackSeq: Int,
        /** 0 si le drone ne mesure pas la batterie. */
        val vbatMv: Int,
        val rollDeg: Float,
        val pitchDeg: Float,
        val yawRateDps: Float,
        val state: FlightState,
        val statusFlags: Int,
        val rssiDbm: Int,
        val loopHz: Int,
    ) : DroneMessage {
        val imuOk get() = statusFlags and STATUS_IMU_OK != 0
        val batteryLow get() = statusFlags and STATUS_BATTERY_LOW != 0
        val batteryCritical get() = statusFlags and STATUS_BATTERY_CRITICAL != 0
        val armRefusedThrottle get() = statusFlags and STATUS_ARM_REFUSED_THROTTLE != 0
        val calibrating get() = statusFlags and STATUS_CALIBRATING != 0

        companion object {
            const val STATUS_IMU_OK = 1 shl 0
            const val STATUS_BATTERY_LOW = 1 shl 1
            const val STATUS_BATTERY_CRITICAL = 1 shl 2
            const val STATUS_ARM_REFUSED_THROTTLE = 1 shl 3
            const val STATUS_CALIBRATING = 1 shl 4
        }
    }
}

/** État du drone (SPEC_FIRMWARE §5.1). */
enum class FlightState(val code: Int) {
    DISARMED(0), ARMED(1), FAILSAFE(2), ESTOP_LATCH(3), UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** Encodage et décodage d'un protocole de liaison app ↔ drone. */
interface ProtocolCodec {
    /** Le protocole prévoit-il un HELLO et une télémétrie ? */
    val supportsHandshake: Boolean

    fun encodeCommand(input: ControlInput, seq: Int): ByteArray

    /** Null si le protocole n'a pas de handshake. */
    fun encodeHello(seq: Int): ByteArray?

    /** Null si le datagramme est invalide ou non géré. */
    fun decode(data: ByteArray, length: Int = data.size): DroneMessage?
}
