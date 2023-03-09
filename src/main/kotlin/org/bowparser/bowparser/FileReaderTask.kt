package org.bowparser.bowparser

import javafx.application.Platform
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.concurrent.Task
import java.nio.file.Path

@OptIn(ExperimentalUnsignedTypes::class)
class FileReaderTask(private val path: Path, private val binary: Boolean, private val deviceByInt: Map<UByte, String>, private val decoder: Decoder) : Task<ObservableList<Message>>() {

    private val parser = MessageParser({ message ->

        val errors = decoder.check(message)
        val decoded = if (errors.isEmpty()) decoder.decode(message) else errors


        // println()

        print("tgt:${withName(message.target, deviceByInt, false)} typ:${message.type}")
        print(
            when (message.type.toInt()) {
                0x00 -> "        [${hex(message.message.take(1))}-${hex(message.message.slice(1 until 2))}-${hex(message.message.takeLast(1))}]"
                0x03, 0x04 -> " src:${withName(message.source, deviceByInt, false)} [${hex(message.message.take(1))}-${hex(message.message.slice(1 until 3))}-${hex(message.message.takeLast(1))}]"
                else -> " src:${withName(message.source, deviceByInt, false)} [${hex(message.message.take(1))}-${hex(message.message.slice(1 until 3))}-${
                    hex(
                        message.message.drop(3).dropLast(1)
                    )
                }-${hex(message.message.takeLast(1))}] [${hex(message.message.drop(3).dropLast(1))}]"
            }
        )

        println("$decoded")

        Platform.runLater { messages.get().add(message) }
    }, { message -> println("Incomplete: ${hex(message)}, crc:${hex(CRC8().crc8Bow(message.dropLast(1)))}") })


    private val messages = ReadOnlyObjectWrapper(
        this, "messages",
        FXCollections.observableArrayList(ArrayList<Message>())
    )

    fun getMessages(): ObservableList<Message> {
        return messages.get()
    }

    fun messagesProperty(): ReadOnlyObjectProperty<ObservableList<Message>> {
        return messages.readOnlyProperty
    }

    @Throws(Exception::class)
    override fun call(): ObservableList<Message> {
        try {
            FileReader().readFile(path, binary) { byte ->
                // print(hex(byte))
                parser.feed(byte)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return messages.get()
    }
}