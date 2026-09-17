package com.drone.appcontrolleur.link

import com.drone.appcontrolleur.protocol.BinaryV1Codec
import com.drone.appcontrolleur.protocol.ControlInput
import com.drone.appcontrolleur.protocol.Crc16
import com.drone.appcontrolleur.protocol.FlightState
import com.drone.appcontrolleur.protocol.LegacyCsvCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Boucle réelle sur la boucle locale, sans Android (réseau non lié). */
class LinkManagerTest {

    @Test
    fun sendsAt50HzThenDisarmBurst() = runBlocking {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { receiver ->
            val settings = LinkSettings(host = "127.0.0.1", port = receiver.localPort, protocol = ProtocolType.LEGACY_CSV)
            val armed = ControlInput(throttle = 0.5f, arm = true)
            val manager = LinkManager()
            val received = mutableListOf<String>()

            val job = launch(Dispatchers.Default) { manager.run(null, settings, LegacyCsvCodec()) { armed } }
            receiver.soTimeout = 200
            val end = System.nanoTime() + 1_000_000_000L
            while (System.nanoTime() < end) received += receiver.receiveText()
            assertEquals(DronePhase.UNCONFIRMED, manager.drone.value.phase)
            job.cancelAndJoin()
            try {
                while (true) received += receiver.receiveText()
            } catch (_: SocketTimeoutException) {
            }

            val commands = received.takeWhile { it == "512,512,512,512,1,0" }
            assertTrue("${commands.size} paquets en 1 s", commands.size in 45..55)
            assertEquals(List(LinkManager.DISARM_BURST) { "512,512,512,0,0,0" }, received.drop(commands.size))
        }
    }

    @Test
    fun binaryWaitsForHelloAckThenReportsTelemetry() = runBlocking {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { drone ->
            val manager = LinkManager()
            val job = launch(Dispatchers.Default) { manager.run(null, binarySettings(drone), BinaryV1Codec()) { ControlInput.SAFE } }
            drone.soTimeout = 1000

            // Sans réponse : HELLO répétés, aucune commande
            assertEquals(VT_HELLO, drone.receivePacket().verType())
            val hello = drone.receivePacket()
            assertEquals("HELLO répété", VT_HELLO, hello.verType())
            assertEquals(DronePhase.HANDSHAKE, manager.drone.value.phase)

            drone.reply(hello, helloAck(protoVersion = 1))
            var commands = 0
            val end = System.nanoTime() + 300_000_000L
            while (System.nanoTime() < end) {
                val p = drone.receivePacket()
                if (p.verType() != VT_COMMAND) continue
                if (++commands == 1) {
                    // Télémétrie : drone désarmé, IMU OK, roulis 12,34°
                    drone.reply(p, packet(BinaryV1Codec.TELEMETRY_SIZE, VT_TELEMETRY) {
                        putShort(1); putShort(0); putShort(0); putShort(1234); putShort(0); putShort(0)
                        put(0); put(1); put(-50); put(125)
                    })
                }
            }
            assertTrue("$commands commandes après le HELLO_ACK", commands >= 10)
            val link = manager.drone.value
            assertEquals(DronePhase.ACTIVE, link.phase)
            assertEquals(3, link.hello!!.fwMinor)
            assertEquals(FlightState.DISARMED, link.telemetry!!.state)
            assertEquals(12.34f, link.telemetry!!.rollDeg, 0.001f)

            // Le drone se tait : déclaré muet, télémétrie retirée, commandes toujours émises
            var commandsDuringSilence = 0
            val silenceEnd = System.nanoTime() + (LinkManager.SILENCE_MS + 200) * 1_000_000
            while (System.nanoTime() < silenceEnd) {
                if (drone.receivePacket().verType() == VT_COMMAND) commandsDuringSilence++
            }
            assertEquals(DronePhase.NO_RESPONSE, manager.drone.value.phase)
            assertNull(manager.drone.value.telemetry)
            assertTrue(commandsDuringSilence > 0)

            job.cancelAndJoin()
            assertEquals(DronePhase.IDLE, manager.drone.value.phase)
        }
    }

    @Test
    fun incompatibleVersionSendsNoCommand() = runBlocking {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { drone ->
            val manager = LinkManager()
            val job = launch(Dispatchers.Default) { manager.run(null, binarySettings(drone), BinaryV1Codec()) { ControlInput.SAFE } }
            drone.soTimeout = 1000
            drone.reply(drone.receivePacket(), helloAck(protoVersion = 2))

            drone.soTimeout = 400
            var sawCommand = false
            try {
                while (true) if (drone.receivePacket().verType() == VT_COMMAND) sawCommand = true
            } catch (_: SocketTimeoutException) {
            }
            assertFalse(sawCommand)
            assertEquals(DronePhase.INCOMPATIBLE, manager.drone.value.phase)
            job.cancelAndJoin()
        }
    }

    private fun binarySettings(drone: DatagramSocket) =
        LinkSettings(host = "127.0.0.1", port = drone.localPort, protocol = ProtocolType.BINARY_V1)

    private fun helloAck(protoVersion: Int) = packet(BinaryV1Codec.HELLO_ACK_SIZE, VT_HELLO) {
        put(protoVersion.toByte()); put(0); put(3); put(0); put(1); put(0)
    }

    private fun packet(size: Int, verType: Int, body: ByteBuffer.() -> Unit): ByteArray {
        val bytes = ByteArray(size)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(BinaryV1Codec.MAGIC.toByte())
        buf.put(verType.toByte())
        buf.body()
        buf.putShort(size - 2, Crc16.ccittFalse(bytes, 0, size - 2).toShort())
        return bytes
    }

    private fun DatagramPacket.verType() = data[1].toInt() and 0xFF

    private fun DatagramSocket.receivePacket(): DatagramPacket {
        val packet = DatagramPacket(ByteArray(64), 64)
        receive(packet)
        return packet
    }

    private fun DatagramSocket.reply(to: DatagramPacket, bytes: ByteArray) =
        send(DatagramPacket(bytes, bytes.size, to.socketAddress))

    private fun DatagramSocket.receiveText(): String {
        val buffer = ByteArray(64)
        val packet = DatagramPacket(buffer, buffer.size)
        receive(packet)
        return String(buffer, 0, packet.length, Charsets.US_ASCII)
    }

    private companion object {
        const val VT_COMMAND = 0x11
        const val VT_TELEMETRY = 0x12
        const val VT_HELLO = 0x13
    }
}
