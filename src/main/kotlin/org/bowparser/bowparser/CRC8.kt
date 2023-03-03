package org.bowparser.bowparser

class CRC8 {

    // Values for BOWBus CRC
    private val init = 0x07u.toUByte()
    private val poly = 0x42u.toUByte()

    fun crc8Bow(dataIn: List<UByte>): UByte {
        var crc = init
        for (data in dataIn) {
            for (bit in 0 until 8) {
                val bitTrue = ((crc xor data.rotateRight(bit)) and 0x01u) > 0u
                if (bitTrue) {
                    crc = crc xor poly
                }
                crc = (crc.rotateRight(1)) and 0x7Fu
                if (bitTrue) {
                    crc = crc or 0x80u
                }
            }
        }
        return crc
    }
}