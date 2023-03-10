package org.bowparser.bowparser

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

        if (message.isReq()) {
            toReqParts(message.data()).forEach { reqPart -> append(reqPart) }
        } else {
            if (message.data()[0].toInt() == 0x00) {
                val prevReqParts = message.previous?.takeIf { it.cmd() == message.cmd() }?.let { toReqParts(it.data()) }
                val respParts = toRespParts(message.data().drop(1))

                if (prevReqParts != null) {
                    prevReqParts.zip(respParts) { req, resp ->
                        append(req)
                        append(resp.dataToString())
                    }
                } else {
                    respParts.forEach { resp -> append(resp) }
                }
            }
            if (message.data()[0].toInt() == 0x01) {
                append(" NOT FOUND")
            }
        }
    }

    private fun toReqParts(data: List<UByte>): List<ReqPart> {
        val result = ArrayList<ReqPart>()
        var index = 0
        do {
            val type = TypeFlags(data[index])
            val partSize = if (type.array) 3 else 2
            val part = data.slice(index until index + partSize)
            index += partSize

            result.add(ReqPart(type, part[1], if(type.array) part[2].toInt() else null))
        } while (type.more)

        if(index != data.size) throw java.lang.IllegalStateException("Invalid get data request")

        return result;
    }

    inner class ReqPart(val type: TypeFlags, val id: UByte, val offset: Int?) {
        override fun toString() = buildString {
            append(" ${hex(type.typeValue)}:${hex(id)}")
            append("(${dataIdsByInt[id] ?: "Unknown"})")
            if (type.array) append("[${offset}]")
        }
    }

    private fun toRespParts(data: List<UByte>): List<RespPart> {
        val result = ArrayList<RespPart>()
        var index = 0
        do {
            val type = TypeFlags(data[index])
            val elementCount = if (type.array) data[index + 2].toInt() else 1
            val headerSize = if (type.array) 3 else 2
            val partSize = headerSize + (type.elementSize * elementCount)

            val part = data.slice(index until index + partSize)
            index += partSize

            val elementsData = part.drop(headerSize)
            val elements = ArrayList<List<UByte>>()
            if (type.array) {
                if (type.size == 0) {
                    // Array of bytes
                    elements.add(elementsData)
                } else {
                    // Array of sized elements
                    for (elementIndex in 0 until elementCount) {
                        elements.add(elementsData.slice(elementIndex * type.elementSize until (elementIndex + 1) * type.elementSize))
                    }
                }
            } else {
                elements.add(elementsData)
            }

            result.add(RespPart(type, part[1], elements))
        } while (type.more)

        if(index != data.size) throw java.lang.IllegalStateException("Invalid get data response")

        return result;
    }

    inner class RespPart(val type: TypeFlags, val id: UByte, val elements: List<List<UByte>>) {
        override fun toString() = buildString {
            append(" ${hex(type.typeValue)}:${hex(id)}")
            append("(${dataIdsByInt[id] ?: "Unknown"})")
            append(dataToString())
        }

        fun dataToString() = buildString {
            append(": ")
            append(
                if (type.array && type.size != 0) {
                    elements.joinToString(prefix = "[", postfix = "]", transform = type.formatter)
                } else {
                    type.formatter(elements.first())
                }
            )
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