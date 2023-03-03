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
        var errors = ""
        if (message.message.size != message.size!!.toInt()) {
            errors += " SIZE MISMATCH"
        }

        if (CRC8().crc8Bow(message.message.dropLast(1)) != message.message.last()) {
            errors += " CRC MISMATCH"
        }

        val decoded = decoder.decode(message)


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

        println("$errors - $decoded")

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
        FileReader().readFile(path, binary) { byte ->
            // print(hex(byte))
            parser.feed(byte)
        }
        return messages.get()
    }
}