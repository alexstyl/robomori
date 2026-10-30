package com.avdbuddy

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class ScreenSharingReconnectTest {
    @TempDir lateinit var root: File

    private fun state(scope: CoroutineScope, transport: () -> String?, mirror: () -> DeviceMirror = { TestDeviceMirror() },
        status: () -> String = { "device" }, adb: (List<String>) -> String? = { null },
        licensed: () -> Boolean = { true }, onLocked: () -> Unit = {}): ConnectedDevicesState {
        val fake = FakeAndroid(root)
        return ConnectedDevicesState(scope, { fake.settings }, DeviceNamesStore(File(root, "names")),
            PairedDevicesStore(File(root, "paired.json")), mirror, { settings ->
                AndroidService(settings, CommandExecutor { command, _, _ ->
                    adb(command.args) ?: when {
                        command.args == listOf("devices", "-l") -> transport()?.let { "$it ${status()} model:Pixel" }.orEmpty()
                        command.args.takeLast(2) == listOf("getprop", "ro.serialno") -> "hardware"
                        command.args.takeLast(2) == listOf("getprop", "ro.product.manufacturer") -> "Google"
                        else -> ""
                    }
                })
            }, licensed, onLocked)
    }

    @Test fun `USB sharing starts on discovery and reconnect and cannot be stopped while plugged in`() = runBlocking {
        var transport: String? = "USB123"
        val state = state(this, { transport })
        state.refresh()
        val phone = state.devices.single()
        val first = assertNotNull(state.sharingSession(phone))
        state.refresh()
        assertSame(first, state.sharingSession(phone))

        state.stopSharing(phone)
        assertSame(first, state.sharingSession(phone))
        transport = null
        state.refresh()
        assertNull(state.sharingSession(phone))
        transport = "USB123"
        state.refresh()
        assertTrue(state.sharing(state.devices.single()))
        assertNotSame(first, state.sharingSession(state.devices.single()))

        transport = null
        state.refresh()
        assertNull(state.sharingSession(phone))
        state.stopSharing(phone)
    }

    @Test fun `USB sharing waits for debugging authorization`() = runBlocking {
        var status = "unauthorized"
        val state = state(this, { "USB123" }, status = { status })
        state.refresh()
        assertFalse(state.sharingRequested(state.devices.single()))
        status = "device"
        state.refresh()
        val phone = state.devices.single()
        assertTrue(state.sharing(phone))
        state.stopSharing(phone)
    }

    @Test fun `sharing resumes after reconnecting on the same or a different transport`() = runBlocking {
        for (reconnected in listOf("192.168.1.2:12345", "192.168.1.8:54321")) {
            var transport: String? = "192.168.1.2:12345"
            val state = state(this, { transport })
            state.refresh()
            val phone = state.devices.single()
            state.selected = phone
            state.startSharing(phone)
            val first = assertNotNull(state.sharingSession(phone))
            transport = null
            state.refresh()
            assertNull(state.sharingSession(phone))
            assertFalse(assertNotNull(state.selected).available)
            transport = reconnected
            state.refresh()
            val restored = assertNotNull(state.selected)
            assertEquals(reconnected, restored.serial)
            assertTrue(state.sharing(restored))
            assertNotSame(first, state.sharingSession(restored))
            state.stopSharing(restored)
        }
    }

    @Test fun `stopping sharing while offline prevents automatic resume`() = runBlocking {
        var transport: String? = "192.168.1.2:12345"
        val state = state(this, { transport })
        state.refresh()
        val phone = state.devices.single()
        state.startSharing(phone)
        transport = null
        state.refresh()
        state.stopSharing(phone)
        transport = "192.168.1.8:54321"
        state.refresh()
        assertFalse(state.sharing(state.devices.single()))
        assertFalse(state.sharingRequested(phone))
    }

    @Test fun `reconnecting a device that was never shared does not start mirroring`() = runBlocking {
        var transport: String? = "192.168.1.2:12345"
        val state = state(this, { transport })
        state.refresh()
        transport = null
        state.refresh()
        transport = "192.168.1.2:12345"
        state.refresh()
        assertFalse(state.sharing(state.devices.single()))
    }

    @Test fun `a dropped mirror stream resumes even when ADB stays available`() = runBlocking {
        var attempts = 0
        val state = state(this, { "192.168.1.2:12345" }, {
            attempts++
            if (attempts == 1) object : TestDeviceMirror() {
                override fun frames(settings: AppSettings, serial: String) = flow<ImageBitmap> { error("Stream disconnected") }
            } else TestDeviceMirror()
        })
        state.refresh()
        val phone = state.devices.single()
        state.startSharing(phone)
        yield()
        val failed = assertNotNull(state.sharingSession(phone))
        assertNotNull(failed.failure)
        state.refresh()
        assertEquals(2, attempts)
        assertNotSame(failed, state.sharingSession(phone))
        assertTrue(state.sharing(phone))
        state.stopSharing(phone)
    }

    @Test fun `restarting a shared device preserves automatic resume`() = runBlocking {
        val state = state(this, { "192.168.1.2:12345" })
        state.refresh()
        val phone = state.devices.single()
        state.startSharing(phone)
        state.restart(phone)
        assertNull(state.sharingSession(phone))
        assertTrue(state.sharingRequested(phone))
        coroutineContext.job.children.toList().joinAll()
        state.refresh()
        assertTrue(state.sharing(state.devices.single()))
        state.stopSharing(phone)
    }

    @Test fun `stopping a wireless device disconnects it until sharing starts again`() = runBlocking {
        var transport: String? = "192.168.1.2:12345"
        val adb = mutableListOf<List<String>>()
        val state = state(this, { transport }, adb = { args ->
            adb += args
            when (args.firstOrNull()) {
                "disconnect" -> { transport = null; "disconnected ${args[1]}" }
                "connect" -> { transport = args[1]; "connected to ${args[1]}" }
                else -> null
            }
        })
        state.refresh()
        val phone = state.devices.single()
        state.selected = phone
        state.startSharing(phone)
        state.stopSharing(phone)
        assertFalse(assertNotNull(state.selected).available)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(listOf("disconnect", "192.168.1.2:12345") in adb)
        assertFalse(state.devices.single().available)
        assertFalse(state.sharingRequested(phone))

        val offline = state.devices.single()
        assertTrue(state.canReconnect(offline))
        state.startSharing(offline)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(listOf("connect", "192.168.1.2:12345") in adb)
        assertTrue(state.sharing(state.devices.single()))
        state.stopSharing(state.devices.single())
        coroutineContext.job.children.toList().joinAll()
    }

    @Test fun `a wireless device that cannot be reached stays stopped and explains why`() = runBlocking {
        var transport: String? = "192.168.1.2:12345"
        val state = state(this, { transport }, adb = { args ->
            when (args.firstOrNull()) {
                "disconnect" -> { transport = null; "" }
                "connect" -> "failed to connect to ${args[1]}"
                else -> null
            }
        })
        state.refresh()
        val phone = state.devices.single()
        state.startSharing(phone)
        state.stopSharing(phone)
        coroutineContext.job.children.toList().joinAll()
        state.startSharing(state.devices.single())
        coroutineContext.job.children.toList().joinAll()
        assertIs<OperationState.Failed>(state.action)
        assertFalse(state.sharingRequested(phone))
        transport = "192.168.1.2:12345"
        state.refresh()
        assertFalse(state.sharing(state.devices.single()))

        state.startSharing(state.devices.single())
        assertTrue(state.sharing(state.devices.single()))
        assertEquals(OperationState.Idle, state.action)
        state.stopSharing(state.devices.single())
        coroutineContext.job.children.toList().joinAll()
    }

    @Test fun `USB devices are never disconnected`() = runBlocking {
        val adb = mutableListOf<List<String>>()
        val state = state(this, { "USB123" }, adb = { args -> adb += args; null })
        state.refresh()
        state.stopSharing(state.devices.single())
        coroutineContext.job.children.toList().joinAll()
        assertTrue(adb.none { it.firstOrNull() == "disconnect" })
        assertTrue(state.sharing(state.devices.single()))
    }

    @Test fun `starting wireless sharing without a license asks for one instead`() = runBlocking {
        var locked = 0
        val state = state(this, { "192.168.1.2:12345" }, licensed = { false }, onLocked = { locked++ })
        state.refresh()
        val phone = state.devices.single()
        state.startSharing(phone)
        assertEquals(1, locked)
        assertNull(state.sharingSession(phone))
    }

    @Test fun `reconnecting a wireless device without a license asks for one instead`() = runBlocking {
        var locked = 0
        val adb = mutableListOf<List<String>>()
        var transport: String? = "192.168.1.2:12345"
        val state = state(this, { transport }, adb = { adb += it; null }, licensed = { false }, onLocked = { locked++ })
        state.refresh()
        transport = null
        state.refresh()
        state.startSharing(state.devices.single())
        assertEquals(1, locked)
        assertTrue(adb.none { it.firstOrNull() == "connect" })
    }

    @Test fun `USB sharing does not need a license`() = runBlocking {
        var locked = 0
        val state = state(this, { "USB123" }, licensed = { false }, onLocked = { locked++ })
        state.refresh()
        assertTrue(state.sharing(state.devices.single()))
        assertEquals(0, locked)
    }

    @Test fun `wireless sharing stops quietly when the license goes away`() = runBlocking {
        var licensed = true
        var locked = 0
        val state = state(this, { "192.168.1.2:12345" }, licensed = { licensed }, onLocked = { locked++ })
        state.refresh()
        val phone = state.devices.single()
        state.startSharing(phone)
        assertTrue(state.sharing(phone))
        licensed = false
        state.refresh()
        assertFalse(state.sharing(phone))
        assertFalse(state.sharingRequested(phone))
        assertEquals(0, locked)
    }
}
