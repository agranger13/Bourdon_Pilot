package com.drone.appcontrolleur.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/** Protocole binaire v1, little-endian (SPEC_FIRMWARE §4). */
class BinaryV1Codec : ProtocolCodec {

    override val supportsHandshake = true

    override fun encodeCommand(input: ControlInput, seq: Int): ByteArray {
        var flags = 0
        if (input.arm) flags = flags or FLAG_ARM
        if (input.estop) flags = flags or FLAG_ESTOP
        return packet(COMMAND_SIZE, TYPE_COMMAND, seq) {
            putShort(scale(input.roll, -1f, 1f).toShort())
            putShort(scale(input.pitch, -1f, 1f).toShort())
            putShort(scale(input.yaw, -1f, 1f).toShort())
            putShort(scale(input.throttle, 0f, 1f).toShort())
            put(flags.toByte())
            put(0)
        }
    }

    override fun encodeHello(seq: Int): ByteArray = packet(HELLO_SIZE, TYPE_HELLO, seq) {}

    override fun decode(data: ByteArray, length: Int): DroneMessage? {
        if (length < 2 || data[0] != MAGIC.toByte()) return null
        val verType = data[1].toInt() and 0xFF
        if (verType shr 4 != VERSION) return null
        val expectedSize = when (verType and 0x0F) {
            TYPE_TELEMETRY -> TELEMETRY_SIZE
            TYPE_HELLO -> HELLO_ACK_SIZE
            else -> return null
        }
        if (length != expectedSize) return null
        val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        val crc = buf.getShort(length - 2).toInt() and 0xFFFF
        if (crc != Crc16.ccittFalse(data, 0, length - 2)) return null

        buf.position(2)
        return when (verType and 0x0F) {
            TYPE_TELEMETRY -> DroneMessage.Telemetry(
                seq = buf.u16(),
                ackSeq = buf.u16(),
                vbatMv = buf.u16(),
                rollDeg = buf.short / 100f,
                pitchDeg = buf.short / 100f,
                yawRateDps = buf.short / 10f,
                state = FlightState.fromCode(buf.u8()),
                statusFlags = buf.u8(),
                rssiDbm = buf.get().toInt(),
                loopHz = buf.u8() * 2,
            )
            else -> DroneMessage.HelloAck(
                protoVersion = buf.u8(),
                fwMajor = buf.u8(),
                fwMinor = buf.u8(),
                fwPatch = buf.u8(),
                boardId = buf.u8(),
            )
        }
    }

    private inline fun packet(size: Int, type: Int, seq: Int, body: ByteBuffer.() -> Unit): ByteArray {
        val bytes = ByteArray(size)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(MAGIC.toByte())
        buf.put(((VERSION shl 4) or type).toByte())
        buf.putShort(seq.toShort())
        buf.body()
        buf.putShort(size - 2, Crc16.ccittFalse(bytes, 0, size - 2).toShort())
        return bytes
    }

    private fun scale(v: Float, min: Float, max: Float) = floor(v.coerceIn(min, max) * 1000.0 + 0.5).toInt()

    private fun ByteBuffer.u8() = get().toInt() and 0xFF
    private fun ByteBuffer.u16() = short.toInt() and 0xFFFF

    companion object {
        const val MAGIC = 0xD7
        const val VERSION = 1
        const val TYPE_COMMAND = 0x1
        const val TYPE_TELEMETRY = 0x2
        const val TYPE_HELLO = 0x3

        const val COMMAND_SIZE = 16
        const val TELEMETRY_SIZE = 20
        const val HELLO_SIZE = 6
        const val HELLO_ACK_SIZE = 10

        const val FLAG_ARM = 1 shl 0
        const val FLAG_ESTOP = 1 shl 1
    }
}
