package com.avdbuddy

import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class DeviceCaptureTest {
    @TempDir lateinit var desktop: File
    private val settings = AppSettings("/unused/sdk", "/unused/avds")

    @Test fun `bundled scrcpy is executable and includes its Android server`() {
        val files = BundledScrcpy.files
        assertTrue(files.executable.canExecute())
        assertTrue(files.server.isFile)
        val process = ProcessBuilder(files.executable.absolutePath, "--version")
            .redirectErrorStream(true).start()
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals(0, process.exitValue(), process.inputStream.bufferedReader().readText())
    }

    @Test fun `bundled scrcpy records an MP4 on a connected emulator`() {
        val serial = System.getenv("AVDBUDDY_TEST_RECORDING_SERIAL")
        assumeTrue(!serial.isNullOrBlank())
        val adb = System.getenv("AVDBUDDY_TEST_RECORDING_ADB") ?: "adb"
        val capture = AdbDeviceCapture(desktop)
        val recording = capture.startRecording(settings.copy(adb = adb), serial, "Recording Test", DeviceType.PHONE)
        Thread.sleep(3_000)
        recording.stop()
        val file = recording.save()
        assertTrue(file.length() > 1_000, "Expected a finalized MP4 recording")
        assertEquals("mp4", file.extension)
    }

    private fun licensed(scope: CoroutineScope) = licensedForTest(scope, File(desktop, "license"))

    @Test fun `unsupported fast capture falls back to Android PNG and removes failed captures`() {
        assumeTrue(HostOs.detect() != HostOs.WINDOWS)
        val commands = mutableListOf<List<String>>()
        var fail = false
        val capture = AdbDeviceCapture(desktop, processBuilder = { command ->
            commands += command
            ProcessBuilder("sh", "-c", if (fail) "exit 1" else "printf 'PNG bytes'")
        })
        val file = capture.screenshot(settings, "emulator-5554", "Pixel 10")
        assertEquals(desktop, file.parentFile)
        assertTrue(file.name.endsWith(".png"))
        assertEquals("PNG bytes", file.readText())
        assertEquals(listOf("exec-out", "screencap", "-p"), commands.last().takeLast(3))

        fail = true
        assertFailsWith<IllegalStateException> { capture.screenshot(settings, "emulator-5554", "Pixel 10") }
        assertEquals(listOf(file), desktop.listFiles()?.toList())
    }

    @Test fun `recording uses bundled scrcpy to save an MP4 without a time limit`() {
        assumeTrue(HostOs.detect() != HostOs.WINDOWS)
        val commands = mutableListOf<List<String>>()
        var psCalls = 0
        val bundled = File(desktop, "scrcpy").apply { mkdirs() }
        val capture = AdbDeviceCapture(desktop, { command ->
            commands += command
            when {
                "ps" in command -> ProcessBuilder("sh", "-c", if (psCalls++ == 0) "printf 'PID NAME ARGS\\n'"
                    else "printf 'PID NAME ARGS\\n1234 app_process app_process / com.genymobile.scrcpy.Server 5.0.1\\n'")
                "kill" in command -> ProcessBuilder("sh", "-c", "exit 0")
                else -> ProcessBuilder("sh", "-c", "printf 'MP4 bytes' > \"\$1\"; sleep 1", "sh",
                    command.first { it.startsWith("--record=") }.removePrefix("--record="))
            }
        }, { ScrcpyFiles(File(bundled, "scrcpy"), File(bundled, "scrcpy-server")) })
        val recording = capture.startRecording(settings, "emulator-5554", "Pixel 10", DeviceType.PHONE)
        recording.stop()
        val file = recording.save()
        assertEquals(desktop, file.parentFile)
        assertTrue(file.name.endsWith(".mp4"))
        assertEquals("MP4 bytes", file.readText())
        assertTrue(commands.any { "--no-window" in it && "--no-audio" in it && "--no-control" in it })
        assertTrue(commands.none { it.any { arg -> arg.contains("time-limit") || arg == "screenrecord" } })
        assertTrue(commands.any { it.takeLast(2) == listOf("-2", "1234") })
    }

    @Test fun `Wear recording uses the same unlimited MP4 recorder`() {
        assumeTrue(HostOs.detect() != HostOs.WINDOWS)
        val commands = mutableListOf<List<String>>()
        var psCalls = 0
        val bundled = File(desktop, "scrcpy").apply { mkdirs() }
        val capture = AdbDeviceCapture(desktop, { command ->
            commands += command
            when {
                "ps" in command -> ProcessBuilder("sh", "-c", if (psCalls++ == 0) "printf 'PID NAME ARGS\\n'"
                    else "printf '2345 app_process app_process / com.genymobile.scrcpy.Server 5.0.1\\n'")
                "kill" in command -> ProcessBuilder("sh", "-c", "exit 0")
                else -> ProcessBuilder("sh", "-c", "printf 'MP4 bytes' > \"\$1\"; sleep 1", "sh",
                    command.first { it.startsWith("--record=") }.removePrefix("--record="))
            }
        }, { ScrcpyFiles(File(bundled, "scrcpy"), File(bundled, "scrcpy-server")) })
        val recording = capture.startRecording(settings, "emulator-5554", "Pixel Watch", DeviceType.WEAR)
        recording.stop()
        val file = recording.save()
        assertEquals("MP4 bytes", file.readText())
        assertEquals("mp4", file.extension)
        assertTrue(commands.any { it.first() == File(bundled, "scrcpy").absolutePath })
    }

    @Test fun `capture controls show their result after screenshot and recording stop`() = runBlocking {
        withTimeout(5_000) {
            val fake = FakeAndroid(desktop)
            val device = fake.device("Pixel_10")
            fake.active[device.name] = "emulator-5554"
            val stopped = CountDownLatch(1)
            val screenshot = File(desktop, "capture.png").apply { writeText("png") }
            val video = File(desktop, "recording.mp4").apply { writeText("mp4") }
            val capture = object : DeviceCapture {
                override fun screenshot(settings: AppSettings, serial: String, deviceName: String) = screenshot
                override fun startRecording(settings: AppSettings, serial: String, deviceName: String, deviceType: DeviceType) = object : Recording {
                    override fun stop() { stopped.countDown() }
                    override fun save(): File {
                        check(stopped.await(3, TimeUnit.SECONDS))
                        return video
                    }
                }
            }
            val state = AppState(this, initial = fake.settings, executor = fake, capture = capture, license = licensed(this))
            state.refresh()
            state.captureScreenshot(device)
            while (state.captureNotices[device.name] == null) yield()
            assertEquals(CaptureNotice.Saved(CaptureKind.Screenshot, screenshot), state.captureNotices[device.name])

            state.toggleRecording(device)
            while (state.recordingDevices[device.name] != true) yield()
            assertTrue(state.recordingStartedAt.containsKey(device.name))
            assertFalse(state.showDemoDialog)
            state.toggleRecording(device)
            // Starting the recording replaced the screenshot result; saving it shows the recording's.
            while (state.captureNotices[device.name] != CaptureNotice.Saved(CaptureKind.Recording, video)) yield()
            assertFalse(state.recordingDevices.containsKey(device.name))
            assertFalse(state.recordingStartedAt.containsKey(device.name))
        }
    }

    @Test fun `demo pairing opens the purchase dialog instead of pairing`() = runBlocking {
        val fake = FakeAndroid(desktop)
        val state = AppState(this, initial = fake.settings, executor = fake, license = AppLicense(this, File(desktop, "no-license")))

        state.pairDeviceWirelessly()

        assertTrue(state.showDemoDialog)
        assertFalse(state.connected.showConnect)
    }

    @Test fun `licensed pairing opens the pairing dialog`() = runBlocking {
        val fake = FakeAndroid(desktop)
        val state = AppState(this, initial = fake.settings, executor = fake, license = licensed(this))

        state.pairDeviceWirelessly()

        assertTrue(state.connected.showConnect)
        assertFalse(state.showDemoDialog)
    }

    @Test fun `demo recording opens the purchase dialog without starting capture`() = runBlocking {
        val fake = FakeAndroid(desktop)
        val device = fake.device("Pixel_10")
        fake.active[device.name] = "emulator-5554"
        var recordingStarted = false
        val capture = object : DeviceCapture {
            override fun screenshot(settings: AppSettings, serial: String, deviceName: String) = File(desktop, "screenshot.png")
            override fun startRecording(settings: AppSettings, serial: String, deviceName: String, deviceType: DeviceType): Recording {
                recordingStarted = true
                error("Recording should stay locked")
            }
        }
        val state = AppState(this, initial = fake.settings, executor = fake, capture = capture,
            license = AppLicense(this, File(desktop, "no-license")))
        state.refresh()

        state.toggleRecording(device)

        assertTrue(state.showDemoDialog)
        assertTrue(state.hasOpenDialog)
        assertFalse(recordingStarted)
        assertFalse(state.recordingDevices.containsKey(device.name))
    }
}
