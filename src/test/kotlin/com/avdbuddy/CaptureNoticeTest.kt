package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class CaptureNoticeTest {
    @TempDir lateinit var root: File
    private val phone = ConnectedDevice("USB123", "Pixel", "device")

    private fun state(scope: CoroutineScope, screenshot: () -> File): AppState {
        val fake = FakeAndroid(root)
        val capture = object : DeviceCapture {
            override fun screenshot(settings: AppSettings, serial: String, deviceName: String) = screenshot()
            override fun startRecording(settings: AppSettings, serial: String, deviceName: String, deviceType: DeviceType): Recording =
                error("Unexpected recording")
        }
        return AppState(scope, initial = fake.settings, executor = fake, capture = capture,
            license = licensedForTest(scope, File(root, "license")))
    }

    @Test fun `saved screenshot emits its file as data`(): Unit = runBlocking {
        val file = File(root, "capture.png")
        val state = state(this) { file }
        state.captureScreenshot(phone)
        coroutineContext.job.children.toList().joinAll()
        assertEquals(CaptureNotice.Saved(CaptureKind.Screenshot, file), state.captureNotices[phone.name])
    }

    @Test fun `failed screenshot emits a screenshot failure`(): Unit = runBlocking {
        val cause = IllegalStateException()
        val state = state(this) { throw cause }
        state.captureScreenshot(phone)
        coroutineContext.job.children.toList().joinAll()
        val failure = assertIs<CaptureNotice.Failed>(state.captureNotices[phone.name])
        assertEquals(CaptureStep.Screenshot, failure.step)
        assertIs<IllegalStateException>(failure.cause)
    }
}
