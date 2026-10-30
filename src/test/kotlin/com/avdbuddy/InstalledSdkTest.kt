package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.*
import java.nio.file.Files

/** CI integration smoke checks: real native SDK tools, without booting any device or UI. */
@EnabledIfEnvironmentVariable(named = "AVDBUDDY_TEST_INSTALLED_SDK", matches = "true")
@Timeout(90)
class InstalledSdkTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "AVDBUDDY_TEST_IMAGE_INSTALL", matches = "true")
    @Timeout(180)
    fun `prepared image archive installs through the real SDK tools`() {
        val avds = Files.createTempDirectory("avdbuddy-sdk-install-").toFile()
        val backend = AndroidService(AppSettings(HostPlatform().defaultSdk.path, avds.path))
        val image = backend.images().firstOrNull { it.api == "21" && it.tag == "google_apis" && it.abi == nativeImageAbi() && !it.installed }
            ?: return
        try {
            requireImageStorage(image, backend.settings)
            downloadImageArchive(image, backend.settings, AtomicBoolean()) {}.use { archive ->
                backend.installDownloadedImage(image, archive, AtomicBoolean()) {}
            }
            assertTrue(backend.images().any { it.path == image.path && it.installed })
        } finally {
            if (backend.images().any { it.path == image.path && it.installed }) backend.deleteImage(image)
            avds.deleteRecursively()
        }
    }

    @Test fun `installed host SDK tools execute through production process handling`() {
        val tools = Toolchain(HostPlatform().defaultSdk)
        assertTrue(tools.ready, "Incomplete SDK: ${tools.tools}")
        fun run(tool: java.io.File, vararg args: String) = ProcessExecutor().run(
            Command(tool, args.toList()), AtomicBoolean()) {}
        assertContains(run(tools.adb, "version"), "Android Debug Bridge")
        assertContains(run(tools.emulator, "-version"), "Android emulator version")
        assertTrue(run(tools.sdkmanager, "--version").any(Char::isDigit))
        assertTrue(parseProfiles(run(tools.avdmanager, "list", "device")).isNotEmpty())
    }
}
