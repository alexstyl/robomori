package com.avdbuddy

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class AndroidDownloadsTest {
    @TempDir lateinit var root: File
    private fun image(api: Int = 37) = SystemImage("system-images;android-$api;google_apis_playstore;arm64-v8a", "Android", false)

    @Test fun `installed image size includes its files and ignores symlinks`() {
        val installed = image().copy(installed = true)
        assertNull(installed.sizeOnDisk(root))
        val directory = installed.directory(root).apply { mkdirs() }
        File(directory, "system.img").writeBytes(ByteArray(12))
        File(directory, "data").apply { mkdirs() }
        File(directory, "data/source.properties").writeBytes(ByteArray(5))
        val outside = File(root, "outside.img").apply { writeBytes(ByteArray(20)) }
        java.nio.file.Files.createSymbolicLink(File(directory, "linked.img").toPath(), outside.toPath())
        assertEquals(17L, installed.sizeOnDisk(root))
    }

    private fun testDownloads(scope: CoroutineScope, storage: (SystemImage, AppSettings) -> Unit = { _, _ -> }) =
        AndroidDownloads(scope,
            fetchArchive = { _, _, _, progress ->
                val directory = java.nio.file.Files.createTempDirectory(root.toPath(), "download-").toFile()
                progress(DownloadProgress(100, "Downloading…"))
                DownloadedImage(File(directory, "image.zip").apply { writeText("archive") }, "https://example.test/image.zip")
            },
            installArchive = { backend, image, _, cancelled, output -> backend.installImage(image, cancelled, output) },
            checkStorage = storage)
    private suspend fun awaitCondition(condition: () -> Boolean) = withTimeout(5000) {
        while (!condition()) delay(5)
    }

    @Test fun `progress handles split percentages and carriage return updates`() {
        val parser = DownloadProgressParser()
        assertNull(parser.append("Preparing…\r[===] 4"))
        assertEquals(DownloadProgress(42, "Downloading system.img"), parser.append("2% Downloading system.img"))
        assertEquals(DownloadProgress(73, "Unzipping…"), parser.append("\r[=======] 73% Unzipping…"))
    }

    @Test fun `downloads run independently and queue without creating a device`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.args.firstOrNull() == "--install") {
                entered.countDown()
                output("[===] 42% Downloading system.img\r")
                check(release.await(5, TimeUnit.SECONDS))
            }
            fake.run(command, cancelled, output)
        }
        val downloads = testDownloads(this)
        var installed = 0
        val backend = AndroidService(fake.settings, executor)
        downloads.start(image(), backend) { installed++ }
        downloads.start(image(), backend) { fail("Duplicate download") }
        downloads.start(image(36), backend) { installed++ }
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            awaitCondition { downloads.tasks.values.any { it.state == AndroidDownloadState.WaitingToInstall } &&
                downloads.tasks.values.any { it.progress.value.percent == 42 } }
            assertEquals(2, downloads.activeCount)
            assertEquals(1, downloads.tasks.values.count { it.progress.value.percent == 42 })
            assertEquals(1, downloads.tasks.values.count { it.state == AndroidDownloadState.WaitingToInstall })
        } finally { release.countDown() }
        awaitCondition { downloads.activeCount == 0 }
        assertEquals(2, installed)
        assertEquals(2, fake.commands.count { it.args.firstOrNull() == "--install" })
        assertTrue(fake.commands.filter { it.args.firstOrNull() == "--install" }.all { it.env["TERM"] == "xterm" })
        assertTrue(backend.devices().isEmpty())
        assertFalse(fake.commands.any { it.detached || it.args.take(2) == listOf("create", "avd") })
    }

    @Test fun `failed downloads can retry and installed versions do not download`(): Unit = runBlocking {
        val fake = FakeAndroid(root).apply { installFailure = "Connection interrupted" }
        val downloads = testDownloads(this)
        val backend = AndroidService(fake.settings, fake)
        var installed = false
        downloads.start(image(), backend) { installed = true }
        awaitCondition { downloads.activeCount == 0 }
        assertFalse(installed)
        assertIs<AndroidDownloadState.Failed>(downloads.tasks.getValue(image().path).state)
        fake.installFailure = null
        downloads.start(image(), backend) { installed = true }
        awaitCondition { downloads.activeCount == 0 }
        assertTrue(installed)
        assertEquals(AndroidDownloadState.Installed, downloads.tasks.getValue(image().path).state)
        downloads.start(image().copy(installed = true), backend) { fail("Already installed") }
        assertEquals(2, fake.commands.count { it.args.firstOrNull() == "--install" })
    }

    @Test fun `unchecking an active download cancels without marking it installed`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val entered = CountDownLatch(1)
        val executor = CommandExecutor { _, cancelled, _ ->
            entered.countDown()
            while (!cancelled.get()) Thread.sleep(5)
            throw CancellationException("Cancelled")
        }
        val downloads = testDownloads(this)
        var installed = false
        downloads.start(image(), AndroidService(fake.settings, executor)) { installed = true }
        withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
        downloads.cancel(image().path)
        awaitCondition { downloads.activeCount == 0 }
        assertFalse(installed)
        assertEquals(AndroidDownloadState.Cancelled, downloads.tasks.getValue(image().path).state)
    }

    @Test fun `storage failures stop before invoking the SDK installer`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val downloads = testDownloads(this) { _, _ -> error("Not enough disk space") }
        downloads.start(image(), AndroidService(fake.settings, fake)) { fail("Storage check failed") }
        awaitCondition { downloads.activeCount == 0 }
        assertIs<AndroidDownloadState.Failed>(downloads.tasks.getValue(image().path).state)
        assertTrue(fake.commands.isEmpty())
    }

    @Test fun `queued download cancellation does not wait for another install`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val downloads = testDownloads(this)
        downloads.sdkLock.lock()
        try {
            downloads.start(image(), AndroidService(fake.settings, fake)) { fail("Cancelled while queued") }
            downloads.cancel(image().path)
            awaitCondition { downloads.activeCount == 0 }
            assertTrue(fake.commands.isEmpty())
        } finally { downloads.sdkLock.unlock() }
    }

    @Test fun `deleting an unused installed version makes it available to download`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake)
        state.loadCatalog()
        awaitCondition { !state.catalogLoading && state.images.isNotEmpty() }
        val installed = state.images.first { it.installed }
        state.deleteImage(installed)
        assertFalse(state.busy)
        assertEquals(setOf(installed.path), state.deletingImagePaths)
        awaitCondition { state.deletingImagePaths.isEmpty() }
        assertFalse(state.images.first { it.path == installed.path }.installed)
        assertFalse(state.imageDeletions.values.any { it is OperationState.Failed })
        assertTrue(fake.commands.any { it.args == listOf("--uninstall", installed.path) })
    }

    @Test fun `installed versions show from disk while the catalog is still loading`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        File(fake.sdk, "system-images/android-36/google_apis_playstore/arm64-v8a").apply {
            mkdirs()
            File(this, "source.properties").writeText("Pkg.Desc=Google Play ARM64 System Image\n")
            File(this, "system.img").writeText("image")
        }
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake)
        state.downloads.sdkLock.lock()
        try {
            state.loadCatalog()
            awaitCondition { state.images.isNotEmpty() }
            assertTrue(state.catalogLoading)
            assertEquals(listOf("system-images;android-36;google_apis_playstore;arm64-v8a"), state.images.map { it.path })
            assertTrue(fake.commands.none { it.args == listOf("--list") })
        } finally { state.downloads.sdkLock.unlock() }
        awaitCondition { !state.catalogLoading }
        assertTrue(fake.commands.any { it.args == listOf("--list") })
    }

    @Test fun `the catalog is fetched once a day and installed versions always come from disk`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        File(fake.sdk, "system-images/android-37/google_apis_playstore/arm64-v8a").apply {
            mkdirs()
            File(this, "source.properties").writeText("Pkg.Desc=Google Play ARM64 System Image\n")
            File(this, "system.img").writeText("image")
        }
        val cache = File(root, "sdk-catalog.txt")
        val listed = "Available Packages:\n  system-images;android-36;google-tv;arm64-v8a | 1 | Google TV ARM 64 v8a System Image\n"
        cache.writeText("sdk=${fake.settings.sdk}\n$listed")
        val fresh = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake, catalogCache = cache)
        fresh.loadCatalog()
        awaitCondition { !fresh.catalogLoading && fresh.images.isNotEmpty() }
        assertTrue(fake.commands.none { it.args == listOf("--list") })
        val tv = fresh.images.single { it.path == "system-images;android-36;google-tv;arm64-v8a" }
        assertFalse(tv.installed)
        // Installed images come from disk even though the cached listing doesn't mention them.
        assertEquals(listOf("system-images;android-37;google_apis_playstore;arm64-v8a"), fresh.images.filter { it.installed }.map { it.path })

        cache.setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        val stale = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake, catalogCache = cache)
        stale.loadCatalog()
        awaitCondition { !stale.catalogLoading && fake.commands.any { it.args == listOf("--list") } }
        assertEquals(1, fake.commands.count { it.args == listOf("--list") })
        assertTrue(cache.readText().startsWith("sdk=${fake.settings.sdk}\n"))
        assertTrue(System.currentTimeMillis() - cache.lastModified() < 60_000)
    }

    @Test fun `opening downloads refreshes the catalog while keeping existing versions visible`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake)
        state.openSettings(SettingsSection.Images)
        awaitCondition { !state.catalogLoading && state.images.isNotEmpty() }
        val previous = state.images
        state.showSettings = false
        state.imageDeletions[previous.first().path] = OperationState.Failed(IllegalStateException())
        fake.additionalImages = "\nsystem-images;android-21;google_apis;arm64-v8a | 1 | Google APIs ARM 64 System Image\n"
        state.openSettings(SettingsSection.Images)
        assertTrue(state.showSettings)
        assertFalse(state.imageDeletions.values.any { it is OperationState.Failed })
        assertEquals(previous, state.images)
        awaitCondition { state.images.any { it.api == "21" } }
        assertEquals(2, fake.commands.count { it.args == listOf("--list") })
    }

    @Test fun `deleting a version used by a device fails before uninstalling`() {
        val fake = FakeAndroid(root)
        fake.device("Pixel", api = "36")
        val backend = AndroidService(fake.settings, fake)
        val installed = backend.images().first { it.installed }
        val failure = assertFailsWith<IllegalArgumentException> { backend.deleteImage(installed) }
        assertContains(failure.message.orEmpty(), "Pixel")
        assertFalse(fake.commands.any { it.args.firstOrNull() == "--uninstall" })
        assertEquals(1, backend.devices().size)
    }

    @Test fun `Android TV image is recognized as in use`() {
        val fake = FakeAndroid(root)
        val backend = AndroidService(fake.settings, fake)
        val image = SystemImage("system-images;android-36;android-tv;arm64-v8a", "Android TV", true)
        backend.create(CreateSpec("Android_TV", image, "tv_1080p"), java.util.concurrent.atomic.AtomicBoolean()) {}
        val device = backend.devices().single()

        assertTrue(device.usesImage(image, fake.sdk))
        val failure = assertFailsWith<IllegalArgumentException> { backend.deleteImage(image) }
        assertContains(failure.message.orEmpty(), device.displayName)
        assertFalse(fake.commands.any { it.args.firstOrNull() == "--uninstall" })
    }

    @Test fun `image usage matches relative and absolute paths without confusing variants`() {
        val fake = FakeAndroid(root)
        val device = fake.device("Pixel", api = "36")
        val image = SystemImage("system-images;android-36;google_apis;arm64-v8a", "Android 16", true)
        assertTrue(device.usesImage(image, fake.sdk))
        val absolute = File(fake.sdk, device.config.getValue("image.sysdir.1")).absolutePath
        assertTrue(device.copy(config = device.config + ("image.sysdir.1" to absolute)).usesImage(image, fake.sdk))
        assertFalse(device.usesImage(image.copy(path = image.path.replace("google_apis;", "google_apis_playstore;")), fake.sdk))
        assertFalse(device.usesImage(image.copy(path = image.path.replace("android-36", "android-35")), fake.sdk))
        assertFalse(device.copy(config = emptyMap()).usesImage(image, fake.sdk))
    }

    @Test fun `deleting an emulator clears stale image deletion errors while downloads stays open`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Pixel", api = "36")
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake)
        state.refresh()
        state.openSettings(SettingsSection.Images)
        val path = "system-images;android-36;google_apis;arm64-v8a"
        state.imageDeletions[path] = OperationState.Failed(IllegalStateException())
        state.delete(listOf(device))
        awaitCondition { device.name !in state.deviceOperations }
        assertTrue(state.devices.isEmpty())
        assertTrue(state.showSettings)
        assertFalse(state.imageDeletions.values.any { it is OperationState.Failed })
    }

    @Test fun `archive transfers overlap but only one installation runs at a time`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val fetching = CountDownLatch(2)
        val releaseTransfers = CountDownLatch(1)
        val installing = CountDownLatch(1)
        val releaseInstallation = CountDownLatch(1)
        val installs = java.util.concurrent.atomic.AtomicInteger()
        val maximum = java.util.concurrent.atomic.AtomicInteger()
        val archives = java.util.Collections.synchronizedList(mutableListOf<File>())
        val downloads = AndroidDownloads(this,
            fetchArchive = { _, _, _, progress ->
                fetching.countDown()
                check(releaseTransfers.await(5, TimeUnit.SECONDS))
                progress(DownloadProgress(100, "Downloading…"))
                val dir = java.nio.file.Files.createTempDirectory(root.toPath(), "parallel-").toFile()
                val file = File(dir, "image.zip").apply { writeText("archive") }
                archives += file
                DownloadedImage(file, "https://example.test/image.zip")
            },
            installArchive = { _, _, _, _, _ ->
                val count = installs.incrementAndGet()
                maximum.updateAndGet { maxOf(it, count) }
                installing.countDown()
                try { check(releaseInstallation.await(5, TimeUnit.SECONDS)) }
                finally { installs.decrementAndGet() }
            },
            checkStorage = { _, _ -> })
        var completed = 0
        val backend = AndroidService(fake.settings, fake)
        downloads.start(image(), backend) { completed++ }
        downloads.start(image(36), backend) { completed++ }
        try {
            withContext(Dispatchers.IO) { assertTrue(fetching.await(5, TimeUnit.SECONDS), "Both transfers must start before either finishes") }
            releaseTransfers.countDown()
            withContext(Dispatchers.IO) { assertTrue(installing.await(5, TimeUnit.SECONDS)) }
            awaitCondition { downloads.tasks.values.any { it.state == AndroidDownloadState.WaitingToInstall } }
            assertEquals(1, installs.get())
        } finally {
            releaseTransfers.countDown()
            releaseInstallation.countDown()
        }
        awaitCondition { downloads.activeCount == 0 }
        assertEquals(2, completed)
        assertEquals(1, maximum.get())
        assertTrue(archives.all { !it.exists() && !it.parentFile.exists() })
    }
}
