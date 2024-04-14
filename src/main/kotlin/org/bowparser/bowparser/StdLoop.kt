package org.bowparser.bowparser

import com.fazecast.jSerialComm.SerialPort
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread


@OptIn(ExperimentalUnsignedTypes::class)
abstract class StdLoop(serialPort: SerialPort, baudRate: Int) : SerialOp(serialPort, baudRate) {

    private enum class State {
        // Clear everything currently in the buffer, to get rid of noise
        FLUSH,

        // Wait for the target device to wake up
        WAIT_FOR_BAT,

        // Time for use to send a command
        SEND_COMMAND,

        // Wait for a response to a command we sent
        WAIT_RESPONSE,

        // Everything is done, we'll exit the loop
        DONE
    }

    enum class Mode {
        // In this mode, we keep trying to wake the battery up.
        WAKEUP_BAT,

        // In this mode, we try to wake the battery, but go to direct mode if it doesn't respond timely.
        CHECK_BAT,

        // In this mode, we don't try to wake the battery, and drive communication ourselves.
        DIRECT
    }

    enum class Result {
        // Result of handling the response is: continue in the same state.
        CONTINUE,

        // Result of handling the response is: the next command should be sent.
        SEND_COMMAND,

        // Result of handling the response is: we are done.
        DONE
    }

    private val stdoutQueue = LinkedBlockingQueue<String>()
    private val messageLog = ArrayList<Message>(500);
    private var state = State.FLUSH
    private val readBuffer = ByteArray(1024)
    private val parser = MessageParser(this::handleMessage) { message -> log("Incomplete message: ${hex(message)}, crc:${hex(CRC8().crc8Bow(message.dropLast(1)))}") }
    private var waited = 0

    abstract fun sendCommand()

    abstract fun handleResponse(message: Message): Result

    protected fun loop(mode: Mode) {
        val logThread = thread(start = true) {
            while (true) {
                try {
                    // If we got interrupted, still empty out the queue, but don't block.
                    val logLine = if (Thread.currentThread().isInterrupted) stdoutQueue.poll() else stdoutQueue.take()
                    if (logLine == null) {
                        break;
                    }
                    println(logLine);
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }

        state = when (mode) {
            Mode.DIRECT -> State.FLUSH
            else -> State.WAIT_FOR_BAT
        }

        while (state != State.DONE) {
            // It is time for use to send a command, actual command to send is up to the implementing class.
            if (state == State.SEND_COMMAND) {
                sendCommand()
                state = State.WAIT_RESPONSE
                continue
            }

            // Read what's available, will block until the timeout if nothing is available.
            val numRead = read(readBuffer)

            // If we are busy flushing, ignore everything read and continue, unless nothing was read.
            if (state == State.FLUSH) {
                if (numRead == 0) {
                    // Nothing read, so we are done with flushing. Decide the next state.
                    state = when (mode) {
                        Mode.DIRECT -> State.SEND_COMMAND
                        else -> State.WAIT_FOR_BAT
                    }
                }
                continue
            }

            // Timeout while waiting for bytes
            if (numRead == 0) {
                if (state == State.WAIT_RESPONSE) {
                    // We were waiting for a response, if it doesn't come send our command again.
                    state = State.SEND_COMMAND
                }
                if (state == State.WAIT_FOR_BAT) {
                    waited++
                    if (waited % 5 == 0) {
                        if (mode == Mode.CHECK_BAT && waited == 20) {
                            log("No response from battery, assuming not present.")
                            state = State.SEND_COMMAND
                            continue
                        }
                        log("Bus silent, sending wake up byte.")
                        sendWakeUpByte()
                    }

                }
                continue
            }

            // Feed the parser anything we read.
            readBuffer.sliceArray(0 until numRead).forEach { if (state != State.DONE) parser.feed(it.toUByte()) }
        }

        logThread.interrupt()
        logThread.join()
    }

    protected fun log(msg: String) {
        stdoutQueue.put(msg)
    }

    protected fun getMessageLog(): List<Message> {
        return messageLog
    }

    private fun handleMessage(message: Message) {
        messageLog.add(message)
        if (state == State.WAIT_FOR_BAT) {
            if (message.tgt() == pcId) {
                if (message.isPingOrPong()) {
                    // Respond to a PING sent to us
                    sendPong(message.src()!!.toUByte())
                } else if (message.isHandoff()) {
                    // If we're given control, try to fully 'wake up' the battery.
                    sendWakeUp(batId.toUByte())
                } else if (message.isRsp() && message.src() == batId && message.cmd() == 0x14) {
                    // We got a response to our wakeup message.
                    state = State.SEND_COMMAND
                }
            }
        }

        if (state == State.WAIT_RESPONSE) {
            state = when (handleResponse(message)) {
                Result.SEND_COMMAND -> State.SEND_COMMAND
                Result.DONE -> State.DONE
                Result.CONTINUE -> state
            }
        }
    }
}
