package com.drone.appcontrolleur.link

import android.net.Network
import com.drone.appcontrolleur.protocol.ControlInput
import com.drone.appcontrolleur.protocol.DroneMessage
import com.drone.appcontrolleur.protocol.ProtocolCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

sealed interface ProbeResult {
    /** Legacy CSV : pas de réponse possible, vérifier les logs série du drone. */
    data class Sent(val payload: String) : ProbeResult
    data class Reply(val message: DroneMessage, val rttMs: Long) : ProbeResult
    data object NoReply : ProbeResult
    data class Failed(val reason: String) : ProbeResult
}

/**
 * Test ponctuel de la liaison (étape 4) : valide `bindSocket` sans lancer la boucle 50 Hz.
 * N'envoie que des paquets sûrs : HELLO, ou une consigne désarmée gaz à zéro.
 */
object UdpProbe {

    suspend fun probe(network: Network?, settings: LinkSettings, codec: ProtocolCodec): ProbeResult =
        withContext(Dispatchers.IO) {
            try {
                DatagramSocket().use { socket ->
                    // Sans réseau lié (émulateur + simulateur), la route par défaut est utilisée
                    network?.bindSocket(socket)
                    socket.soTimeout = REPLY_TIMEOUT_MS
                    val address = InetAddress.getByName(settings.host)

                    val hello = codec.encodeHello(seq = 0)
                    if (hello == null) {
                        val payload = codec.encodeCommand(ControlInput.SAFE, seq = 0)
                        socket.send(DatagramPacket(payload, payload.size, address, settings.port))
                        return@use ProbeResult.Sent(String(payload, Charsets.US_ASCII))
                    }

                    val start = System.nanoTime()
                    socket.send(DatagramPacket(hello, hello.size, address, settings.port))
                    val buffer = ByteArray(64)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val rttMs = (System.nanoTime() - start) / 1_000_000
                    codec.decode(buffer, packet.length)
                        ?.let { ProbeResult.Reply(it, rttMs) }
                        ?: ProbeResult.Failed("Réponse invalide (${packet.length} octets)")
                }
            } catch (e: SocketTimeoutException) {
                ProbeResult.NoReply
            } catch (e: Exception) {
                ProbeResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }

    private const val REPLY_TIMEOUT_MS = 1000
}
