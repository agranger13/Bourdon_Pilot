package com.bourdon.pilot.protocol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Vérifie les codecs contre shared/protocol_vectors.json (généré par tools/gen_vectors.py). */
class ProtocolVectorsTest {

    private val vectors = JSONObject(File(System.getProperty("protocolVectors")!!).readText())
    private val binary = BinaryV1Codec()
    private val csv = LegacyCsvCodec()

    @Test
    fun crc16() = vectors.objects("crc16_ccitt_false").forEach {
        assertEquals(it.getInt("crc"), Crc16.ccittFalse(it.getString("ascii").toByteArray()))
    }

    @Test
    fun commandEncoding() = vectors.objects("command").forEach {
        assertArrayEquals(it.toString(), it.getString("hex").hexToBytes(), binary.encodeCommand(it.toInput(), it.getInt("seq")))
    }

    @Test
    fun legacyCsvEncoding() = vectors.objects("legacy_csv").forEach {
        assertEquals(it.getString("text"), String(csv.encodeCommand(it.toInput(), 0), Charsets.US_ASCII))
    }

    @Test
    fun helloEncoding() = vectors.objects("hello").forEach {
        assertArrayEquals(it.getString("hex").hexToBytes(), binary.encodeHello(it.getInt("seq")))
    }

    @Test
    fun telemetryDecoding() = vectors.objects("telemetry").forEach {
        val t = binary.decode(it.getString("hex").hexToBytes()) as DroneMessage.Telemetry
        assertEquals(it.getInt("seq"), t.seq)
        assertEquals(it.getInt("ackSeq"), t.ackSeq)
        assertEquals(it.getInt("vbatMv"), t.vbatMv)
        assertEquals(it.getInt("rollCdeg") / 100f, t.rollDeg, 1e-3f)
        assertEquals(it.getInt("pitchCdeg") / 100f, t.pitchDeg, 1e-3f)
        assertEquals(it.getInt("yawRateDdps") / 10f, t.yawRateDps, 1e-3f)
        assertEquals(FlightState.fromCode(it.getInt("state")), t.state)
        assertEquals(it.getInt("statusFlags"), t.statusFlags)
        assertEquals(it.getInt("rssi"), t.rssiDbm)
        assertEquals(it.getInt("loopHzHalf") * 2, t.loopHz)
    }

    @Test
    fun helloAckDecoding() = vectors.objects("hello_ack").forEach {
        val ack = binary.decode(it.getString("hex").hexToBytes()) as DroneMessage.HelloAck
        assertEquals(DroneMessage.HelloAck(
            it.getInt("protoVersion"), it.getInt("fwMajor"), it.getInt("fwMinor"),
            it.getInt("fwPatch"), it.getInt("boardId"),
        ), ack)
    }

    @Test
    fun corruptedTelemetryIsRejected() {
        val bytes = vectors.objects("telemetry").first().getString("hex").hexToBytes()
        bytes[8] = (bytes[8] + 1).toByte()
        assertNull(binary.decode(bytes))
    }

    @Test
    fun wrongSizeOrMagicIsRejected() {
        val bytes = vectors.objects("telemetry").first().getString("hex").hexToBytes()
        assertNull(binary.decode(bytes, bytes.size - 1))
        assertNull(binary.decode(bytes.copyOf().also { it[0] = 0x00 }))
        assertNull(binary.decode(ByteArray(0)))
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array: JSONArray = getJSONArray(key)
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun JSONObject.toInput() = ControlInput(
        roll = getDouble("roll").toFloat(),
        pitch = getDouble("pitch").toFloat(),
        yaw = getDouble("yaw").toFloat(),
        throttle = getDouble("throttle").toFloat(),
        arm = getBoolean("arm"),
        estop = getBoolean("estop"),
    )

    private fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
