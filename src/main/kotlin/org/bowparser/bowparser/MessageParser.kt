package org.bowparser.bowparser

@OptIn(ExperimentalUnsignedTypes::class)
data class Message constructor(val type: UByte, val target: UByte, val source: UByte?, val size: UByte?, val message: UByteArray)

@OptIn(ExperimentalUnsignedTypes::class)
class MessageParser(
    val handler: (
        message: Message
    ) -> Unit, val incompleteHandler: (
        message: UByteArray
    ) -> Unit
) {

    private var cnt = 0u
    private var target: UByte? = null
    private var source: UByte? = null
    private var type: UByte? = null
    private var size: UByte? = null
    private var message: UByteArray? = null
    private var escaping = false

    fun feed(
        inByte: UByte,
    ) {
        val input: UByteArray

        if (escaping) {
            if (inByte.toUInt() == 0x10u) {
                input = ubyteArrayOf(0x10u)
            } else {
                input = ubyteArrayOf(0x10u, inByte)
                // Unescaped 0x10, reset
                if (cnt != 0u) {
                    if (message != null) {
                        incompleteHandler(message!!)
                    }
                    cnt = 0u
                }
                target = null
                source = null
                type = null
                size = null
                message = null
            }
            escaping = false
        } else if (inByte.toUInt() != 0x10u) {
            input = ubyteArrayOf(inByte)
        } else {
            escaping = true
            return
        }

        input.forEach { value ->
            val low: UByte = value and 0x0fu
            val high: UByte = value.rotateRight(4) and 0x0fu
            if (cnt == 0u) {
                if (value.toUInt() == 0x00u) {
                    // Ignore single '00' with no leading '10'
                    return
                }
                type = null
                size = null
                message = UByteArray(0)
            } else if (cnt == 1u) {
                target = high
                type = low
            } else if (cnt == 2u) {
                if (type!!.toUInt() == 0x00u) {
                    size = 3u
                } else {
                    source = high
                    size = if (type!!.toUInt() == 0x03u || type!!.toUInt() == 0x04u) {
                        4u
                    } else {
                        low.plus(5u).toUByte()
                    }
                }
            }

            message = message!!.plus(value)

            cnt++

            if (cnt > 2u && cnt == size!!.toUInt()) {
                handler(Message(type!!, target!!, source, size, message!!))
                cnt = 0u
            }
        }
    }
}