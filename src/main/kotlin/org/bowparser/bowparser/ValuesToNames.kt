package org.bowparser.bowparser

import java.util.stream.Collectors

fun byInt(config: Map<String, String>): Map<UByte, String> {
    return config.entries.stream().collect(Collectors.toMap({ e -> Integer.decode(e.key).toUByte() }, { e -> e.value }))
}

fun withName(target: UByte?, namesByInt: Map<UByte, String>, withValue: Boolean = true): String {
    if (target == null) {
        return "-"
    }
    val hex = Integer.toHexString(target.toInt()).padStart(2, '0')
    val targetText = namesByInt[target]

    return if (targetText != null) (if (withValue) "$targetText($hex)" else targetText) else hex
}