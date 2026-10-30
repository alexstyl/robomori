package com.avdbuddy

import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A license that verifies offline, for tests that exercise licensed features. */
internal fun licensedForTest(scope: CoroutineScope, directory: File,
    request: suspend (String, kotlinx.serialization.json.JsonObject) -> kotlinx.serialization.json.JsonObject = { path, input -> licenseRequest(path, input) }): AppLicense {
    directory.mkdirs()
    val deviceId = "capture-test-device"
    File(directory, "license-device-id").writeText(deviceId)
    val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded.takeLast(32).toByteArray())
    val claims = buildJsonObject {
        put("schema", 1)
        put("issuer", LICENSE_ORIGIN)
        put("product", "avdbuddy")
        put("perpetual", true)
        put("device_hash", MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray()).joinToString("") { "%02x".format(it) })
        put("updates_until", 1)
    }
    val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toString().toByteArray())
    val signature = Signature.getInstance("Ed25519").apply { initSign(keys.private); update(payload.toByteArray()) }.sign()
    File(directory, "license-receipt.json").writeText(buildJsonObject {
        put("algorithm", "Ed25519")
        put("key_id", "primary")
        put("payload", payload)
        put("signature", Base64.getEncoder().encodeToString(signature))
    }.toString())
    return AppLicense(scope, directory, origin = LICENSE_ORIGIN, publicKey = publicKey, request = request)
}
