package com.avdbuddy

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class QrPairingTest {
    @TempDir lateinit var root: File

    @Test fun `QR pairing explicitly connects by guid when the ADB discovery list is empty`() = runBlocking {
        val fake = FakeAndroid(root)
        val pairing = QrPairing.create()
        var connected = false
        val commands = mutableListOf<Command>()
        val state = ConnectedDevicesState(this, { fake.settings }, DeviceNamesStore(File(root, "names")),
            PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() }, { settings ->
                AndroidService(settings, CommandExecutor { command, _, _ ->
                    commands += command
                    when {
                        command.args.firstOrNull() == "pair" -> "Enter pairing code: Successfully paired to 192.168.1.2:12345 [guid=adb-hardware-abc]"
                        command.args == listOf("connect", "adb-hardware-abc._adb-tls-connect._tcp") -> {
                            connected = true
                            "connected to adb-hardware-abc._adb-tls-connect._tcp"
                        }
                        command.args == listOf("devices", "-l") -> if (connected) "adb-hardware-abc._adb-tls-connect._tcp device model:Pixel" else ""
                        command.args == listOf("mdns", "services") -> error("ADB discovery missed the existing connection service")
                        command.args.takeLast(2) == listOf("getprop", "ro.serialno") -> "hardware"
                        command.args.takeLast(2) == listOf("getprop", "ro.product.manufacturer") -> "Google"
                        else -> ""
                    }
                })
            })
        state.pairQr(WirelessDevice(pairing.serviceName, "192.168.1.2:12345", true), pairing.password)
        coroutineContext.job.children.first().join()
        val selected = assertNotNull(state.selected)
        assertTrue(state.sharing(selected))
        assertEquals(1, commands.count { it.args.firstOrNull() == "connect" })
        state.stopSharing(selected)
    }

    @Test fun `QR pairing recognizes an automatically connected device using the returned guid`() = runBlocking {
        val fake = FakeAndroid(root)
        val pairing = QrPairing.create()
        val paired = PairedDevicesStore(File(root, "paired.json"))
        paired.save(emptyList(), setOf("hardware"))
        val state = ConnectedDevicesState(this, { fake.settings }, DeviceNamesStore(File(root, "names")),
            paired, { TestDeviceMirror() }, { settings ->
                AndroidService(settings, CommandExecutor { command, _, _ ->
                    when {
                        command.args.firstOrNull() == "pair" -> "Enter pairing code: Successfully paired to 192.168.1.2:12345 [guid=adb-hardware-abc]\n"
                        command.args == listOf("devices", "-l") -> "adb-hardware-abc._adb-tls-connect._tcp device model:Pixel"
                        command.args == listOf("mdns", "services") -> error("The connection service is no longer advertised")
                        command.args.takeLast(2) == listOf("getprop", "ro.serialno") -> "hardware"
                        command.args.takeLast(2) == listOf("getprop", "ro.product.manufacturer") -> "Google"
                        else -> ""
                    }
                })
            })
        state.showConnect = true
        state.pairQr(WirelessDevice(pairing.serviceName, "192.168.1.2:12345", true), pairing.password)
        coroutineContext.job.children.first().join()
        val selected = assertNotNull(state.selected)
        assertEquals("hardware", selected.identity)
        assertTrue(state.sharing(selected))
        assertFalse(state.showConnect)
        assertTrue(paired.loadForgotten().isEmpty())
        state.stopSharing(selected)
    }

    @Test fun `generated QR code decodes to the Android wireless pairing payload`() {
        val pairing = QrPairing.create()
        val matrix = pairing.matrix()
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels)))
        val decoded = QRCodeReader().decode(bitmap, mapOf(DecodeHintType.PURE_BARCODE to true))
        assertEquals("WIFI:T:ADB;S:${pairing.serviceName};P:${pairing.password};;", decoded.text)
        assertTrue(pairing.serviceName.startsWith("studio-"))
        assertTrue(pairing.password.length >= 20)
        val next = QrPairing.create()
        assertNotEquals(pairing.serviceName, next.serviceName)
        assertNotEquals(pairing.password, next.password)
    }

    @Test fun `QR discovery only accepts the service requested by this QR code`() = runBlocking {
        val fake = FakeAndroid(root)
        val pairing = QrPairing.create()
        val state = ConnectedDevicesState(this, { fake.settings }, DeviceNamesStore(File(root, "names")),
            PairedDevicesStore(File(root, "paired.json")), serviceFactory = { settings ->
                AndroidService(settings, CommandExecutor { _, _, _ -> """
                    ${pairing.serviceName} _adb-tls-pairing._tcp 192.168.1.2:12345
                    studio-other _adb-tls-pairing._tcp 192.168.1.3:12345
                    adb-phone-abc _adb-tls-pairing._tcp 192.168.1.4:12345
                """.trimIndent() })
            })
        state.discover(pairing.serviceName)
        assertEquals(pairing.serviceName, state.nearby.single().name)
        state.discover()
        assertEquals("adb-phone-abc", state.nearby.single().name)
    }

    @Test fun `QR pairing sends its secret over stdin and starts sharing the matching connection service`() = runBlocking {
        val fake = FakeAndroid(root)
        val pairing = QrPairing.create()
        val commands = mutableListOf<Command>()
        var connected = false
        val state = ConnectedDevicesState(this, { fake.settings }, DeviceNamesStore(File(root, "names")),
            PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() }, { settings ->
                AndroidService(settings, CommandExecutor { command, _, _ ->
                    commands += command
                    when {
                        command.args.firstOrNull() == "pair" -> "Successfully paired to 192.168.1.2:12345"
                        command.args.firstOrNull() == "connect" -> {
                            connected = true
                            "connected to 192.168.1.2:54321"
                        }
                        command.args == listOf("devices", "-l") -> if (connected) "adb-hardware-abc._adb-tls-connect._tcp device model:Pixel" else ""
                        command.args == listOf("mdns", "services") -> "adb-hardware-abc _adb-tls-connect._tcp 192.168.1.2:54321"
                        command.args.takeLast(2) == listOf("getprop", "ro.serialno") -> "hardware"
                        command.args.takeLast(2) == listOf("getprop", "ro.product.manufacturer") -> "Google"
                        else -> ""
                    }
                })
            })
        state.showConnect = true
        state.pairQr(WirelessDevice(pairing.serviceName, "192.168.1.2:12345", true), pairing.password)
        coroutineContext.job.children.first().join()
        val pair = commands.single { it.args.firstOrNull() == "pair" }
        assertEquals(listOf("pair", "192.168.1.2:12345"), pair.args)
        assertEquals("${pairing.password}\n", pair.input)
        val selected = assertNotNull(state.selected)
        assertEquals("hardware", selected.identity)
        assertTrue(state.sharing(selected))
        assertFalse(state.showConnect)
        state.stopSharing(selected)
    }
}
