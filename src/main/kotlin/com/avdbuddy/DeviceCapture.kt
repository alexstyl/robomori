package com.avdbuddy

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.EncodedImageFormat

internal fun encodeRawScreenshot(raw: ByteArray): ByteArray {
    require(raw.size >= 12) { "Incomplete screenshot." }
    val header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
    val width = header.int
    val height = header.int
    val format = header.int
    require(width in 1..8192 && height in 1..8192 && format in 1..2) { "Unsupported screenshot format." }
    val pixels = width.toLong() * height * 4
    val offset = raw.size.toLong() - pixels
    require(offset == 12L || offset == 16L) { "Incomplete screenshot pixels." }
    // Preserve wide-gamut colors through Android's PNG encoder when necessary.
    require(offset != 16L || header.int == 1) { "Screenshot uses a non-sRGB color space." }
    return Bitmap().use { bitmap ->
        bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))
        check(bitmap.installPixels(raw.copyOfRange(offset.toInt(), raw.size))) { "Could not read screenshot pixels." }
        Image.makeFromBitmap(bitmap).use { image ->
            checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "Could not encode screenshot." }.use { it.bytes }
        }
    }
}

interface DeviceCapture {
    fun screenshot(settings: AppSettings, serial: String, deviceName: String): File
    fun startRecording(settings: AppSettings, serial: String, deviceName: String, deviceType: DeviceType): Recording
}

interface Recording {
    fun stop()
    fun save(): File
}

class AdbDeviceCapture(
    private val desktop: File = File(userHome, "Desktop"),
    private val processBuilder: (List<String>) -> ProcessBuilder = ::ProcessBuilder,
    private val scrcpy: () -> ScrcpyFiles = { BundledScrcpy.files },
) : DeviceCapture {
    override fun screenshot(settings: AppSettings, serial: String, deviceName: String): File {
        val file = newCaptureFile(deviceName, "png")
        try {
            if (fastScreenshot(settings, serial, file)) return file
            val process = processBuilder(adb(settings, serial) + listOf("exec-out", "screencap", "-p"))
                .redirectOutput(file).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            try {
                check(process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0 && file.length() > 0) {
                    "Could not capture the screenshot. Try again."
                }
            } finally { if (process.isAlive) process.destroyForcibly() }
            return file
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    private fun fastScreenshot(settings: AppSettings, serial: String, destination: File): Boolean {
        val compressed = Files.createTempFile("avdbuddy-screen-", ".gz").toFile()
        try {
            val process = processBuilder(adb(settings, serial) + listOf("exec-out", "screencap | gzip -1"))
                .redirectOutput(compressed).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            try {
                if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0) return false
            } finally { if (process.isAlive) process.destroyForcibly() }
            val raw = GZIPInputStream(compressed.inputStream()).use { it.readNBytes(256 * 1024 * 1024 + 17) }
            destination.writeBytes(encodeRawScreenshot(raw))
            return true
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) { return false }
        finally { compressed.delete() }
    }

    override fun startRecording(settings: AppSettings, serial: String, deviceName: String, deviceType: DeviceType): Recording {
        val before = serverPids(settings, serial)
        val files = scrcpy()
        val file = newCaptureFile(deviceName, "mp4")
        val process = try {
            processBuilder(listOf(files.executable.absolutePath, "--serial=$serial", "--no-audio",
                "--no-window", "--no-control", "--record=${file.absolutePath}"))
                .apply {
                    directory(files.executable.parentFile)
                    environment()["ADB"] = Toolchain(settings).adb.path
                    environment()["SCRCPY_SERVER_PATH"] = files.server.absolutePath
                }
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
        val pid = try { waitForServer(settings, serial, before, process) }
        catch (failure: Exception) {
            process.destroyForcibly()
            file.delete()
            throw failure
        }
        return object : Recording {
            override fun stop() {
                if (process.isAlive) runCommand(adb(settings, serial) + listOf("shell", "kill", "-2", pid.toString()))
            }

            override fun save(): File {
                process.waitFor()
                if (file.length() == 0L) {
                    file.delete()
                    error("Could not save the recording. Try again.")
                }
                return file
            }
        }
    }

    private fun waitForServer(settings: AppSettings, serial: String, before: Set<Int>, process: Process): Int {
        repeat(50) {
            check(process.isAlive) { "Could not start recording. Try again." }
            (serverPids(settings, serial) - before).singleOrNull()?.let { return it }
            Thread.sleep(100)
        }
        error("Could not start recording. Try again.")
    }

    private fun serverPids(settings: AppSettings, serial: String): Set<Int> =
        commandOutput(adb(settings, serial) + listOf("shell", "ps", "-A", "-o", "PID,NAME,ARGS"))
            .lineSequence().mapNotNull { line ->
                val fields = line.trim().split(Regex("\\s+"), limit = 3)
                fields.getOrNull(0)?.toIntOrNull()?.takeIf {
                    fields.getOrNull(1)?.startsWith("app_process") == true &&
                        fields.getOrNull(2)?.contains("com.genymobile.scrcpy.Server") == true
                }
            }.toSet()

    private fun newCaptureFile(deviceName: String, extension: String): File {
        check(desktop.isDirectory) { "Desktop is unavailable. Connect it and try again." }
        val safeName = deviceName.replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-').ifEmpty { "Device" }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        return Files.createTempFile(desktop.toPath(), "AvdBuddy-$safeName-$timestamp-", ".$extension").toFile()
    }

    private fun commandOutput(command: List<String>): String {
        val process = processBuilder(command).redirectErrorStream(true).start()
        try {
            check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
                "Could not communicate with the device. Try again."
            }
            return process.inputStream.bufferedReader().readText()
        } finally { if (process.isAlive) process.destroyForcibly() }
    }

    private fun runCommand(command: List<String>) {
        val process = processBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try { check(process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) { "Could not stop recording. Try again." } }
        finally { if (process.isAlive) process.destroyForcibly() }
    }

    private fun adb(settings: AppSettings, serial: String) = listOf(Toolchain(settings).adb.path, "-s", serial)
}
