package com.drone.appcontrolleur.link

import android.net.Network
import com.drone.appcontrolleur.protocol.BinaryV1Codec
import com.drone.appcontrolleur.protocol.ControlInput
import com.drone.appcontrolleur.protocol.DroneMessage
import com.drone.appcontrolleur.protocol.ProtocolCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.math.abs
import kotlin.math.max

data class LinkStats(
    val rateHz: Int = 0,
    val sendErrors: Int = 0,
    val maxJitterMs: Float = 0f,
)

/** Dialogue avec le drone en binaire v1 (F-CON-3, F-CON-4). */
enum class DronePhase {
    /** Pas d'émission en cours. */
    IDLE,

    /** Legacy CSV : émission sans retour du drone. */
    UNCONFIRMED,

    /** HELLO envoyé, en attente du HELLO_ACK : aucune commande émise. */
    HANDSHAKE,

    /** Le drone répond (HELLO_ACK ou télémétrie récents). */
    ACTIVE,

    /** Handshake fait, mais plus rien reçu du drone depuis [LinkManager.SILENCE_MS]. */
    NO_RESPONSE,

    /** Le drone parle une autre version du protocole : aucune commande émise. */
    INCOMPATIBLE,
}

data class DroneLink(
    val phase: DronePhase = DronePhase.IDLE,
    val hello: DroneMessage.HelloAck? = null,
    /** Dernière télémétrie, null si aucune reçue depuis [LinkManager.TELEMETRY_STALE_MS]. */
    val telemetry: DroneMessage.Telemetry? = null,
) {
    val responding get() = phase == DronePhase.ACTIVE
}

/**
 * Boucle d'émission à 50 Hz (F-PIL-4, NF-2).
 * En binaire v1 : HELLO jusqu'au HELLO_ACK, puis commandes, réception de la télémétrie
 * et HELLO de maintien tant que le drone n'envoie pas de télémétrie (firmware sans FW-4).
 * [run] émet jusqu'à son annulation, puis envoie une rafale DISARM (F-SEC-4).
 */
class LinkManager {

    private val _stats = MutableStateFlow(LinkStats())
    val stats: StateFlow<LinkStats> = _stats.asStateFlow()

    private val _drone = MutableStateFlow(DroneLink())
    val drone: StateFlow<DroneLink> = _drone.asStateFlow()

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
            var helloSeq = 0
            var errors = 0

            fun sendBytes(bytes: ByteArray) {
                try {
                    socket.send(DatagramPacket(bytes, bytes.size, address, settings.port))
                } catch (e: IOException) {
                    errors++
                }
            }

            fun send(command: ControlInput) {
                sendBytes(codec.encodeCommand(command, seq))
                seq = (seq + 1) and 0xFFFF
            }

            val inbox = Inbox()

            val receiver = if (codec.supportsHandshake) {
                launch {
                    socket.soTimeout = RECEIVE_TIMEOUT_MS
                    val buffer = ByteArray(64)
                    val packet = DatagramPacket(buffer, buffer.size)
                    while (isActive) {
                        try {
                            packet.length = buffer.size
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        } catch (_: IOException) {
                            if (socket.isClosed) break else continue
                        }
                        // Seul le drone configuré est écouté
                        if (packet.address != address || packet.port != settings.port) continue
                        val now = System.nanoTime()
                        when (val message = codec.decode(buffer, packet.length)) {
                            is DroneMessage.HelloAck -> {
                                inbox.hello = message
                                inbox.lastHeardNs = now
                            }
                            is DroneMessage.Telemetry -> {
                                inbox.telemetry = message
                                inbox.lastHeardNs = now
                                inbox.lastTelemetryNs = now
                            }
                            null -> Unit
                        }
                    }
                }
            } else {
                null
            }

            _drone.value = DroneLink(if (codec.supportsHandshake) DronePhase.HANDSHAKE else DronePhase.UNCONFIRMED)

            try {
                var windowStart = System.nanoTime()
                var sentInWindow = 0
                var maxJitterNs = 0L
                var lastSend = 0L
                var nextSend = windowStart
                var lastHelloNs = 0L
                while (isActive) {
                    val now = System.nanoTime()
                    if (lastSend != 0L) maxJitterNs = max(maxJitterNs, abs(now - lastSend - PERIOD_NS))
                    lastSend = now

                    if (codec.supportsHandshake) {
                        val ack = inbox.hello
                        val freshTelemetry = inbox.telemetry.takeIf { now - inbox.lastTelemetryNs < TELEMETRY_STALE_MS * 1_000_000 }
                        val phase = when {
                            ack != null && ack.protoVersion != BinaryV1Codec.VERSION -> DronePhase.INCOMPATIBLE
                            ack == null -> DronePhase.HANDSHAKE
                            now - inbox.lastHeardNs > SILENCE_MS * 1_000_000 -> DronePhase.NO_RESPONSE
                            else -> DronePhase.ACTIVE
                        }
                        _drone.value = DroneLink(phase, ack, freshTelemetry)

                        // HELLO rapide tant que le drone ne répond pas ; lent pour le maintien sans télémétrie
                        val helloPeriodMs = when {
                            phase == DronePhase.INCOMPATIBLE -> Long.MAX_VALUE
                            phase != DronePhase.ACTIVE -> HELLO_RETRY_MS
                            freshTelemetry == null -> HELLO_KEEPALIVE_MS
                            else -> Long.MAX_VALUE
                        }
                        if (helloPeriodMs != Long.MAX_VALUE && now - lastHelloNs >= helloPeriodMs * 1_000_000) {
                            codec.encodeHello(helloSeq)?.let(::sendBytes)
                            helloSeq = (helloSeq + 1) and 0xFFFF
                            lastHelloNs = now
                        }
                        if (phase == DronePhase.ACTIVE || phase == DronePhase.NO_RESPONSE) {
                            send(input())
                            sentInWindow++
                        }
                    } else {
                        send(input())
                        sentInWindow++
                    }

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
                    // Après la rafale : receive() bloque jusqu'à RECEIVE_TIMEOUT_MS
                    receiver?.cancelAndJoin()
                    _stats.value = LinkStats()
                    _drone.value = DroneLink()
                }
            }
        }
    }

    /** Écrit par le récepteur, lu par la boucle d'émission. */
    private class Inbox {
        @Volatile var hello: DroneMessage.HelloAck? = null
        @Volatile var telemetry: DroneMessage.Telemetry? = null
        @Volatile var lastHeardNs = 0L
        @Volatile var lastTelemetryNs = 0L
    }

    companion object {
        const val PERIOD_MS = 20L
        const val PERIOD_NS = PERIOD_MS * 1_000_000
        const val DISARM_BURST = 5

        /** Sous le verrou pilote du firmware (300 ms sans paquet valide). */
        const val HELLO_RETRY_MS = 250L
        const val HELLO_KEEPALIVE_MS = 500L
        const val TELEMETRY_STALE_MS = 500L
        const val SILENCE_MS = 1200L
        const val RECEIVE_TIMEOUT_MS = 100
    }
}
