package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort

fun send(cmd: List<UByte>, comPort: SerialPort) {
    val outList = cmd.toMutableList()
    outList.add(0, 0x10u)
    outList.add(CRC8().crc8Bow(outList))

    val escaped = outList.flatMapIndexed { ind, it -> if (ind != 0 && it.toUInt() == 0x10u) listOf(it, it) else listOf(it) }
    comPort.writeBytes(escaped.toUByteArray().toByteArray(), escaped.size)
}
