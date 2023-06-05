package org.bowparser.bowparser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.nio.file.Files
import java.nio.file.Paths

data class Config(
    var types: Map<String, String>,
    var devices: Map<String, String>,
    var dataIds: Map<String, String>,
    var commands: Map<String, String>
)

fun loadConfig(): Config {
    val mapper = ObjectMapper(YAMLFactory())
    mapper.registerModule(KotlinModule.Builder().build())

    val appConfigPath = Paths.get("app", "config.yml")
    val localConfigPath = Paths.get("config.yml")
    val configPath = if (Files.exists(appConfigPath)) appConfigPath else localConfigPath

    return Files.newBufferedReader(configPath).use {
        mapper.readValue(it, Config::class.java)
    }
}