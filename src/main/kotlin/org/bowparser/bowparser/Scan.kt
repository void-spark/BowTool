package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort

object Scan {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = loadConfig()
        val comPort = SerialPort.getCommPorts()[0]
        Scanner(comPort, 0x0Cu, byInt(config.dataIds)).scan()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
class Scanner(private val serialPort: SerialPort, private val target: UByte, dataIdsByInt: Map<UByte, String>) {
    enum class State {
        FLUSH, SEND_COMMAND, WAIT_RESPONSE, DONE
    }

    private val toScan = List(256) { it.toUByte() }.toMutableList()
    private val allTypes = (0x00u..0xffu).map { it.toUByte() }
    private val types = listOf<UByte>(0x00u, 0x04u, 0x08u, 0x14u, 0x28u, 0x70u, 0x40u, 0x44u, 0x48u, 0x54u) + allTypes

    private var state = State.FLUSH
    private var idPos = 0
    private var typePos = 0
    private val readBuffer = ByteArray(1024)
    private var request: Message? = null
    private val results = ArrayList<Pair<Message, Message>>()
    private val decoder = GetDataDecoder(dataIdsByInt)
    private val parser = MessageParser(this::handleMessage) { message -> println("Incomplete: ${hex(message)}, crc:${hex(CRC8().crc8Bow(message.dropLast(1)))}") }

    fun scan() {
        serialPort.openPort()
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 225, 0)

        while (state != State.DONE) {
            if (state == State.SEND_COMMAND) {
                request = null
                val first = (target.toInt().shl(4) or 0x01).toUByte()
                if (TypeFlags(types[typePos]).array) {
                    send(listOf(first, 0x43u, 0x08u, types[typePos], toScan[idPos], 0x00u), serialPort)
                } else {
                    send(listOf(first, 0x42u, 0x08u, types[typePos], toScan[idPos]), serialPort)
                }

                state = State.WAIT_RESPONSE

                continue
            }

            val numRead = serialPort.readBytes(readBuffer, readBuffer.size.toLong())

            if (state == State.FLUSH) {
                if (numRead == 0) {
                    state = State.SEND_COMMAND
                }
                continue
            }

            if (numRead == 0) {
                if (state == State.WAIT_RESPONSE) {
                    state = State.SEND_COMMAND
                }
                continue
            }

            val read = readBuffer.sliceArray(0 until numRead).toUByteArray()
            read.forEach { if (state != State.DONE) parser.feed(it) }
        }
    }

    private fun handleMessage(message: Message) {
        if (state == State.WAIT_RESPONSE) {
            if (message.tgt() == target.toInt() && message.isReq() && message.src() == 0x04 && message.isCmd(0x08)) {
                request = message
            } else if (message.tgt() == 0x04 && message.isRsp() && message.src() == target.toInt() && message.isCmd(0x08)) {
                state = State.SEND_COMMAND

                val response = message.data()[0]
                if (response.toUInt() == 0x01u) {
                    // No value, or invalid type requested
                    if (typePos == 0) {
                        // The first scan round uses a good type, during that round we check which values are missing.
                        toScan.removeAt(idPos)
                    } else {
                        idPos++
                    }
                } else if (response.toUInt() == 0x02u) {
                    // Requested type does not match stored type
                    idPos++
                } else {
                    results.add(Pair(request!!, message))
                    toScan.removeAt(idPos)
                }
                if (idPos == toScan.size) {
                    if (toScan.size == 0 || typePos == types.size - 1) {
                        results.sortBy { msg -> msg.first.data()[1] }
                        results.forEach {
                            println("Req: ${hex(it.first.message)}, Resp: ${hex(it.second.message)}, Decoded:${decoder.createGetDataString(it.second)}")
                        }
                        state = State.DONE
                        return
                    }
                    typePos++
                    idPos = 0
                }
            }
        }
    }
}