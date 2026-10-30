package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlinx.serialization.json.*
import kotlin.test.*

class LicenseOperationTest {
    @TempDir lateinit var root: File

    @Test fun `background refresh saves the renewed receipt`(): Unit = runBlocking {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded.takeLast(32).toByteArray())
        val device = "refresh-test-device"
        File(root, "license-device-id").writeText(device)
        fun receipt(until: Long): JsonObject {
            val claims = buildJsonObject {
                put("schema", 1); put("issuer", LICENSE_ORIGIN); put("product", "avdbuddy"); put("perpetual", true)
                put("device_hash", MessageDigest.getInstance("SHA-256").digest(device.toByteArray()).joinToString("") { "%02x".format(it) })
                put("updates_until", until)
            }
            val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().toByteArray())
            val signature = Signature.getInstance("Ed25519").apply { initSign(keys.private); update(payload.toByteArray()) }.sign()
            return buildJsonObject { put("algorithm", "Ed25519"); put("key_id", "primary"); put("payload", payload); put("signature", Base64.getEncoder().encodeToString(signature)) }
        }
        File(root, "license-receipt.json").writeText(receipt(1).toString())
        val renewed = receipt(2)
        val license = AppLicense(this, root, origin = LICENSE_ORIGIN, publicKey = publicKey, request = { path, _ ->
            assertEquals("/api/v1/refresh", path)
            buildJsonObject { put("receipt", renewed) }
        })
        license.refresh()
        assertEquals(2L, license.updatesUntil?.epochSecond)
        assertEquals(renewed, Json.parseToJsonElement(File(root, "license-receipt.json").readText()))
    }

    @Test fun `offline refresh preserves the existing receipt`(): Unit = runBlocking {
        val license = licensedForTest(this, root) { _, _ -> throw java.io.IOException("offline") }
        val old = assertNotNull(license.receipt)
        license.refresh()
        assertEquals(old, license.receipt)
    }

    @Test fun `blank key produces missing key state`(): Unit = runBlocking {
        val license = AppLicense(this, root, request = { _, _ -> error("Unexpected request") })
        license.activate("  ")
        assertEquals(LicenseOperation.MissingKey, license.operation)
    }

    @Test fun `activation failure exposes the service status`(): Unit = runBlocking {
        val license = AppLicense(this, root, request = { _, _ -> throw LicenseServiceException(409) })
        license.activate("test-key")
        coroutineContext.job.children.toList().joinAll()
        val failure = assertIs<LicenseOperation.ActivationFailed>(license.operation)
        assertEquals(409, assertIs<LicenseServiceException>(failure.cause).statusCode)
    }

    @Test fun `activation stays running while awaiting the service`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val license = AppLicense(this, root, request = { _, _ ->
            entered.complete(Unit)
            release.await()
            throw LicenseServiceException(403)
        })
        license.activate("test-key")
        try {
            withTimeout(3000) { entered.await() }
            assertEquals(LicenseOperation.Activating, license.operation)
        } finally { release.complete(Unit) }
        coroutineContext.job.children.toList().joinAll()
    }

    @Test fun `cancelling activation clears the running state`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val license = AppLicense(this, root, request = { _, _ ->
            entered.complete(Unit)
            awaitCancellation()
        })
        license.activate("test-key")
        withTimeout(3000) { entered.await() }
        coroutineContext.job.children.toList().forEach { it.cancelAndJoin() }
        assertEquals(LicenseOperation.Idle, license.operation)
    }
}
