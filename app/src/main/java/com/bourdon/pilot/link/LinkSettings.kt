package com.bourdon.pilot.link

import com.bourdon.pilot.protocol.BinaryV1Codec
import com.bourdon.pilot.protocol.LegacyCsvCodec
import com.bourdon.pilot.protocol.ProtocolCodec

/** F-CON-6 : protocole parlé par le firmware. */
enum class ProtocolType(val label: String) {
    LEGACY_CSV("Legacy CSV"),
    BINARY_V1("Binaire v1");

    fun codec(): ProtocolCodec = when (this) {
        LEGACY_CSV -> LegacyCsvCodec()
        BINARY_V1 -> BinaryV1Codec()
    }
}

/** Paramètres de liaison (F-REG-1). Persistance DataStore à venir (F-REG-3). */
data class LinkSettings(
    val ssid: String = "DRONE_IOT",
    val password: String = "dronepassword",
    val host: String = "192.168.4.1",
    val port: Int = 4210,
    val protocol: ProtocolType = ProtocolType.LEGACY_CSV,
)
