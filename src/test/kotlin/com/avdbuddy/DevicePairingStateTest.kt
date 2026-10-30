package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class DevicePairingStateTest {
    @TempDir lateinit var root: File
    private val usb = ConnectedDevice("USB123", "Pixel", "device", identity = "hardware-123")
    private val wireless = usb.copy(serial = "192.168.1.2:12345")

    private fun state(scope: CoroutineScope, paired: List<ConnectedDevice> = emptyList(),
        executor: CommandExecutor = CommandExecutor { _, _, _ -> error("Unexpected command") }): ConnectedDevicesState {
        val fake = FakeAndroid(root)
        val store = PairedDevicesStore(File(root, "paired.json"))
        store.save(paired)
        return ConnectedDevicesState(scope, { fake.settings }, DeviceNamesStore(File(root, "names")), store,
            { TestDeviceMirror() }, { settings -> AndroidService(settings, executor) })
    }

    @Test fun `USB connection alone is not a pairing`(): Unit = runBlocking {
        assertFalse(state(this).isPaired(usb))
    }

    @Test fun `USB transport retains an existing wireless pairing by hardware identity`(): Unit = runBlocking {
        assertTrue(state(this, listOf(wireless)).isPaired(usb))
    }

    @Test fun `offline paired device remains paired`(): Unit = runBlocking {
        assertTrue(state(this, listOf(wireless)).isPaired(wireless.copy(status = "offline")))
    }

    @Test fun `another device pairing does not pair a USB device`(): Unit = runBlocking {
        assertFalse(state(this, listOf(wireless.copy(identity = "other-hardware"))).isPaired(usb))
    }

    @Test fun `unpairing a USB-only device sends no commands`(): Unit = runBlocking {
        val commands = mutableListOf<Command>()
        val state = state(this, executor = CommandExecutor { command, _, _ -> commands += command; "" })
        state.unpair(usb)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(commands.isEmpty())
    }
}
