package org.bowparser.bowparser

@OptIn(ExperimentalUnsignedTypes::class)
class Decoder(private val commandsByInt: Map<UByte, String>, private val dataIdsByInt: Map<UByte, String>) {

    fun decode(
        message: Message,
    ): String {
        return when (message.type.toInt()) {
            0x04 -> "PING!"
            0x03 -> "PONG!"
            0x00 -> "HANDOFF"
            0x01, 0x02 -> decodeRequestOrResponse(message)
            else -> ""
        }
    }

    private fun decodeRequestOrResponse(message: Message): String {
        var decoded = ""
        val cmd = message.message[3].toInt()
        val cmdName = withName(message.message[3], commandsByInt)
        val data = message.message.drop(4).dropLast(1)

        // Defaults
        if (message.type.toInt() == 0x01) {
            decoded = "$cmdName ${hex(data)}"
        } else if (message.type.toInt() == 0x02) {
            decoded = "$cmdName - OK ${hex(data)}"
        }


        if (cmd == 0x08) {
            if (message.type.toInt() == 0x01) {
                decoded += createGetDataString(message.message, dataIdsByInt)
            }
        } else if (cmd == 0x17) {
            if (message.type.toInt() == 0x01) {
                decoded = "$cmdName: E-00${hex(message.message.slice(4 until 5))}"
            }
        } else if (cmd == 0x20) {
            if (message.type.toInt() == 0x02) {
                decoded = "$cmdName - OK ${hex(message.message.slice(4 until 6))} ${hex(message.message.slice(10 until 12))} (${hex(data)})"
            }
        } else if (cmd == 0x34) {
            if (message.type.toInt() == 0x01) {
                decoded += when (val value = message.message[4].toInt()) {
                    0x00 -> ""
                    0x01 -> " > ECO"
                    0x02 -> " > NORMAL"
                    0x03 -> " > POWER"
                    else -> " > ???($value)"
                }
            }
        } else if (cmd == 0x26 || cmd == 0x27) {
            if (message.type.toInt() == 0x01) {
                decoded += ": "
                decoded += createBlinkString("OFF", message.message[4], 0)
                decoded += createBlinkString("ECO", message.message[4], 2)
                decoded += createBlinkString("NRM", message.message[4], 4)
                decoded += createBlinkString("POW", message.message[4], 6)

                decoded += createBlinkString("WRE", message.message[5], 0)
                decoded += createBlinkString("TOT", message.message[5], 2)
                decoded += createBlinkString("TRP", message.message[5], 4)
                decoded += createBlinkString("LIG", message.message[5], 6)

                decoded += createBlinkString("BAR", message.message[6], 0)
                decoded += createBlinkString("COM", message.message[6], 4)
                decoded += createBlinkString("KM", message.message[6], 6)

                decoded += "%02d%% ".format(message.message[7].toInt())

                val spd = hex(message.message.slice(8 until 10))
                decoded += "'${spd.slice(1 until 3)}.${spd.substring(3)}' "

                decoded += "'${hex(message.message.slice(10 until 13)).substring(1).replace('c', ' ').replace('a', '-')}' "
            }
        } else if (cmd == 0x28) {
            if (message.type.toInt() == 0x01) {
                decoded += ": "
                decoded += when (message.message[4].toInt()) {
                    0x00 -> "SCR:MAIN"
                    0x01 -> "SCR:BAT+CHRG"
                    0x02 -> "SCR:BAT"
                    0x03 -> "SCR:MAIN"
                    else -> "SCR:???"
                }
                decoded += "(${message.message[4]}) "

                decoded += when (message.message[5].toInt()) {
                    0x00 -> "ASS:OFF"
                    0x01 -> "ASS:1"
                    0x02 -> "ASS:2"
                    0x03 -> "ASS:3"
                    0x04 -> "ASS:P"
                    0x05 -> "ASS:R"
                    0x06 -> "ASS:4"
                    else -> "ASS:???"
                }
                decoded += "(${message.message[5]}) "

                if (isBitSet(message.message[6], 3)) {
                    decoded += "SCR:ON "
                }

                if (isBitSet(message.message[6], 0)) {
                    decoded += "LIGHT "
                }

                if (isBitSet(message.message[6], 2)) {
                    decoded += "RANGE_EXT "
                }

                decoded += "speed:${message.message[7].toInt().shl(8) + message.message[8].toInt()} "
                decoded += "trip1:${message.message[9].toInt().shl(24) + message.message[10].toInt().shl(16) + message.message[11].toInt().shl(8) + message.message[12].toInt()} "
                decoded += "trip2:${message.message[13].toInt().shl(24) + message.message[14].toInt().shl(16) + message.message[15].toInt().shl(8) + message.message[16].toInt()} "
            }
        }
        return decoded
    }


    private fun createBlinkString(name: String, input: UByte, shift: Int): String {
        return buildString {
            val value = input.rotateRight(shift) and 0x03u
            if (value.toUInt() != 0x00u) {
                append(name)
                append(
                    when (value.toUInt()) {
                        0x00u -> ""
                        0x01u -> ":FST"
                        0x02u -> ":SLW"
                        0x03u -> ":SOL"
                        else -> throw IllegalStateException("Got value which should not be possible: $value")
                    }
                )
                append(" ")
            }
        }
    }

    private fun createGetDataString(message: UByteArray, dataIdsByInt: Map<UByte, String>): String {
        val data = message.drop(4).dropLast(1)

        return buildString {
            var index = 0
            while (true) {
                val array = (data[index].toUInt() and 0x40u) != 0u
                val more = (data[index].toUInt() and 0x80u) != 0u
                val partSize = if (array) 3 else 2

                val part = data.slice(index until (index + partSize))

                val flags = (part[0].toUInt() and 0x7fu).toUByte()
                val type = part[1]

                append(" ${hex(flags)}:${hex(type)}")
                if (array) {
                    append("[${part[2]}]")
                }

                var typeName = dataIdsByInt[type]
                typeName = typeName ?: "Unknown"
                append("($typeName)")

                if (more) {
                    index += partSize
                } else {
                    break
                }
            }
        }
    }
}