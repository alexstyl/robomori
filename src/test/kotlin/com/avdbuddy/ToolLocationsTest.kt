package com.avdbuddy

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class ToolLocationsTest {
    @TempDir lateinit var root: File

    private fun executable(path: String) = File(root, path).apply {
        parentFile.mkdirs(); writeText("#!/bin/sh\n"); setExecutable(true)
    }.path

    private fun splitSettings(fake: FakeAndroid) = fake.settings.copy(
        adb = executable("platform tools/adb"), emulator = executable("emulator/bin/emulator"),
        sdkmanager = executable("cmdline tools/bin/sdkmanager"),
        avdmanager = executable("other tools/bin/avdmanager"),
    )

    @Test fun `individual tool paths persist and blank paths use the SDK defaults`() {
        val fake = FakeAndroid(root)
        val settings = splitSettings(fake)
        val store = SettingsStore(File(root, "config/settings.properties"))
        store.save(settings)
        assertEquals(settings, store.load())
        val tools = Toolchain(store.load())
        assertTrue(tools.ready)
        assertEquals(File(settings.adb), tools.adb)
        assertEquals(File(settings.emulator), tools.emulator)
        assertEquals(File(settings.sdkmanager), tools.sdkmanager)
        assertEquals(File(settings.avdmanager), tools.avdmanager)
        assertEquals(Toolchain(fake.sdk).adb, Toolchain(settings.copy(adb = " ")).adb)
        assertFalse(Toolchain(settings.copy(adb = File(root, "missing").path)).ready)
    }

    @Test fun `old preferences load without tool overrides`() {
        val file = File(root, "settings.properties").apply { writeText("sdk=/sdk\navds=/avds\n") }
        assertEquals(AppSettings("/sdk", "/avds"), SettingsStore(file).load())
    }

    @Test fun `commands use external tools while SDK data stays in the selected folder`() {
        val fake = FakeAndroid(root)
        val settings = splitSettings(fake)
        val service = AndroidService(settings, fake)
        service.images()
        service.profiles()
        service.running()
        assertEquals(listOf(settings.sdkmanager, settings.avdmanager, settings.adb), fake.commands.map { it.executable.path })
        assertEquals(listOf("--list", "--sdk_root=${settings.sdk}"), fake.commands.first().args)
        assertContains(fake.commands[1].env.getValue("AVDMANAGER_OPTS"), "-Dcom.android.sdkmanager.toolsdir=${settings.sdk}")
        assertTrue(fake.commands.all { it.env["ANDROID_HOME"] == settings.sdk && it.env["ANDROID_AVD_HOME"] == settings.avds })
    }

    @Test fun `external avdmanager launcher receives the selected SDK without evaluating path contents`() {
        if (HostOs.detect() == HostOs.WINDOWS) return
        val sdk = File(root, "SDK ' with \$(touch unexpected) `whoami`").apply { mkdir() }
        val launcher = File(executable("external/bin/avdmanager"))
        launcher.writeText("#!/bin/sh\neval set -- \$AVDMANAGER_OPTS\nprintf '%s' \"\$1\"\n")
        val settings = AppSettings(sdk.path, root.path, avdmanager = launcher.path)
        val output = AndroidService(settings).command(launcher, emptyList())
        assertEquals("-Dcom.android.sdkmanager.toolsdir=${File(sdk, "cmdline-tools/latest").path}", output)
        assertFalse(File(root, "unexpected").exists())
    }

    @Test fun `saving reports the missing tool and accepts split installations`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val settings = splitSettings(fake)
        val store = SettingsStore(File(root, "settings.properties"))
        val state = AppState(this, store, fake.settings, fake)
        assertEquals(SettingsSaveResult.InvalidExecutables(setOf("adb")), state.saveSettings(settings.copy(adb = root.path)))
        assertEquals(fake.settings, state.settings)
        assertEquals(SettingsSaveResult.Saved, state.saveSettings(settings))
        assertEquals(settings, store.load())
        assertEquals(settings, state.settings)
    }

    @Test fun `existing emulator launches without command line tools`() {
        val fake = FakeAndroid(root)
        val device = fake.device("Phone")
        Toolchain(fake.settings).sdkmanager.delete()
        Toolchain(fake.settings).avdmanager.delete()

        AndroidService(fake.settings, fake).launch(device)

        assertTrue(fake.commands.single { it.detached }.args.contains("-avd"))
        assertFailsWith<IllegalArgumentException> { AndroidService(fake.settings, fake).catalog() }
    }

    @Test fun `settings accept missing optional command line tools`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        Toolchain(fake.settings).sdkmanager.delete()
        Toolchain(fake.settings).avdmanager.delete()
        val state = AppState(this, SettingsStore(File(root, "settings.properties")), fake.settings, fake)

        assertEquals(SettingsSaveResult.Saved, state.saveSettings(fake.settings))
        assertFalse(Toolchain(fake.settings).ready)
        assertFalse(state.ready)
        Toolchain(fake.settings).avdmanager.apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertTrue(state.ready)
    }

}
