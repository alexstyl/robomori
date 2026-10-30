package com.avdbuddy

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.time.Duration
import java.util.Locale
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

const val GIB = 1024L * 1024 * 1024
fun storageSize(bytes: Long) = String.format(Locale.ENGLISH, "%.1f GiB", bytes.toDouble() / GIB)
data class ImageFootprint(val download: Long, val unpacked: Long)
data class DiskSpace(val volume: String, val available: Long)
data class DiskRequirement(val location: String, val path: String, val required: Long, val available: Long) {
    val enough get() = available >= required
}
data class StoragePlan(val image: ImageFootprint, val deviceCapacity: Long, val disks: List<DiskRequirement>) {
    val canCreate get() = disks.all { it.enough }
    val required get() = disks.sumOf { it.required }
}

/** Budget initial sparse data and the emulator's first-boot free-space requirement. */
fun calculateStoragePlan(settings: AppSettings, spec: CreateSpec, image: ImageFootprint, disk: (File) -> DiskSpace): StoragePlan {
    val sdk = expandPath(settings.sdk)
    val avds = expandPath(settings.avds)
    val deviceCapacity = (spec.storage.toLong() + (spec.sdCard ?: 0)) * GIB
    val avdSpace = disk(avds)
    val initialData = minOf(spec.storage.toLong() * GIB, 6 * GIB)
    // The emulator refuses first initialization unless 120% of the virtual
    // data partition capacity is free, even when the image will be sparse.
    val initializationRequired = (spec.storage.toLong() * GIB * 6 + 4) / 5
    val sdCard = (spec.sdCard ?: 0).toLong() * GIB
    // Data grows as the guest writes to it. Reserve the SD card in full and
    // leave 5 GiB for startup, cache, and configuration after initial setup.
    val deviceRequired = maxOf(initialData + 5 * GIB, initializationRequired) + sdCard
    val requirements = if (spec.image.installed) {
        listOf(DiskRequirement("Emulator storage", avds.path, deviceRequired, avdSpace.available))
    } else {
        val sdkSpace = disk(sdk)
        val installRequired = image.download + image.unpacked + GIB // ZIP coexists with extracted files
        if (sdkSpace.volume == avdSpace.volume) {
            listOf(DiskRequirement("SDK and emulator storage", "${sdk.path}\n${avds.path}", installRequired + deviceRequired, minOf(sdkSpace.available,avdSpace.available)))
        } else listOf(
            DiskRequirement("Android SDK", sdk.path, installRequired, sdkSpace.available),
            DiskRequirement("Emulator storage", avds.path, deviceRequired, avdSpace.available),
        )
    }
    return StoragePlan(image, deviceCapacity, requirements)
}
fun diskSpace(file: File): DiskSpace {
    val existing = generateSequence(file) { it.parentFile }.firstOrNull { it.exists() }
        ?: error("Storage location is unavailable: ${file.path}")
    val store = Files.getFileStore(existing.toPath())
    return DiskSpace(store.toString(), store.usableSpace)
}
fun planDeviceStorage(spec: CreateSpec, settings: AppSettings): StoragePlan {
    val image = if (spec.image.installed) ImageFootprint(0, 0) else imageFootprint(spec.image)
    return calculateStoragePlan(settings, spec, image, ::diskSpace)
}

internal data class ImageArchive(val size: Long, val uri: URI, val checksum: String, val checksumType: String)
fun requireImageStorage(image: SystemImage, settings: AppSettings) {
    val sdk = expandPath(settings.sdk)
    val images = File(sdk, "system-images")
    require(!Files.isSymbolicLink(images.toPath()) || images.isDirectory) { "System image storage is unavailable. Connect its drive or choose another SDK in Settings." }
    val footprint = imageFootprint(image)
    val required = footprint.download + footprint.unpacked + GIB
    require(diskSpace(sdk).available >= required) {
        "Not enough space to download this Android version. Free at least ${storageSize(required)} in your SDK storage and try again."
    }
}

private val footprintCache = java.util.concurrent.ConcurrentHashMap<ImageArchive, ImageFootprint>()
private fun imageFootprint(image: SystemImage): ImageFootprint {
    val archive = findImageArchive(image.path)
        ?: error("Download size is unavailable for this image. Refresh the Android catalog and try again.")
    return footprintCache.getOrPut(archive) {
        // Read only ZIP directory metadata, without downloading the image itself.
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build()
        fun range(start: Long, end: Long): ByteArray {
            require(end >= start && end-start < 4 * 1024 * 1024) { "Image archive metadata is too large to read." }
            val request = HttpRequest.newBuilder(archive.uri).timeout(Duration.ofSeconds(20))
                .header("Range", "bytes=$start-$end").header("Accept-Encoding", "identity").GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            return response.body().use { stream ->
                check(response.statusCode() == 206) { "Could not check unpacked image size. The download server did not provide archive metadata." }
                val bytes = stream.readNBytes((end-start+1).toInt()+1)
                check(bytes.size.toLong() == end-start+1) { "Incomplete image archive metadata." }
                bytes
            }
        }
        val tail = range(maxOf(0, archive.size-65557), archive.size-1)
        val (offset, length) = zipDirectoryLocation(tail)
        val directory = range(offset, offset+length-1)
        ImageFootprint(archive.size, zipUnpackedSize(directory))
    }
}

/** SDK Manager's cache is refreshed by --list; use its URL metadata to resolve relative archive URLs. */
internal fun findImageArchive(path: String): ImageArchive? {
    val cache = File(HostPlatform().androidUserHome, "cache")
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false; isExpandEntityReferences = false
    }
    for (file in cache.listFiles().orEmpty().filter { it.name.startsWith("sdkbin-") && it.name.contains("sys-img") }.sortedByDescending { it.lastModified() }) {
        val archive = runCatching {
            val metadata = File(cache, file.name.replaceFirst("sdkbin-", "sdkinf-"))
            val source = Properties().apply { metadata.inputStream().use(::load) }.getProperty("URL")
            val base = URI(source)
            require(base.scheme == "https" && base.host == "dl.google.com")
            val packages = factory.newDocumentBuilder().parse(file).getElementsByTagName("remotePackage")
            (0 until packages.length).map { packages.item(it) as Element }.firstOrNull { it.getAttribute("path") == path }?.let { pkg ->
                val archives = pkg.getElementsByTagName("archive")
                val host = when { System.getProperty("os.name").contains("Mac") -> "macosx"; System.getProperty("os.name").contains("Windows") -> "windows"; else -> "linux" }
                val selected = (0 until archives.length).map { archives.item(it) as Element }.firstOrNull {
                    val hosts = it.getElementsByTagName("host-os")
                    hosts.length==0 || hosts.item(0).textContent.trim()==host
                } ?: return@let null
                val complete = selected.getElementsByTagName("complete").item(0) as? Element ?: return@let null
                val size = complete.getElementsByTagName("size").item(0).textContent.trim().toLong()
                val uri = base.resolve(complete.getElementsByTagName("url").item(0).textContent.trim())
                require(size > 0 && uri.scheme == "https" && uri.host == "dl.google.com")
                val checksum = complete.getElementsByTagName("checksum").item(0) as Element
                ImageArchive(size, uri, checksum.textContent.trim(), checksum.getAttribute("type").ifBlank { "sha1" })
            }
        }.getOrNull()
        if (archive != null) return archive
    }
    return null
}
private fun ByteArray.u16(offset: Int) = ByteBuffer.wrap(this,offset,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
private fun ByteArray.u32(offset: Int) = ByteBuffer.wrap(this,offset,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
private fun ByteArray.i64(offset: Int) = ByteBuffer.wrap(this,offset,8).order(ByteOrder.LITTLE_ENDIAN).long
internal fun zipDirectoryLocation(tail: ByteArray): Pair<Long, Long> {
    val end = (tail.size-22 downTo 0).firstOrNull { tail.u32(it)==0x06054b50L && it+22+tail.u16(it+20)==tail.size }
        ?: error("Could not read image archive directory.")
    val size = tail.u32(end+12); val offset = tail.u32(end+16)
    if (size == 0xffffffffL || offset == 0xffffffffL) {
        val zip64 = (end-56 downTo 0).firstOrNull { tail.u32(it)==0x06064b50L }
            ?: error("Missing ZIP64 archive directory metadata.")
        val wideSize=tail.i64(zip64+40); val wideOffset=tail.i64(zip64+48)
        require(wideSize>0 && wideOffset>=0) { "Invalid ZIP64 archive directory metadata." }
        return wideOffset to wideSize
    }
    return offset to size
}
internal fun zipUnpackedSize(directory: ByteArray): Long {
    var offset = 0; var total = 0L
    while (offset < directory.size) {
        require(offset+46 <= directory.size && directory.u32(offset)==0x02014b50L) { "Invalid image archive directory." }
        val name = directory.u16(offset+28); val extra = directory.u16(offset+30); val comment = directory.u16(offset+32)
        val next = offset+46+name+extra+comment
        require(next <= directory.size) { "Incomplete image archive directory." }
        var size = directory.u32(offset+24)
        if (size == 0xffffffffL) {
            var cursor = offset+46+name; val end = cursor+extra
            var found = false
            while (cursor+4 <= end) {
                val type = directory.u16(cursor); val length = directory.u16(cursor+2)
                require(cursor+4+length <= end) { "Invalid ZIP64 image size." }
                if (type==1 && length>=8) { size=directory.i64(cursor+4); found=true; break }
                cursor+=4+length
            }
            require(found) { "Missing ZIP64 image size." }
        }
        require(size >= 0) { "Invalid image size." }
        total = Math.addExact(total,size); offset=next
    }
    require(total>0) { "Image archive is empty." }
    return total
}
