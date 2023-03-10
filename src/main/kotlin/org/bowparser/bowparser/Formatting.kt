@file:OptIn(ExperimentalUnsignedTypes::class)

package org.bowparser.bowparser

import java.math.BigInteger
import java.util.*

fun hex(value: UByte) = "%02x".format(value.toInt())

fun hex(value: List<UByte>) = hex(value.toUByteArray())

fun hex(value: UByteArray) = HexFormat.of().formatHex(value.asByteArray())

fun asUint(value: List<UByte>) = BigInteger(1, value.toUByteArray().toByteArray()).toInt().toString()

fun asInt(value: List<UByte>) = BigInteger(value.toUByteArray().toByteArray()).toInt().toString()

fun asFloat32(value: List<UByte>) = Float.fromBits(BigInteger(value.toUByteArray().toByteArray()).toInt()).toString()

fun asString(value: List<UByte>) = "'${String(value.toUByteArray().asByteArray())}'"