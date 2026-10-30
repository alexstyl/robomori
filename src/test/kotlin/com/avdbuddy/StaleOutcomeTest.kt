package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

/** An outcome must not outlive the action that produced it or show for something else. */
class StaleOutcomeTest {
    @TempDir lateinit var root: File

    private fun CoroutineScope.connected() = ConnectedDevicesState(this, { AppSettings(root.path, root.path) },
        DeviceNamesStore(File(root, "names")), PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() },
        { AndroidService(it, CommandExecutor { _, _, _ -> error("Device unavailable") }) })

    @Test fun `a failed device action only shows for that device`() = runBlocking {
        val state = connected()
        val failing = ConnectedDevice("USB-A", "Pixel", "device")
        val other = ConnectedDevice("USB-B", "Pixel", "device")
        state.restart(failing)
        coroutineContext.job.children.toList().joinAll()
        assertIs<OperationState.Failed>(state.actionFor(failing))
        assertEquals(OperationState.Idle, state.actionFor(other))
    }

    @Test fun `closing a device dialog clears the outcome it showed`() = runBlocking {
        val state = connected()
        val phone = ConnectedDevice("USB-A", "Pixel", "device")
        state.restart(phone)
        coroutineContext.job.children.toList().joinAll()
        state.renameTarget = phone
        state.dismissRename()
        assertEquals(OperationState.Idle, state.actionFor(phone))

        state.restart(phone)
        coroutineContext.job.children.toList().joinAll()
        state.dismissUnpair()
        assertEquals(OperationState.Idle, state.actionFor(phone))
    }

    @Test fun `opening settings forgets an earlier license outcome`() = runBlocking {
        val fake = FakeAndroid(root)
        val license = AppLicense(this, File(root, "license"))
        val state = AppState(this, initial = fake.settings, executor = fake, license = license)
        license.activate("")
        assertEquals(LicenseOperation.MissingKey, license.operation)
        state.openSettings(SettingsSection.License)
        assertEquals(LicenseOperation.Idle, license.operation)
    }
}
