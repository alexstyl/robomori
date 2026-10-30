package com.avdbuddy

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

class DeviceNamesStore(private val file: File = File(HostPlatform().configDirectory, "device-names.properties")) {
    fun load(): Map<String, String> = Properties().apply {
        if (file.isFile) file.inputStream().use(::load)
    }.entries.associate { it.key.toString() to it.value.toString() }

    fun save(names: Map<String, String>) {
        file.parentFile.mkdirs()
        val temporary = File.createTempFile("device-names-", ".tmp", file.parentFile)
        try {
            temporary.outputStream().use { output -> Properties().apply { putAll(names) }.store(output, null) }
            Files.move(temporary.toPath(), file.toPath(), REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
}
