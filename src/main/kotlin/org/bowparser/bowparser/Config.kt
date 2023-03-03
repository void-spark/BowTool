package org.bowparser.bowparser

data class Config(
    var types: Map<String, String>,
    var devices: Map<String, String>,
    var dataIds: Map<String, String>,
    var commands: Map<String, String>
)