package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort

@OptIn(ExperimentalUnsignedTypes::class)

object Scan {

    enum class State {
        FLUSH, SEND_COMMAND, WAIT_RESPONSE, DONE
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val config = loadConfig()
        val comPort = SerialPort.getCommPorts()[0]
        scan(comPort, byInt(config.dataIds))
    }

    fun scan(serialPort: SerialPort, dataIdsByInt: Map<UByte, String>) {
        val decoder = GetDataDecoder(dataIdsByInt)
        serialPort.openPort()
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 225, 0)
        val results = ArrayList<Pair<List<UByte>, Message>>()
        try {
            val toScan = List(256) { it.toUByte() }.toMutableList()
            val readBuffer = ByteArray(1024)
            var request: List<UByte>? = null
            var state = State.FLUSH

            var idPos = 0
            var typePos = 0
            val allTypes = (0x00u..0xffu).map { it.toUByte() }
            val types = listOf<UByte>(0x00u, 0x04u, 0x08u, 0x14u, 0x28u, 0x70u, 0x40u, 0x44u, 0x48u) + allTypes

            val parser = MessageParser(fun(message) {
                if (state == State.WAIT_RESPONSE) {
                    if (message.tgt() == 0x04 && message.isRsp() && message.src() == 0x0C && message.isCmd(0x08)) {
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
                                results.forEach {
                                    println("Req: ${hex(it.first)}, Resp: ${hex(it.second.message)}, Decoded:${decoder.createGetDataString(it.second)}")
                                }
                                state = State.DONE
                                return
                            }
                            typePos++
                            idPos = 0
                        }
                    }
                }
            }, { message -> println("Incomplete: ${hex(message)}, crc:${hex(CRC8().crc8Bow(message.dropLast(1)))}") })

            while (state != State.DONE) {
                if (state == State.SEND_COMMAND) {
                    request = if (TypeFlags(types[typePos]).array) {
                        send(listOf(0x10u, 0xC1u, 0x43u, 0x08u, types[typePos], toScan[idPos], 0x00u), serialPort)
                    } else {
                        send(listOf(0x10u, 0xC1u, 0x42u, 0x08u, types[typePos], toScan[idPos]), serialPort)
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
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun send(cmd: List<UByte>, comPort: SerialPort): List<UByte> {
        val outList = cmd.toMutableList()
        outList.add(CRC8().crc8Bow(outList))

        val escaped = mutableListOf(outList.first())
        outList.drop(1).forEach {
            if (it.toUInt() == 0x10u) {
                escaped.add(it)
                escaped.add(it)
            } else {
                escaped.add(it)
            }
        }
        comPort.writeBytes(escaped.toUByteArray().toByteArray(), escaped.size.toLong())
        return outList
    }
}