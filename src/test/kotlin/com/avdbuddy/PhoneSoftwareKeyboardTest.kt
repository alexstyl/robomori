package com.avdbuddy

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class PhoneSoftwareKeyboardTest {
    @TempDir lateinit var root: File

    private class RecordingMirror : TestDeviceMirror() {
        val events = mutableListOf<Any>()
        override fun frames(settings: AppSettings, serial: String) = flow<ImageBitmap> { awaitCancellation() }
        override fun keyboardKey(usage: Int, down: Boolean) { events += usage to down }
        override fun releaseKeyboard() { events += "release" }
    }

    // Values from a Pixel 2 on Android 11 sharing its screen with Gboard in hardware keyboard mode.
    private fun imeWindowDump(visibleInset: Int, visible: Boolean = true) =
        "mFullConfiguration={ winConfig={ mAppBounds=Rect(0, 0 - 1080, 1794) } }\n" +
            " mGivenContentInsets=[0,$visibleInset][0,0] mGivenVisibleInsets=[0,$visibleInset][0,0]\n" +
            " mFrame=[0,63][1080,1920] last=[0,63][1080,1920]\n isVisible=$visible"

    private val onScreen = imeWindowDump(visibleInset = 985)
    private val collapsed = imeWindowDump(visibleInset = 1731)

    private fun phoneKeyboard(dump: String, mirror: DeviceMirror = RecordingMirror(), commands: MutableList<Command> = mutableListOf()) =
        PhoneSoftwareKeyboard("USB123", AndroidService(AppSettings("/sdk", "/avds"), CommandExecutor { command, _, _ ->
            commands += command
            if ("dumpsys" in command.args) dump else ""
        }), mirror)

    @Test fun `keyboard covering the app counts as on screen`() =
        assertTrue(ImeWindow.parse(onScreen)!!.coversApp)

    @Test fun `Gboard collapsed to its floating button is not on screen although it reports shown`() =
        assertFalse(ImeWindow.parse(collapsed)!!.coversApp)

    @Test fun `hidden IME window is not on screen`() =
        assertFalse(ImeWindow.parse(imeWindowDump(visibleInset = 985, visible = false))!!.coversApp)

    @Test fun `unreadable IME window is not on screen`() =
        assertFalse(phoneKeyboard("isVisible=true").isOnScreen())

    @Test fun `hidden keyboard is shown with Gboard's Alt+K shortcut`() {
        val mirror = RecordingMirror()
        val commands = mutableListOf<Command>()
        phoneKeyboard(collapsed, mirror, commands).toggle()
        assertEquals<List<Any>>(listOf("release", 226 to true, 14 to true, 14 to false, 226 to false), mirror.events)
        assertTrue(commands.none { "keyevent" in it.args })
    }

    @Test fun `keyboard on screen is hidden with BACK`() {
        val mirror = RecordingMirror()
        val commands = mutableListOf<Command>()
        phoneKeyboard(onScreen, mirror, commands).toggle()
        assertTrue(mirror.events.isEmpty())
        assertTrue(commands.any { it.args.takeLast(3) == listOf("input", "keyevent", "KEYCODE_BACK") })
    }

    @Test fun `keyboard commands address the shared phone`() {
        val commands = mutableListOf<Command>()
        phoneKeyboard(onScreen, commands = commands).toggle()
        assertTrue(commands.all { it.args.take(2) == listOf("-s", "USB123") })
    }

    @Test fun `only a shared phone has a software keyboard to toggle`(): Unit = runBlocking {
        val connected = ConnectedDevicesState(this, { AppSettings(root.path, root.path) },
            DeviceNamesStore(File(root, "names")), PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() },
            { AndroidService(it, CommandExecutor { _, _, _ -> "" }) })
        val phone = ConnectedDevice("USB123", "Pixel", "device", identity = "USB123")
        assertNull(connected.softwareKeyboard(phone))
        connected.startSharing(phone)
        try {
            assertNotNull(connected.softwareKeyboard(phone))
        } finally { connected.stopSharing(phone) }
    }
}
