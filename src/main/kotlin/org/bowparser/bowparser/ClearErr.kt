package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort

class ClearErr(serialPort: SerialPort, baudRate: Int) : StdLoop(serialPort, baudRate) {

    private enum class State {
        SHOW_ERR,
        CLEAR_ERR,
        CHECK_ERR
    }

    private var state = State.SHOW_ERR

    fun exec(): List<Message> {
        if (!open()) return emptyList()

        loop(Mode.WAKEUP_BAT)
        return getMessageLog()
    }

    override fun sendCommand() {
        when (state) {
            State.SHOW_ERR -> sendGetErrorState()
            State.CLEAR_ERR -> sendStoreClearErrorState()
            State.CHECK_ERR -> sendGetErrorState()
        }
    }

    override fun handleResponse(message: Message): Result {
        if (message.tgt() != pcId || !message.isRsp()) {
            return Result.CONTINUE
        }

        when (state) {
            State.SHOW_ERR -> {
                if (message.src() == batId && message.isCmd(0x08)) {
                    log("Error state stored in bat: ${hex(message.data().drop(3))}")
                    state = State.CLEAR_ERR
                    return Result.SEND_COMMAND
                }
            }


            State.CLEAR_ERR -> {
                if (message.src() == batId && message.isCmd(0x09)) {
                    log("Error state set to '0'!")
                    state = State.CHECK_ERR
                    return Result.SEND_COMMAND
                }
            }

            State.CHECK_ERR -> {
                if (message.src() == batId && message.isCmd(0x08)) {
                    log("Error state stored in bat: ${hex(message.data().drop(3))}")
                    return Result.DONE
                }
            }
        }
        return Result.CONTINUE
    }
}