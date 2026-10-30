package com.avdbuddy

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class ImageArchiveDownloadTest {
    @TempDir lateinit var root: File

    private fun withArchive(block: (ImageArchive, ByteArray) -> Unit) {
        val bytes = ByteArray(512 * 1024) { (it % 251).toByte() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/image.zip") { exchange ->
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            block(ImageArchive(bytes.size.toLong(), URI("http://127.0.0.1:${server.address.port}/image.zip"), checksum, "SHA-256"), bytes)
        } finally { server.stop(0) }
    }

    @Test fun `archives download with progress and verified contents before installation`() = withArchive { archive, bytes ->
        val progress = mutableListOf<DownloadProgress>()
        downloadImageArchive(archive, root, AtomicBoolean(), progress::add).use { downloaded ->
            assertContentEquals(bytes, downloaded.file.readBytes())
            assertEquals(archive.uri.toString(), downloaded.uri)
            assertEquals(100, progress.last().percent)
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `checksum failures discard the archive`() = withArchive { archive, _ ->
        assertFailsWith<IllegalStateException> {
            downloadImageArchive(archive.copy(checksum = "invalid"), root, AtomicBoolean()) {}
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test fun `cancelled transfers discard partial archives`() = withArchive { archive, _ ->
        val cancelled = AtomicBoolean()
        assertFailsWith<CancellationException> {
            downloadImageArchive(archive, root, cancelled) { cancelled.set(true) }
        }
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }
}
