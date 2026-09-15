package com.drone.appcontrolleur.link

import com.drone.appcontrolleur.protocol.ControlInput
import com.drone.appcontrolleur.protocol.LegacyCsvCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

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

    private fun DatagramSocket.receiveText(): String {
        val buffer = ByteArray(64)
        val packet = DatagramPacket(buffer, buffer.size)
        receive(packet)
        return String(buffer, 0, packet.length, Charsets.US_ASCII)
    }
}
