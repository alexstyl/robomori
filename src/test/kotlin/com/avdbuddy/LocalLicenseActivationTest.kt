package com.avdbuddy

import java.io.File
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

/** Opt-in integration check against an isolated localhost licensing service. Never sends email. */
@EnabledIfEnvironmentVariable(named = "AVDBUDDY_LOCAL_TEST_KEY_FILE", matches = ".+")
class LocalLicenseActivationTest {
    @TempDir lateinit var directory: File

    @Test fun `purchased key activates persists offline and enforces one device until deactivated`(): Unit = runBlocking {
        val key = File(System.getenv("AVDBUDDY_LOCAL_TEST_KEY_FILE")).readText().trim()
        val origin = System.getenv("AVDBUDDY_LICENSE_ORIGIN")
        require(origin.startsWith("http://127.0.0.1:"))
        val publicKey = System.getenv("AVDBUDDY_LICENSE_PUBLIC_KEY")
        val first = System.getenv("AVDBUDDY_LOCAL_TEST_DIRECTORY")?.let(::File) ?: File(directory, "first")
        require(!File(first, "license-receipt.json").exists()) { "Use a fresh local test directory." }
        val license = AppLicense(this, first, origin, publicKey)
        license.activate("")
        assertEquals(LicenseOperation.MissingKey, license.operation)
        license.activate("LIC-INVALID")
        coroutineContext.job.children.toList().joinAll()
        assertFalse(license.licensed)
        assertIs<LicenseOperation.ActivationFailed>(license.operation)
        license.activate(key)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(license.licensed)
        assertEquals(LicenseOperation.Activated, license.operation)
        assertNotNull(license.updatesThrough)
        assertTrue(AppLicense(this, first, origin, publicKey).licensed)
        assertFalse(File(first, "license-receipt.json").readText().contains(key))
        // Retrying on the same installation must reuse its slot.
        license.activate(key)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(license.licensed)
        assertEquals(LicenseOperation.Activated, license.operation)
        val other = AppLicense(this, File(directory, "second"), origin, publicKey)
        other.activate(key)
        coroutineContext.job.children.toList().joinAll()
        assertFalse(other.licensed)
        val failure = assertIs<LicenseOperation.ActivationFailed>(other.operation)
        assertEquals(409, assertIs<LicenseServiceException>(failure.cause).statusCode)
        license.deactivate()
        coroutineContext.job.children.toList().joinAll()
        assertFalse(license.licensed)
        assertEquals(LicenseOperation.Idle, license.operation)
        assertFalse(File(first, "license-receipt.json").exists())
        other.activate(key)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(other.licensed)
    }
}
