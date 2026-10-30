package com.avdbuddy

import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class ConnectedDeviceActionsTest {
    @TempDir lateinit var root: File

    @Test fun `restart immediately stops sharing and marks the device offline before sending reboot`() = runBlocking {
        val fake = FakeAndroid(root)
        val phone = ConnectedDevice("phone", "Pixel", "device", manufacturer = "Google")
        val paired = PairedDevicesStore(File(root, "paired.json"))
        paired.save(listOf(phone))
        val commands = mutableListOf<Command>()
        val state = ConnectedDevicesState(this, { fake.settings }, DeviceNamesStore(File(root, "names")), paired,
            { TestDeviceMirror() }, { settings -> AndroidService(settings, CommandExecutor { command, _, _ ->
                commands += command
                ""
            }) })
        state.selected = phone
        state.startSharing(phone)
        state.restart(phone)
        assertNull(state.sharingSession(phone))
        assertFalse(assertNotNull(state.selected).available)
        assertFalse(state.devices.single().available)
        assertTrue(commands.isEmpty())
        coroutineContext.job.children.toList().joinAll()
        assertEquals(listOf("-s", "phone", "reboot"), commands.single().args)
        assertEquals(OperationState.Succeeded, state.action)
    }

    @Test fun `successful pairing selects and starts sharing the paired device instead of another connected device`() = runBlocking {
        val fake = FakeAndroid(root)
        val serviceFactory: (AppSettings) -> AndroidService = { settings ->
            AndroidService(settings, CommandExecutor { command, _, _ ->
                when {
                    command.args.firstOrNull() == "pair" -> "Successfully paired to 192.168.1.2:45678"
                    command.args == listOf("devices", "-l") -> "other device model:Other\n192.168.1.2:12345 device model:Pixel"
                    command.args.takeLast(2) == listOf("getprop", "ro.serialno") -> command.args[1]
                    command.args.takeLast(2) == listOf("getprop", "ro.product.manufacturer") -> "Google"
                    else -> ""
                }
            })
        }
        val state = ConnectedDevicesState(this, { fake.settings }, DeviceNamesStore(File(root, "names")),
            PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() }, serviceFactory)
        state.selected = ConnectedDevice("other", "Other", "device")
        state.showConnect = true
        state.pair(WirelessDevice("adb-pixel-abc", "192.168.1.2:45678", true), "123456")
        coroutineContext.job.children.first().join()
        val selected = assertNotNull(state.selected)
        assertEquals("192.168.1.2:12345", selected.serial)
        assertTrue(state.sharing(selected))
        assertFalse(state.showConnect)
        assertEquals(OperationState.Succeeded, state.pairing)
        state.stopSharing(selected)
    }

    @Test fun `failed pairing preserves selection without starting sharing`() = runBlocking {
        val fake = FakeAndroid(root)
        val state = ConnectedDevicesState(this, { fake.settings }, DeviceNamesStore(File(root, "names")),
            PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() },
            { settings -> AndroidService(settings, CommandExecutor { _, _, _ -> error("Pairing failed") }) })
        val original = ConnectedDevice("other", "Other", "device")
        state.selected = original
        state.showConnect = true
        state.pair(WirelessDevice("adb-pixel-abc", "192.168.1.2:45678", true), "123456")
        coroutineContext.job.children.toList().joinAll()
        assertEquals(original, state.selected)
        assertFalse(state.sharing(original))
        assertTrue(state.showConnect)
        state.openPairing()
        assertEquals(OperationState.Idle, state.pairing)
    }

    @Test fun `successful unpair reply survives a disconnected shell exit`() {
        val fake = FakeAndroid(root)
        val backend = AndroidService(fake.settings, CommandExecutor { _, _, _ ->
            throw AndroidCommandException("Result: Parcel(00000000    '....')\n", 1)
        })
        backend.revokeDeviceTrust("phone", listOf("fingerprint"))
    }

    @Test fun `failed unpair replies never expose raw Binder output`() {
        val fake = FakeAndroid(root)
        val backend = AndroidService(fake.settings, CommandExecutor { _, _, _ ->
            throw AndroidCommandException("Result: Parcel(ffffffff '....')", 1)
        })
        val failure = assertFailsWith<IllegalStateException> { backend.revokeDeviceTrust("phone", listOf("fingerprint")) }
        assertFalse(failure.message.orEmpty().contains("Parcel"))
    }

    @Test fun `failed remote unpair still removes locally and stays removed after rediscovery and restart`() = runBlocking {
        val fake = FakeAndroid(root)
        val phone = ConnectedDevice("192.168.1.2:12345", "Pixel", "device", identity = "hardware-123", manufacturer = "Google")
        val paired = PairedDevicesStore(File(root, "paired.json"))
        paired.save(listOf(phone))
        val names = DeviceNamesStore(File(root, "names"))
        val serviceFactory: (AppSettings) -> AndroidService = { settings ->
            AndroidService(settings, CommandExecutor { command, _, _ ->
                when {
                    command.args == listOf("devices", "-l") -> "192.168.1.2:12345 device model:Pixel"
                    command.args.takeLast(2) == listOf("getprop", "ro.serialno") -> "hardware-123"
                    command.args.takeLast(2) == listOf("getprop", "ro.product.manufacturer") -> "Google"
                    command.args.firstOrNull() == "disconnect" -> "disconnected"
                    "service" in command.args -> error("Result: Parcel(ffffffff '....')")
                    else -> ""
                }
            })
        }
        val state = ConnectedDevicesState(this, { fake.settings }, names, paired, { TestDeviceMirror() }, serviceFactory)
        state.selected = phone
        state.unpairTarget = phone
        state.startSharing(phone)
        state.unpair(phone)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(state.devices.isEmpty())
        assertNull(state.selected)
        assertFalse(state.sharing(phone))
        assertFalse(state.sharingRequested(phone))
        assertEquals(OperationState.Succeeded, state.action)
        assertNull(state.unpairTarget)
        assertTrue(paired.load().isEmpty())
        state.refresh()
        assertTrue(state.devices.isEmpty())
        val restarted = ConnectedDevicesState(this, { fake.settings }, names, paired, { TestDeviceMirror() }, serviceFactory)
        restarted.refresh()
        assertTrue(restarted.devices.isEmpty())
        restarted.refresh(restoring = WirelessDevice("adb-hardware-123-abc", "192.168.1.2:45678", true))
        assertEquals(phone.identity, restarted.devices.single().identity)
        assertTrue(paired.loadForgotten().isEmpty())
    }

    @Test fun `forgotten devices remain discoverable over USB without restoring wireless connections`() = runBlocking {
        val fake = FakeAndroid(root)
        val paired = PairedDevicesStore(File(root, "paired.json"))
        paired.save(emptyList(), setOf("hardware-123"))
        val names = DeviceNamesStore(File(root, "names"))
        var discovery = "hardware-123 device model:Pixel_2"
        val serviceFactory: (AppSettings) -> AndroidService = { settings ->
            AndroidService(settings, CommandExecutor { command, _, _ ->
                when {
                    command.args == listOf("devices", "-l") -> discovery
                    command.args.takeLast(2) == listOf("getprop", "ro.serialno") -> "hardware-123"
                    command.args.takeLast(2) == listOf("getprop", "ro.product.manufacturer") -> "Google"
                    else -> ""
                }
            })
        }
        val state = ConnectedDevicesState(this, { fake.settings }, names, paired, { TestDeviceMirror() }, serviceFactory)
        state.refresh()
        assertEquals("Pixel 2", state.devices.single().displayName)
        assertTrue(state.devices.single().available)
        assertEquals(setOf("hardware-123"), paired.loadForgotten())

        val restarted = ConnectedDevicesState(this, { fake.settings }, names, paired, { TestDeviceMirror() }, serviceFactory)
        restarted.refresh()
        assertEquals("hardware-123", restarted.devices.single().serial)
        discovery = "192.168.1.2:12345 device model:Pixel_2"
        restarted.refresh()
        assertTrue(restarted.devices.isEmpty())
        assertTrue(paired.load().isEmpty())

        discovery = "hardware-123 unauthorized"
        restarted.refresh()
        assertEquals("unauthorized", restarted.devices.single().status)
    }

    @Test fun `custom names survive reload and use hardware identity instead of network address`() = runBlocking {
        val file = File(root, "names.properties")
        val state = ConnectedDevicesState(this, { AppSettings(root.path, root.path) }, DeviceNamesStore(file), PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() })
        val device = ConnectedDevice("192.168.1.2:12345", "Pixel", "device", identity = "hardware-123")
        state.selected = device
        state.rename(device, "My phone")
        coroutineContext.job.children.toList().joinAll()
        assertEquals("My phone", state.selected?.displayName)
        assertEquals(mapOf("hardware-123" to "My phone"), DeviceNamesStore(file).load())
    }

    @Test fun `disconnecting retains screen sharing intent until explicitly stopped`() = runBlocking {
        val state = ConnectedDevicesState(this, { AppSettings(root.path, root.path) }, DeviceNamesStore(File(root, "names")), PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() })
        val device = ConnectedDevice("192.168.1.2:5555", "Pixel", "device", identity = "stable")
        state.startSharing(device.copy(status = "offline"))
        assertFalse(state.sharing(device))
        state.startSharing(device)
        assertTrue(state.sharing(device))
        state.refresh() // No ADB executable in this fixture: the device has disconnected.
        assertFalse(state.sharing(device))
        assertFalse(state.sharing(device.copy(status = "offline")))
        assertTrue(state.sharingRequested(device))
        state.stopSharing(device)
        assertFalse(state.sharingRequested(device))
    }

    @Test fun `pairing fingerprint uses the Android public key format`() {
        assertEquals("52:89:DF:73:7D:F5:73:26:FC:DD:22:59:7A:FB:1F:AC", adbKeyFingerprint("AQID user@computer"))
    }
}
