package com.avdbuddy

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class InstalledImagesTest {
    @TempDir lateinit var root: File

    private fun install(sdk: File, path: String = "android-36/google_apis_playstore/arm64-v8a"): File =
        File(sdk, "system-images/$path").apply {
            mkdirs()
            File(this, "source.properties").writeText("Pkg.Desc=Google Play ARM64 System Image\n")
            File(this, "system.img").writeText("image")
        }

    private suspend fun awaitImages(state: AppState) = withTimeout(5_000) {
        while (state.creationImagesLoading) yield()
    }

    @Test fun `installed images are read from disk without launching SDK tools`() {
        val fake = FakeAndroid(root)
        install(fake.sdk)
        install(fake.sdk, "android-35/google_apis/x86_64")
        val incomplete = install(fake.sdk, "android-34/google_apis/arm64-v8a")
        File(incomplete, "system.img").delete()
        val backend = AndroidService(fake.settings, CommandExecutor { _, _, _ -> error("Must not launch a tool") })
        val images = backend.installedImages()
        assertEquals(setOf("system-images;android-36;google_apis_playstore;arm64-v8a",
            "system-images;android-35;google_apis;x86_64"), images.map { it.path }.toSet())
        assertTrue(images.all { it.installed })
        assertTrue(images.all { it.description == "Google Play ARM64 System Image" })
    }

    @Test fun `opening create uses local images even if online catalog loading fails`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        install(fake.sdk)
        val state = AppState(this, initial = fake.settings,
            executor = CommandExecutor { _, _, _ -> error("Network unavailable") })
        state.loadCatalog()
        withTimeout(5_000) { while (state.catalogState !is OperationState.Failed) yield() }
        state.resetCreate()
        awaitImages(state)
        assertTrue(state.showCreate)
        assertEquals("36", state.creationImages.single().api)
        assertEquals(OperationState.Succeeded, state.creationImagesState)
        assertIs<OperationState.Failed>(state.catalogState)
    }

    @Test fun `local images load without waiting for a catalog refresh`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        install(fake.sdk)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val state = AppState(this, initial = fake.settings, executor = CommandExecutor { command, cancelled, output ->
            if (command.args == listOf("--list")) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            fake.run(command, cancelled, output)
        })
        state.loadCatalog()
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            state.resetCreate()
            awaitImages(state)
            assertTrue(state.catalogLoading)
            assertEquals("36", state.creationImages.single().api)
            assertTrue(fake.commands.isEmpty())
        } finally { release.countDown() }
    }

    @Test fun `reopening create refreshes images installed or removed externally`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val folder = install(fake.sdk)
        val state = AppState(this, initial = fake.settings,
            executor = CommandExecutor { _, _, _ -> error("Must not launch a tool") })
        state.resetCreate()
        awaitImages(state)
        assertEquals("36", state.creationImages.single().api)
        folder.deleteRecursively()
        state.resetCreate()
        awaitImages(state)
        assertTrue(state.creationImages.isEmpty())
        assertEquals(OperationState.Succeeded, state.creationImagesState)
    }
}
