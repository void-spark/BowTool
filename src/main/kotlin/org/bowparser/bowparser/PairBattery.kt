package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort

object PairBattery {
    @JvmStatic
    fun main(args: Array<String>) {
        val comPort = SerialPort.getCommPorts()[0]
        DisplayPairer(comPort, 19200, 1).exec()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
class BatteryPairer(serialPort: SerialPort, baudRate: Int) : StdLoop(serialPort, baudRate) {
    private enum class State {
        GET_MOTOR_SERIAL,
        GET_STORED_SERIAL,
        PUT_SERIAL,
        CHECK_STORED_SERIAL
    }

    private var state = State.GET_MOTOR_SERIAL
    private var motorSerial: List<UByte> = emptyList()

    fun exec() {
        if (!open()) return

        loop(Mode.CHECK_BAT)
    }

    override fun sendCommand() {
        when (state) {
            State.GET_MOTOR_SERIAL -> sendGetMotorSerialFromMotor()
            State.GET_STORED_SERIAL -> sendGetMotorSerialFromBattery()
            State.PUT_SERIAL -> sendStoreMotorSerialInBattery(*motorSerial.toUByteArray())
            State.CHECK_STORED_SERIAL -> sendGetMotorSerialFromBattery()
        }
    }

    override fun handleResponse(message: Message): Result {
        if (message.tgt() != pcId || !message.isRsp()) {
            return Result.CONTINUE
        }

        when (state) {
            State.GET_MOTOR_SERIAL -> {
                if (message.src() == motorId && message.isCmd(0x08)) {
                    motorSerial = message.data().drop(4)
                    log("Motor serial: ${hex(motorSerial)}")
                    state = State.GET_STORED_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.GET_STORED_SERIAL -> {
                if (message.src() == batId && message.isCmd(0x08)) {
                    val batteryMotorSerial = message.data().drop(4)
                    log("Motor serial stored in battery: ${hex(batteryMotorSerial)}")
                    if (motorSerial.equals(batteryMotorSerial)) {
                        log("Serials already match, no change made")
                        return Result.DONE
                    }
                    state = State.PUT_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.PUT_SERIAL -> {
                if (message.src() == motorId && message.isCmd(0x09)) {
                    log("New motor serial stored in battery!")
                    state = State.CHECK_STORED_SERIAL
                    return Result.SEND_COMMAND
                }
            }

            State.CHECK_STORED_SERIAL -> {
                if (message.src() == batId && message.isCmd(0x08)) {
                    log("Motor serial stored in motor: ${hex(message.data().drop(4))}")
                    return Result.DONE
                }
            }
        }
        return Result.CONTINUE
    }
}