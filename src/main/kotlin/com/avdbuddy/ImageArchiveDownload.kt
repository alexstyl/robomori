package com.avdbuddy

import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class DownloadedImage(val file: File, val uri: String) : AutoCloseable {
    override fun close() { file.delete(); file.parentFile.delete() }
}

private val archiveHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
    .followRedirects(HttpClient.Redirect.NORMAL).build()

internal fun downloadImageArchive(image: SystemImage, settings: AppSettings, cancelled: AtomicBoolean,
                                  progress: (DownloadProgress) -> Unit): DownloadedImage {
    val archive = findImageArchive(image.path) ?: error("Could not find this Android image. Reopen Android Images and try again.")
    return downloadImageArchive(archive, expandPath(settings.sdk), cancelled, progress)
}

internal fun downloadImageArchive(archive: ImageArchive, sdk: File, cancelled: AtomicBoolean,
                                  progress: (DownloadProgress) -> Unit): DownloadedImage {
    val directory = Files.createTempDirectory(sdk.toPath(), ".avdbuddy-download-").toFile()
    val result = DownloadedImage(File(directory, "image.zip"), archive.uri.toString())
    try {
        val request = HttpRequest.newBuilder(archive.uri).timeout(Duration.ofHours(2)).GET().build()
        val response = archiveHttpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { input ->
            check(response.statusCode() == 200) { "Could not download this Android image (HTTP ${response.statusCode()}). Try again." }
            val digest = MessageDigest.getInstance(archive.checksumType)
            var received = 0L
            var lastPercent = -1
            result.file.outputStream().buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    received += count
                    check(received <= archive.size) { "The Android image download exceeded its expected size. Try again." }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    val percent = (received * 100 / archive.size).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        progress(DownloadProgress(percent))
                    }
                }
            }
            check(received == archive.size) { "The Android image download was interrupted. Try again." }
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            check(checksum.equals(archive.checksum, ignoreCase = true)) { "The Android image download could not be verified. Try again." }
        }
        return result
    } catch (e: Exception) { result.close(); throw e }
}
