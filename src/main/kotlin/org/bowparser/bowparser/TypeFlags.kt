package org.bowparser.bowparser

class TypeFlags(flags: UByte) {
    /**
     * Will another value follow, more of a processing instruction than part of the type
     */
    val more = isBitSet(flags, 7)

    /**
     * Is the value an array (of bytes or elements)
     */
    val array = isBitSet(flags, 6)

    /**
     * The value/elements size. 0 indicates byte, so basically 1
     */
    val size = (flags and 0b1110u).rotateRight(1).toInt()

    /**
     * Unsigned int value/elements
     */
    private val unsigned = !isBitSet(flags, 5) && !isBitSet(flags, 4)

    /**
     * Signed int value/elements
     */
    private val signed = !isBitSet(flags, 5) && isBitSet(flags, 4)

    /**
     * Float value/elements
     */
    private val float = isBitSet(flags, 5) && !isBitSet(flags, 4)

    /**
     * The byte array is a String
     */
    val string = isBitSet(flags, 5) && isBitSet(flags, 4)

    /**
     * Size of a single element
     */
    val elementSize = if (size == 0) 1 else size

    /**
     * The correct formatter for the bytes of an array element or whole value
     */
    val formatter = when {
        unsigned && size > 0 -> ::asUint
        signed && size > 0 -> ::asInt
        float && size == 4 -> ::asFloat32
        string && size == 0 -> ::asString
        else -> ::hex
    }

    /**
     * Type bits without the 'more' bit, which isn't really part of the type
     */
    val typeValue = flags and 0b01111111u
}