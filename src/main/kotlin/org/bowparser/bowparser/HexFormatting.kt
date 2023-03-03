@file:OptIn(ExperimentalUnsignedTypes::class)

package org.bowparser.bowparser

import java.util.*

fun hex(value: UByte): String {
    return "%02x".format(value.toInt())
}

fun hex(value: List<UByte>): String {
    return HexFormat.of().formatHex(value.toUByteArray().toByteArray())
}

fun hex(value: UByteArray): String {
    return HexFormat.of().formatHex(value.asByteArray())
}