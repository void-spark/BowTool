package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort

object Scan {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = loadConfig()
        val comPort = SerialPort.getCommPorts()[0]
        Scanner(comPort, 19200, 0x0cu, byInt(config.dataIds)).exec()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
class Scanner(serialPort: SerialPort, baudRate: Int, private val target: UByte, dataIdsByInt: Map<UByte, String>) : StdLoop(serialPort, baudRate) {

    private val toScan = List(256) { it.toUByte() }.toMutableList()
    private val allTypes = (0x00u..0xffu).map { it.toUByte() }
    private val types = listOf<UByte>(0x00u, 0x04u, 0x08u, 0x14u, 0x28u, 0x70u, 0x40u, 0x44u, 0x48u, 0x54u) + allTypes

    private var first = true
    private var idPos = 0
    private var typePos = 0
    private var request: Message? = null
    private val results = ArrayList<Pair<Message, Message>>()
    private val decoder = GetDataDecoder(dataIdsByInt)
    private var arrOffset = 0u

    fun exec(): List<Message>  {
        if (!open()) return emptyList()

        loop(if (target.toUInt() == 0x02u) Mode.WAKEUP_BAT else Mode.CHECK_BAT)
        return getMessageLog()
    }

    /**
     * It's our turn, always send a GET DATA for the value we are trying to read.
     */
    override fun sendCommand() {
        if (first) {
            log("Scanning, this might take some time. Check for TX/RX LED activity on your serial device")
            first = false;
        }
        request = null
        if (TypeFlags(types[typePos]).array) {
            sendGetDataArray(target, types[typePos], toScan[idPos], arrOffset.toUByte())
        } else {
            sendGetData(target, types[typePos], toScan[idPos])
        }
    }

    override fun handleResponse(message: Message): Result {
        if (message.tgt() == target.toInt() && message.isReq() && message.src() == pcId && message.isCmd(0x08)) {
            // Keep a copy of the parsed request we sent.
            request = message
        } else if (message.tgt() == pcId && message.isRsp() && message.src() == target.toInt() && message.isCmd(0x08)) {
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
                var storeResult = true
                var toNext = true

                val type = request!!.data()[0]
                if (TypeFlags(type).array) {
                    val respArrLength = message.data()[3]
                    if (respArrLength.toUInt() != 0u) {
                        // More to read,
                        arrOffset += respArrLength
                        toNext = false
                    } else {
                        // Nothing was left to read, don't store result
                        arrOffset = 0u
                        storeResult = false
                    }
                }
                if (storeResult) {
                    results.add(Pair(request!!, message))
                }
                if (toNext) {
                    toScan.removeAt(idPos)
                }
            }
            if (idPos == toScan.size) {
                if (toScan.size == 0 || typePos == types.size - 1) {
                    results.sortBy { msg -> msg.first.data()[1] }
                    results.forEach {
                        log("Req: ${hex(it.first.message)}, Resp: ${hex(it.second.message)}, Decoded:${decoder.createGetDataString(it.second)}")
                    }
                    log("Scan finished")
                    return Result.DONE
                }
                typePos++
                idPos = 0
            }
            return Result.SEND_COMMAND
        }
        return Result.CONTINUE
    }
}