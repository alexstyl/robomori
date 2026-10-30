package com.avdbuddy

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

internal open class TestDeviceMirror : DeviceMirror {
    override fun frames(settings: AppSettings, serial: String): Flow<ImageBitmap> = emptyFlow()
    override fun touch(action: Int, x: Int, y: Int, width: Int, height: Int) {}
    override fun key(code: Int) {}
    override fun keyboardKey(usage: Int, down: Boolean) {}
    override fun releaseKeyboard() {}
}

class ScreenSharingSessionTest {
    @TempDir lateinit var root: File

    @Test fun `selection changes keep one live stream and the latest frame until explicitly stopped`() = runBlocking {
        withTimeout(3000) {
            val incoming = Channel<ImageBitmap>(Channel.UNLIMITED)
            val delivered = Channel<Unit>(Channel.UNLIMITED)
            val started = CompletableDeferred<Unit>()
            val closed = CompletableDeferred<Unit>()
            var connections = 0
            val mirror = object : TestDeviceMirror() {
                override fun frames(settings: AppSettings, serial: String) = flow {
                    connections++
                    started.complete(Unit)
                    try {
                        for (frame in incoming) { emit(frame); delivered.send(Unit) }
                    } finally { closed.complete(Unit) }
                }
            }
            val state = ConnectedDevicesState(this, { AppSettings(root.path, root.path) },
                DeviceNamesStore(File(root, "names")), PairedDevicesStore(File(root, "paired.json")), { mirror })
            val phone = ConnectedDevice("192.168.1.2:5555", "Pixel", "device", identity = "phone")
            state.selected = phone
            state.startSharing(phone)
            try {
                started.await()
                val first = ImageBitmap(2, 2)
                incoming.send(first); delivered.receive()
                state.selected = null
                val second = ImageBitmap(2, 2)
                incoming.send(second); delivered.receive()
                state.selected = phone
                state.startSharing(phone)
                assertEquals(1, connections)
                assertSame(second, state.sharingSession(phone)?.frame)
                assertFalse(closed.isCompleted)
                state.stopSharing(phone)
                closed.await()
                assertNull(state.sharingSession(phone))
            } finally { state.stopSharing(phone) }
        }
    }
}
