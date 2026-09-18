package com.bourdon.pilot.protocol

/** CRC-16/CCITT-FALSE : polynôme 0x1021, init 0xFFFF, sans réflexion ni XOR final. */
object Crc16 {
    fun ccittFalse(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
            }
            crc = crc and 0xFFFF
        }
        return crc
    }
}
