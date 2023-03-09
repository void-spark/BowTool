package org.bowparser.bowparser

import java.math.BigInteger

@OptIn(ExperimentalUnsignedTypes::class)
class Decoder(private val commandsByInt: Map<UByte, String>, private val dataIdsByInt: Map<UByte, String>) {

    fun check(message: Message) = buildString {
        if (message.message.size != message.size!!.toInt()) {
            append(" SIZE MISMATCH")
        }

        if (CRC8().crc8Bow(message.message.dropLast(1)) != message.message.last()) {
            append(" CRC MISMATCH")
        }
    }

    fun decode(message: Message): String {
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
        val cmdName = withName(message.message[3], commandsByInt)
        val data = message.data()

        // Defaults
        if (message.isReq()) {
            decoded = "$cmdName ${hex(data)}"
        } else if (message.isRsp()) {
            decoded = "$cmdName - OK ${hex(data)}"
        }

        when (message.cmd()) {
            0x08 -> if (message.isReqOrRsp()) decoded += createGetDataString(message)
            0x17 -> if (message.isReq()) decoded = "$cmdName: E-00${hex(data.slice(0 until 1))}"
            0x20 -> if (message.isRsp()) decoded = "$cmdName - OK ${hex(data.slice(0 until 2))} ${hex(data.slice(6 until 8))} (${hex(data)})"

            0x34 -> if (message.isReq()) {
                decoded += when (val value = data[0].toInt()) {
                    0x00 -> ""
                    0x01 -> " > ECO"
                    0x02 -> " > NORMAL"
                    0x03 -> " > POWER"
                    else -> " > ???($value)"
                }
            }

            0x26, 0x27 -> if (message.isReq()) {
                decoded += createCu2UpdateDisplayString(message)
            }

            0x28 -> if (message.isReq()) {
                decoded += ": "
                decoded += when (data[0].toInt()) {
                    0x00 -> "SCR:MAIN"
                    0x01 -> "SCR:BAT+CHRG"
                    0x02 -> "SCR:BAT"
                    0x03 -> "SCR:MAIN"
                    else -> "SCR:???"
                }
                decoded += "(${data[0]}) "

                decoded += when (data[1].toInt()) {
                    0x00 -> "ASS:OFF"
                    0x01 -> "ASS:1"
                    0x02 -> "ASS:2"
                    0x03 -> "ASS:3"
                    0x04 -> "ASS:P"
                    0x05 -> "ASS:R"
                    0x06 -> "ASS:4"
                    else -> "ASS:???"
                }
                decoded += "(${data[1]}) "

                if (isBitSet(data[2], 3)) {
                    decoded += "SCR:ON "
                }

                if (isBitSet(data[2], 0)) {
                    decoded += "LIGHT "
                }

                if (isBitSet(data[2], 2)) {
                    decoded += "RANGE_EXT "
                }

                decoded += "speed:${data[3].toInt().shl(8) + data[4].toInt()} "
                decoded += "trip1:${data[5].toInt().shl(24) + data[6].toInt().shl(16) + data[7].toInt().shl(8) + data[8].toInt()} "
                decoded += "trip2:${data[9].toInt().shl(24) + data[10].toInt().shl(16) + data[11].toInt().shl(8) + data[12].toInt()} "
            }
        }
        return decoded
    }

    private fun createBlinkString(name: String, input: UByte, shift: Int) = buildString {
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

    private fun createGetDataString(message: Message) = buildString {
        val req = if (message.isReq()) message else message.previous
        if (req != null && req.cmd() == message.cmd()) {
            toReqParts(req.data()).forEach { reqPart -> append(reqPart) }
        }

        if (message.isReq()) {
        } else {
            if (message.data()[0].toInt() == 0x00) {
                for (respPart in toRespParts(message.data())) {
                    append(respPart)
                }
            }
            if (message.data()[0].toInt() == 0x01) {
                append(" NOT FOUND")
            }
        }
    }


    private fun toRespParts(data: List<UByte>): List<RespPart> {
        val respData = data.drop(1)
        val result = ArrayList<RespPart>()
        var index = 0
        do {
            val array = isBitSet(respData[index], 6)
            val more = isBitSet(respData[index], 7)

            // String might be String + array?
            val string = isBitSet(respData[index], 5) && isBitSet(respData[index], 4)

            val sizeNibble = respData[index] and 0x0fu


            // For 0 each element is 1 byte, for other values each element is X nibbles
            val elementSize = if (sizeNibble.toInt() == 0) 1 else sizeNibble.toInt() / 2

            val elementCount = if (array) respData[index + 2].toInt() else 1
            val headerSize = if (array) 3 else 2
            val partSize = headerSize + (elementSize * elementCount)

            val part = respData.slice(index until index + partSize)
            index += partSize

            val elementsData = part.drop(headerSize)
            val elements = ArrayList<List<UByte>>()
            if (array) {
                if (sizeNibble.toInt() == 0) {
                    // Array of bytes
                    elements.add(elementsData)
                } else {
                    // Array of sized elements
                    for (elementIndex in 0 until elementCount) {
                        elements.add(elementsData.slice(elementIndex * elementSize until (elementIndex + 1) * elementSize))
                    }
                }
            } else {
                elements.add(elementsData)
            }

            result.add(RespPart(part[0] and 0x7fu, part[1], array, string, elements, part))
        } while (more)

        if (index != respData.size) {
        }


        return result;
    }

    inner class RespPart(val flags: UByte, val type: UByte, val array: Boolean, val string: Boolean, val elements: List<List<UByte>>, val data: List<UByte>) {
        override fun toString() = buildString {

            val sizeNibble = flags and 0x0fu  // TODO: !! Found a '5' size nibble, maybe left 3 bits are size !!! And rightmost is.. ?????
            val unsigned = !isBitSet(flags, 5) && !isBitSet(flags, 4)
            val signed = !isBitSet(flags, 5) && isBitSet(flags, 4)
            val float = isBitSet(flags, 5) && !isBitSet(flags, 4)
            val string = isBitSet(flags, 5) && isBitSet(flags, 4)

            val formatter: (value: List<UByte>) -> String = when {
                unsigned && sizeNibble.toInt() > 0 -> ::uint
                signed && sizeNibble.toInt() > 0 -> ::int
                float && sizeNibble.toInt() == 8 -> ::float32
                else -> ::hex
            }

            append(" ${hex(flags)}:${hex(type)}")
            append("(${dataIdsByInt[type] ?: "Unknown"})")
            append(
                if (array) {
                    if (string) {
                        " '" + String(elements.first().toUByteArray().asByteArray()) + "'"
                    } else {
                        elements.joinToString(prefix = " [", postfix = "]", transform = formatter)
                    }
                } else {
                    " ${formatter(elements.first())}"
                }
            )
        }
    }


    fun uint(value: List<UByte>): String {
        return BigInteger(1, value.toUByteArray().toByteArray()).toInt().toString()
    }

    fun int(value: List<UByte>): String {
        return BigInteger(value.toUByteArray().toByteArray()).toInt().toString()
    }

    fun float32(value: List<UByte>): String {
        return Float.fromBits(BigInteger(value.toUByteArray().toByteArray()).toInt()).toString()
    }

    private fun toReqParts(data: List<UByte>): List<ReqPart> {
        val result = ArrayList<ReqPart>()
        var index = 0
        do {
            val array = isBitSet(data[index], 6)
            val more = isBitSet(data[index], 7)
            val partSize = if (array) 3 else 2
            val part = data.slice(index until index + partSize)
            index += partSize

            result.add(ReqPart(part[0] and 0x7fu, part[1], array, part))
        } while (more)
        return result;
    }

    inner class ReqPart(val flags: UByte, val type: UByte, val array: Boolean, val data: List<UByte>) {
        override fun toString() = buildString {
            append(" ${hex(flags)}:${hex(type)}")
            if (array) append("[${data[2]}]")
            append("(${dataIdsByInt[type] ?: "Unknown"})")
        }
    }

    private fun createCu2UpdateDisplayString(msg: Message) = buildString {
        val data = msg.data()
        append(": ")
        append(createBlinkString("OFF", data[0], 0))
        append(createBlinkString("ECO", data[0], 2))
        append(createBlinkString("NRM", data[0], 4))
        append(createBlinkString("POW", data[0], 6))

        append(createBlinkString("WRE", data[1], 0))
        append(createBlinkString("TOT", data[1], 2))
        append(createBlinkString("TRP", data[1], 4))
        append(createBlinkString("LIG", data[1], 6))

        append(createBlinkString("BAR", data[2], 0))
        append(createBlinkString("COM", data[2], 4))
        append(createBlinkString("KM", data[2], 6))

        append("%02d%% ".format(data[3].toInt()))

        val spd = hex(data.slice(4 until 6))
        append("'${spd.slice(1 until 3)}.${spd.substring(3)}' ")

        append("'${hex(data.slice(6 until 9)).substring(1).replace('c', ' ').replace('a', '-')}' ")
    }
}