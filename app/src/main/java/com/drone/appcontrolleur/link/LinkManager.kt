package com.drone.appcontrolleur.link

import android.net.Network
import com.drone.appcontrolleur.protocol.ControlInput
import com.drone.appcontrolleur.protocol.ProtocolCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.abs
import kotlin.math.max

data class LinkStats(
    val rateHz: Int = 0,
    val sendErrors: Int = 0,
    val maxJitterMs: Float = 0f,
)

/**
 * Boucle d'émission à 50 Hz (F-PIL-4, NF-2).
 * [run] émet jusqu'à son annulation, puis envoie une rafale DISARM (F-SEC-4).
 */
class LinkManager {

    private val _stats = MutableStateFlow(LinkStats())
    val stats: StateFlow<LinkStats> = _stats.asStateFlow()

    /** [network] null : route par défaut (tests JVM, émulateur). */
    suspend fun run(
        network: Network?,
        settings: LinkSettings,
        codec: ProtocolCodec,
        input: () -> ControlInput,
    ) = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            network?.bindSocket(socket)
            val address = InetAddress.getByName(settings.host)
            var seq = 0
            var errors = 0

            fun send(command: ControlInput) {
                val bytes = codec.encodeCommand(command, seq)
                seq = (seq + 1) and 0xFFFF
                try {
                    socket.send(DatagramPacket(bytes, bytes.size, address, settings.port))
                } catch (e: IOException) {
                    errors++
                }
            }

            try {
                var windowStart = System.nanoTime()
                var sentInWindow = 0
                var maxJitterNs = 0L
                var lastSend = 0L
                var nextSend = windowStart
                while (isActive) {
                    val now = System.nanoTime()
                    if (lastSend != 0L) maxJitterNs = max(maxJitterNs, abs(now - lastSend - PERIOD_NS))
                    lastSend = now
                    send(input())
                    sentInWindow++

                    if (now - windowStart >= 1_000_000_000L) {
                        _stats.value = LinkStats(sentInWindow, errors, maxJitterNs / 1e6f)
                        windowStart = now
                        sentInWindow = 0
                        maxJitterNs = 0
                    }

                    nextSend += PERIOD_NS
                    val waitNs = nextSend - System.nanoTime()
                    if (waitNs < -PERIOD_NS) {
                        nextSend = System.nanoTime() // trop en retard : on se recale sans rafale de rattrapage
                    } else if (waitNs > 0) {
                        delay(waitNs / 1_000_000)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    repeat(DISARM_BURST) {
                        send(ControlInput.SAFE)
                        delay(PERIOD_MS)
                    }
                    _stats.value = LinkStats()
                }
            }
        }
    }

    companion object {
        const val PERIOD_MS = 20L
        const val PERIOD_NS = PERIOD_MS * 1_000_000
        const val DISARM_BURST = 5
    }
}
