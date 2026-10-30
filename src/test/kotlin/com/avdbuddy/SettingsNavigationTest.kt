package com.avdbuddy

import java.io.File
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class SettingsNavigationTest {
    @TempDir lateinit var root: File

    @Test fun `settings navigation preserves the selected emulator and preview`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Pixel")
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake)
        state.refresh()
        state.selectDevice(device)
        fake.commands.clear()
        state.openSettings(SettingsSection.Storage)
        assertEquals(SettingsSection.Storage, state.settingsSection)
        state.openSettings(SettingsSection.Updates)
        assertTrue(state.showSettings)
        assertEquals(setOf(device.name), state.selected)
        assertEquals(device.name, state.previewName)
        state.showSettings = false
        assertEquals(device.name, state.previewDevice?.name)
        assertTrue(fake.commands.isEmpty())
        state.openSettings()
        assertEquals(SettingsSection.Updates, state.settingsSection)
    }

    @Test fun `saving unchanged storage stays in settings and preserves emulator selection`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Pixel")
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake)
        state.refresh()
        state.selectDevice(device)
        state.openSettings(SettingsSection.Storage)
        assertEquals(SettingsSaveResult.Saved, state.saveSettings(fake.settings.sdk, fake.settings.avds))
        assertTrue(state.showSettings)
        assertEquals(SettingsSection.Storage, state.settingsSection)
        assertEquals(setOf(device.name), state.selected)
        assertEquals(device.name, state.previewName)
    }

    @Test fun `checking updates opens the settings section without changing emulator selection`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Pixel")
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake)
        state.refresh()
        state.selectDevice(device)
        fake.commands.clear()
        state.checkUpdates()
        assertTrue(state.showSettings)
        assertEquals(SettingsSection.Updates, state.settingsSection)
        assertEquals(device.name, state.previewName)
        assertEquals(setOf(device.name), state.selected)
        assertTrue(fake.commands.isEmpty())
        assertNotNull(state.updates.unavailableReason)
    }
}
