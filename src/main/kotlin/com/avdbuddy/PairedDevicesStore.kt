package com.avdbuddy

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.serialization.json.*

class PairedDevicesStore(private val file: File = File(HostPlatform().configDirectory, "paired-devices.json")) {
    fun load(): List<ConnectedDevice> {
        if (!file.isFile) return emptyList()
        val registry = Json.parseToJsonElement(file.readText())
        val devices = if (registry is JsonArray) registry else registry.jsonObject.getValue("devices").jsonArray
        return devices.map { element ->
            val fields = element.jsonObject
            ConnectedDevice(fields.getValue("serial").jsonPrimitive.content, fields.getValue("model").jsonPrimitive.content,
                "offline", DeviceType.valueOf(fields.getValue("type").jsonPrimitive.content), fields.getValue("identity").jsonPrimitive.content,
                manufacturer = fields["manufacturer"]?.jsonPrimitive?.contentOrNull)
        }
    }

    fun loadForgotten(): Set<String> {
        if (!file.isFile) return emptySet()
        val registry = Json.parseToJsonElement(file.readText())
        if (registry is JsonArray) return emptySet()
        return registry.jsonObject["forgotten"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.toSet()
    }

    fun save(devices: List<ConnectedDevice>, forgotten: Set<String> = loadForgotten()) {
        val entries = buildJsonArray {
            devices.forEach { device -> add(buildJsonObject {
                put("identity", device.identity)
                put("serial", device.serial)
                put("model", device.model)
                device.manufacturer?.let { put("manufacturer", it) }
                put("type", device.type.name)
            }) }
        }
        val json = buildJsonObject {
            put("devices", entries)
            put("forgotten", buildJsonArray { forgotten.forEach { add(it) } })
        }
        file.parentFile.mkdirs()
        val temporary = File.createTempFile("paired-devices-", ".tmp", file.parentFile)
        try {
            temporary.writeText(json.toString())
            Files.move(temporary.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}

internal fun mergeConnectedDevices(remembered: List<ConnectedDevice>, discovered: List<ConnectedDevice>): List<ConnectedDevice> {
    val live = discovered.associateBy { it.identity }
    return remembered.map { live[it.identity] ?: it.copy(status = "offline") } +
        discovered.filter { device -> remembered.none { it.identity == device.identity } }
}
