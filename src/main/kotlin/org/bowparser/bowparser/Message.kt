package org.bowparser.bowparser

@OptIn(ExperimentalUnsignedTypes::class)
data class Message constructor(val type: UByte, val target: UByte, val source: UByte?, val size: UByte?, val message: UByteArray, val previous: Message?) {
    fun cmd() = message[3].toInt()
    fun tgt() = target.toInt()
    fun src() = source?.toInt()
    fun isHandoff() = isType(0x00)
    fun isPingOrPong() = isType(0x03) || isType(0x04)
    fun isReq() = isType(0x01)
    fun isRsp() = isType(0x02)
    fun isReqOrRsp() = isReq() || isRsp()
    fun isCmd(cmd: Int) = isReqOrRsp() && cmd() == cmd
    fun data() = message.drop(4).dropLast(1)
    private fun isType(check: Int) = type.toInt() == check
}