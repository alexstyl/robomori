package com.avdbuddy

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*

class PairedDeviceConnectionTest {
    @TempDir lateinit var root: File
    private val device = WirelessDevice("studio-session", "192.168.1.2:12345", true)

    @Test fun `transient GUID connection failures retry automatically until transport is ready`() {
        val fake = FakeAndroid(root)
        var connections = 0
        var pauses = 0
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ ->
            when {
                command.args.firstOrNull() == "connect" -> {
                    connections++
                    if (connections < 3) throw AndroidCommandException("connection refused", 1)
                    "connected to adb-hardware-abc._adb-tls-connect._tcp"
                }
                command.args == listOf("devices", "-l") -> if (connections >= 3) "adb-hardware-abc._adb-tls-connect._tcp device model:Pixel" else ""
                else -> ""
            }
        })
        val result = service.awaitPairedConnection(device, "adb-hardware-abc", AtomicBoolean(), attempts = 3, pause = { pauses++ })
        assertTrue(result.matches(ConnectedDevice("adb-hardware-abc._adb-tls-connect._tcp", "Pixel", "device")))
        assertEquals(3, connections)
        assertEquals(2, pauses)
    }

    @Test fun `failed IP connections retry instead of aborting the paired operation`() {
        val fake = FakeAndroid(root)
        var connections = 0
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ ->
            when {
                command.args.firstOrNull() == "connect" -> {
                    connections++
                    if (connections < 3) throw AndroidCommandException("connection refused", 1)
                    "connected to 192.168.1.2:54321"
                }
                command.args == listOf("devices", "-l") -> if (connections >= 3) "192.168.1.2:54321 device model:Pixel" else ""
                command.args == listOf("mdns", "services") -> "adb-hardware-abc _adb-tls-connect._tcp 192.168.1.2:54321"
                else -> ""
            }
        })
        val result = service.awaitPairedConnection(device, null, AtomicBoolean(), attempts = 3, pause = {})
        assertEquals("192.168.1.2:54321", result.address)
        assertEquals(3, connections)
    }

    @Test fun `a connect success reply waits for an available ADB transport`() {
        val fake = FakeAndroid(root)
        var reads = 0
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ ->
            when {
                command.args.firstOrNull() == "connect" -> "connected to adb-hardware-abc._adb-tls-connect._tcp"
                command.args == listOf("devices", "-l") -> {
                    reads++
                    "adb-hardware-abc._adb-tls-connect._tcp ${if (reads >= 4) "device" else "offline"} model:Pixel"
                }
                else -> ""
            }
        })
        service.awaitPairedConnection(device, "adb-hardware-abc", AtomicBoolean(), attempts = 3, pause = {})
        assertEquals(4, reads)
    }

    @Test fun `exhausted retries produce a final failure without asking for another attempt`() {
        val fake = FakeAndroid(root)
        var connections = 0
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ ->
            if (command.args.firstOrNull() == "connect") {
                connections++
                throw AndroidCommandException("connection refused", 1)
            }
            ""
        })
        val failure = assertFailsWith<IllegalStateException> {
            service.awaitPairedConnection(device, "adb-hardware-abc", AtomicBoolean(), attempts = 3, pause = {})
        }
        assertEquals(3, connections)
        assertEquals("Could not connect to your device.", failure.message)
    }

    @Test fun `cancellation stops retries without another connection command`() {
        val fake = FakeAndroid(root)
        val cancelled = AtomicBoolean()
        var connections = 0
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ ->
            if (command.args.firstOrNull() == "connect") connections++
            ""
        })
        assertFailsWith<CancellationException> {
            service.awaitPairedConnection(device, "adb-hardware-abc", cancelled, pause = { cancelled.set(true) })
        }
        assertEquals(1, connections)
    }
}
