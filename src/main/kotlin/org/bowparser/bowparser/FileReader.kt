package org.bowparser.bowparser

import java.nio.file.Files
import java.nio.file.Path

class FileReader {

    fun readFile(path: Path, binary: Boolean, handler: (byte: UByte) -> Unit) {
        if (binary) {
            Files.newInputStream(path).buffered().iterator().forEach { byte -> handler.invoke(byte.toUByte()) }
        } else {
            var left = true
            var cur = ""

            Files.newInputStream(path).buffered().iterator().forEach { byte ->
                val char = byte.toInt().toChar()
                if (char.isLetterOrDigit()) {
                    if (left) {
                        cur = "" + char
                        left = false
                    } else {
                        cur += char
                        left = true

                        val value = Integer.parseInt(cur, 16).toUByte()
                        handler.invoke(value)
                    }
                }
            }
        }
    }
}