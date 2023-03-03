package org.bowparser.bowparser

fun isBitSet(value: UByte, index: Int): Boolean {
    return (value.rotateRight(index) and 0x01u) == 0x01u.toUByte()
}