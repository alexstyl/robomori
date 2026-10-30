package com.avdbuddy

import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class ScrcpyMirrorTest {
    @Test fun `bundled scrcpy decodes a live device frame without opening a window`() = runBlocking {
        val serial = System.getenv("AVDBUDDY_MIRROR_TEST_SERIAL")
        assumeTrue(!serial.isNullOrBlank())
        val timings = mutableListOf<Double>()
        val frame = withTimeout(25000) { ScrcpyMirror { decode, conversion ->
            timings += (decode + conversion) / 1_000_000.0
        }.frames(SettingsStore().load(), serial!!).take(20).last() }
        val warm = timings.drop(1).sorted()
        println("Warm frame processing median: ${warm[warm.size / 2]} ms; maximum: ${warm.last()} ms")
        assertTrue(frame.width > 100)
        assertTrue(frame.height > 100)
    }
}
