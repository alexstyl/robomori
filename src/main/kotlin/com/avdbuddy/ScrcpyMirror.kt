package com.avdbuddy

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import org.jcodec.codecs.h264.H264Decoder
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.ColorUtil
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.DataInputStream
import java.io.BufferedInputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random

interface DeviceMirror {
    fun frames(settings: AppSettings, serial: String): Flow<ImageBitmap>
    fun touch(action: Int, x: Int, y: Int, width: Int, height: Int)
    fun key(code: Int)
    fun keyboardKey(usage: Int, down: Boolean)
    fun releaseKeyboard()
}

internal class ScrcpyMirror(private val onFrameTiming: (Long, Long) -> Unit = { _, _ -> }) : DeviceMirror {
    private val control = AtomicReference<ScrcpyControl?>()
    override fun touch(action: Int, x: Int, y: Int, width: Int, height: Int) { control.get()?.touch(action, x, y, width, height) }
    override fun key(code: Int) { control.get()?.key(code) }
    override fun keyboardKey(usage: Int, down: Boolean) { control.get()?.keyboardKey(usage, down) }
    override fun releaseKeyboard() { control.get()?.releaseKeyboard() }

    override fun frames(settings: AppSettings, serial: String) = callbackFlow<ImageBitmap> {
        val socket = AtomicReference<Socket?>()
        val controlSocket = AtomicReference<Socket?>()
        val sessionControl = AtomicReference<ScrcpyControl?>()
        fun closeControl() {
            sessionControl.getAndSet(null)?.let {
                control.compareAndSet(it, null)
                it.close()
            }
        }
        val server = AtomicReference<Process?>()
        val adb = Toolchain(settings).adb.path
        val id = Random.nextInt(1, Int.MAX_VALUE).toString(16).padStart(8, '0')
        val remote = "/data/local/tmp/avdbuddy-mirror-$id.jar"
        var port: String? = null
        fun command(vararg args: String): String {
            val process = ProcessBuilder(listOf(adb, "-s", serial) + args).redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "Could not connect to your device. $result" }
            return result.trim()
        }
        val worker = thread(isDaemon = true, name = "device-mirror-$id") {
            try {
                command("push", BundledScrcpy.files.server.path, remote)
                port = command("forward", "tcp:0", "localabstract:scrcpy_$id")
                val process = ProcessBuilder(adb, "-s", serial, "shell", "CLASSPATH=$remote", "app_process", "/",
                    "com.genymobile.scrcpy.Server", "5.0.1", "scid=$id", "tunnel_forward=true", "audio=false",
                    "control=true", "clipboard_autosync=false", "send_device_meta=false", "send_dummy_byte=false", "max_size=1280", "max_fps=30",
                    "video_codec_options=profile=1,level=512", "cleanup=false")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                server.set(process)
                var stream: DataInputStream? = null
                repeat(50) {
                    if (stream == null && !isClosedForSend) {
                        try {
                            val candidate = Socket("127.0.0.1", port!!.toInt())
                            candidate.soTimeout = 15000
                            candidate.tcpNoDelay = true
                            socket.set(candidate)
                            val inputSocket = Socket("127.0.0.1", port!!.toInt()).apply { tcpNoDelay = true }
                            controlSocket.set(inputSocket)
                            val input = DataInputStream(BufferedInputStream(candidate.getInputStream(), 64 * 1024))
                            check(input.readInt() == 0x68323634) { "Your device could not start H.264 mirroring." }
                            candidate.soTimeout = 0 // A static screen legitimately produces no new video packets.
                            stream = input
                            val writer = ScrcpyControl(inputSocket.getOutputStream()) { close(it) }
                            writer.connectKeyboard()
                            sessionControl.set(writer)
                            if (isClosedForSend) writer.close() else control.set(writer)
                        } catch (_: Exception) {
                            runCatching { socket.getAndSet(null)?.close() }
                            runCatching { controlSocket.getAndSet(null)?.close() }
                            Thread.sleep(100)
                        }
                    }
                }
                val input = stream ?: error("Could not start device mirroring.")
                var width = 0
                var height = 0
                var decoder = H264Decoder()
                var pixels: Array<ByteArray>? = null
                var rgb: Picture? = null
                var rgba: ByteArray? = null
                while (!isClosedForSend) {
                    val first = input.readInt()
                    val second = input.readInt()
                    val third = input.readInt()
                    if (first < 0) {
                        width = second
                        height = third
                        check(width in 1..4096 && height in 1..4096) { "Invalid device screen size." }
                        pixels = Picture.create((width + 15) / 16 * 16, (height + 15) / 16 * 16, ColorSpace.YUV420).data
                        decoder = H264Decoder()
                        rgb = null
                        rgba = null
                        continue
                    }
                    check(third in 1..16_777_216) { "Invalid device video packet." }
                    val bytes = ByteArray(third).also(input::readFully)
                    if (first and 0x40000000 != 0) {
                        decoder = H264Decoder.createH264DecoderFromCodecPrivate(ByteBuffer.wrap(bytes))
                        continue
                    }
                    val decodeStarted = System.nanoTime()
                    val decoded = decoder.decodeFrame(ByteBuffer.wrap(bytes), pixels ?: continue) ?: continue
                    val decodedAt = System.nanoTime()
                    val color = rgb ?: Picture.create(decoded.width, decoded.height, ColorSpace.RGB).also { rgb = it }
                    ColorUtil.getTransform(decoded.color, ColorSpace.RGB).transform(decoded, color)
                    val source = color.getPlaneData(0)
                    val output = rgba ?: ByteArray(width * height * 4).also { rgba = it }
                    for (y in 0 until height) for (x in 0 until width) {
                        val from = (y * color.width + x) * 3
                        val to = (y * width + x) * 4
                        output[to] = (source[from] + 128).toByte()
                        output[to + 1] = (source[from + 1] + 128).toByte()
                        output[to + 2] = (source[from + 2] + 128).toByte()
                        output[to + 3] = 255.toByte()
                    }
                    val bitmap = Bitmap()
                    bitmap.installPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), output, width * 4)
                    bitmap.setImmutable()
                    trySend(bitmap.asComposeImageBitmap())
                    onFrameTiming(decodedAt - decodeStarted, System.nanoTime() - decodedAt)
                }
            } catch (failure: Exception) { if (!isClosedForSend) close(failure) }
            finally {
                closeControl()
                runCatching { controlSocket.getAndSet(null)?.close() }
                runCatching { socket.getAndSet(null)?.close() }
                server.getAndSet(null)?.destroyForcibly()
                port?.let { runCatching { command("forward", "--remove", "tcp:$it") } }
                runCatching { command("shell", "rm", "-f", remote) }
            }
        }
        awaitClose {
            closeControl()
            runCatching { controlSocket.getAndSet(null)?.close() }
            runCatching { socket.getAndSet(null)?.close() }
            server.getAndSet(null)?.destroyForcibly()
            worker.interrupt()
        }
    }.buffer(Channel.CONFLATED)
}
