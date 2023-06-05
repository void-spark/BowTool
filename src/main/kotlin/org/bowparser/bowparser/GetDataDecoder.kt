package org.bowparser.bowparser

class GetDataDecoder(private val dataIdsByInt: Map<UByte, String>) {

    fun createGetDataString(message: Message) = buildString {
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

            result.add(ReqPart(type, part[1], if (type.array) part[2].toInt() else null))
        } while (type.more)

        if (index != data.size) throw java.lang.IllegalStateException("Invalid get data request")

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

        if (index != data.size) throw java.lang.IllegalStateException("Invalid get data response")

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
}