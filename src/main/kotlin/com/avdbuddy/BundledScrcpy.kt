package com.avdbuddy

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

data class ScrcpyFiles(val executable: File, val server: File)

internal object BundledScrcpy {
    val files: ScrcpyFiles by lazy(::extract)

    private fun extract(): ScrcpyFiles {
        val archive = javaClass.classLoader.getResourceAsStream("scrcpy/scrcpy-bundle.zip")
            ?: error("Bundled screen recorder is unavailable.")
        val directory = Files.createTempDirectory("avdbuddy-scrcpy-")
        directory.toFile().deleteOnExit()
        ZipInputStream(archive).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = directory.resolve(entry.name).normalize()
                check(target.startsWith(directory)) { "Invalid screen recorder archive." }
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                    target.toFile().deleteOnExit()
                }
                else {
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target)
                    target.toFile().deleteOnExit()
                }
                zip.closeEntry()
            }
        }
        val executableName = if (HostOs.detect() == HostOs.WINDOWS) "scrcpy.exe" else "scrcpy"
        val executable = Files.walk(directory).use { paths ->
            paths.filter { it.fileName.toString() == executableName }.findFirst().orElse(null)
        }?.toFile() ?: error("Bundled screen recorder is incomplete.")
        val server = File(executable.parentFile, "scrcpy-server")
        check(server.isFile && (HostOs.detect() == HostOs.WINDOWS || executable.setExecutable(true))) {
            "Bundled screen recorder is incomplete."
        }
        return ScrcpyFiles(executable, server)
    }
}
