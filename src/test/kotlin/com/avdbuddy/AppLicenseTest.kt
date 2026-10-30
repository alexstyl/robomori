package com.avdbuddy

import kotlinx.serialization.json.*
import java.security.*
import java.util.Base64
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AppLicenseTest {
    @TempDir lateinit var directory: File
    private val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(pair.public.encoded.takeLast(32).toByteArray())
    private val device = "stable-test-device"
    private fun receipt(updatesUntil: Long = 1, product: String = "avdbuddy", issuer: String = LICENSE_ORIGIN): JsonObject {
        val claims = buildJsonObject {
            put("schema", 1); put("issuer", issuer); put("product", product); put("perpetual", true)
            put("device_hash", MessageDigest.getInstance("SHA-256").digest(device.toByteArray()).joinToString("") { "%02x".format(it) })
            put("updates_until", updatesUntil)
        }
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().toByteArray())
        val signature = Signature.getInstance("Ed25519").apply { initSign(pair.private); update(payload.toByteArray()) }.sign()
        return buildJsonObject { put("algorithm", "Ed25519"); put("key_id", "primary"); put("payload", payload); put("signature", Base64.getEncoder().encodeToString(signature)) }
    }
    @Test fun `perpetual receipt remains valid after update entitlement expires`() {
        assertEquals(1L, verifyLicense(receipt(), device, publicKey, LICENSE_ORIGIN).getValue("updates_until").jsonPrimitive.long)
    }
    @Test fun `development activation reloads offline without launch settings`(): Unit = runBlocking {
        val settings = assertNotNull(localLicenseSettings(mapOf("AVDBUDDY_LICENSE_PUBLIC_KEY" to publicKey), true, directory))
        File(directory, "license-device-id").writeText(device)
        File(directory, "license-receipt.json").writeText(receipt(issuer = settings.origin).toString())
        val next = assertNotNull(localLicenseSettings(emptyMap(), true, directory))
        assertTrue(AppLicense(this, next.directory, next.origin, next.publicKey).licensed)
        assertFalse(AppLicense(this, next.directory, LICENSE_ORIGIN, LICENSE_PUBLIC_KEY).licensed)
    }
    @Test fun `tampering wrong device product and signer are rejected`() {
        val valid = receipt()
        assertFails { verifyLicense(JsonObject(valid + ("payload" to JsonPrimitive(valid.getValue("payload").jsonPrimitive.content + "x"))), device, publicKey) }
        assertFails { verifyLicense(valid, "other-device", publicKey) }
        assertFails { verifyLicense(receipt(product="other"), device, publicKey) }
        assertFails { verifyLicense(valid, device) }
    }
    @Test fun `platforms match native installers and unsupported architectures do not update`() {
        assertEquals("macos-arm64", updatePlatform(HostOs.MAC, "aarch64"))
        assertEquals("windows-x64", updatePlatform(HostOs.WINDOWS, "amd64"))
        assertEquals("linux-x64", updatePlatform(HostOs.LINUX, "x86_64"))
        assertNull(updatePlatform(HostOs.LINUX, "aarch64"))
    }
}
