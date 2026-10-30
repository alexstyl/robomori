package com.avdbuddy

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

data class EmulatorFrame(val bitmap: ImageBitmap, val rotation: Int)

interface DeviceDisplay {
    fun stream(settings: AppSettings, device: Device, serial: String): Flow<EmulatorFrame>
    fun touch(serial: String, x: Int, y: Int, pressed: Boolean)
    fun input(settings: AppSettings, serial: String, arguments: List<String>)
    fun type(serial: String, text: String)
    fun paste(settings: AppSettings, serial: String, text: String)
    fun rotate(serial: String, rotation: Int)
    fun fold(settings: AppSettings, serial: String, folded: Boolean)
}

class AdbDeviceDisplay(private val processBuilder: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }) : DeviceDisplay {
    private val live = EmulatorScreenStream()
    override fun rotate(serial: String, rotation: Int) = live.rotate(serial, rotation)
    override fun touch(serial: String, x: Int, y: Int, pressed: Boolean) = live.touch(serial, x, y, pressed)

    override fun stream(settings: AppSettings, device: Device, serial: String): Flow<EmulatorFrame> = live.frames(device, serial)

    override fun input(settings: AppSettings, serial: String, arguments: List<String>) =
        adb(settings, serial, listOf("shell", "input") + arguments, "Could not send input to the emulator.")

    override fun type(serial: String, text: String) = live.type(serial, text)

    override fun paste(settings: AppSettings, serial: String, text: String) {
        live.setClipboard(serial, text)
        adb(settings, serial, listOf("shell", "input", "keyevent", "279"), "Could not paste into the emulator.")
    }

    // The emulator console switches the hinge posture and the screen that goes with it.
    override fun fold(settings: AppSettings, serial: String, folded: Boolean) =
        adb(settings, serial, listOf("emu", if (folded) "fold" else "unfold"), "Could not ${if (folded) "fold" else "unfold"} the emulator.")

    private fun adb(settings: AppSettings, serial: String, arguments: List<String>, failure: String) {
        val process = processBuilder(listOf(Toolchain(settings).adb.path, "-s", serial) + arguments)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        try { check(process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) { failure } }
        finally { if (process.isAlive) process.destroyForcibly() }
    }
}

internal fun screenPoint(x: Float, y: Float, viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): Pair<Int, Int> =
    ((x / viewWidth.coerceAtLeast(1) * imageWidth).toInt().coerceIn(0, imageWidth - 1)) to
        ((y / viewHeight.coerceAtLeast(1) * imageHeight).toInt().coerceIn(0, imageHeight - 1))

internal fun nativeScreenSize(imageWidth: Int, imageHeight: Int, rotation: Int): Pair<Int, Int> =
    if (rotation % 2 == 0) imageWidth to imageHeight else imageHeight to imageWidth
