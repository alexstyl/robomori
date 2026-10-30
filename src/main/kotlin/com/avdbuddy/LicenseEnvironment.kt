package com.avdbuddy

import dev.nucleusframework.aot.runtime.AotRuntime
import java.io.File
import java.io.StringWriter
import java.net.URI
import java.util.Properties

internal data class LocalLicenseSettings(val origin: String, val publicKey: String, val directory: File, val purchaseUrl: String)

internal fun localLicenseSettings(
    environment: Map<String, String>,
    development: Boolean,
    directory: File = File(HostPlatform().configDirectory.parentFile, "AvdBuddy Development"),
): LocalLicenseSettings? {
    if (!development) return null
    // An empty variable is unset; otherwise it would replace the saved setting with nothing.
    val environment = environment.filterValues(String::isNotBlank)
    val settingsFile = File(directory, "license-service.properties")
    val saved = Properties().apply {
        if (settingsFile.exists()) settingsFile.inputStream().use { load(it) }
    }
    val origin = (environment["AVDBUDDY_LICENSE_ORIGIN"] ?: saved.getProperty("origin")
        ?: "http://127.0.0.1:8090").trimEnd('/')
    val uri = URI(origin)
    require(uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "[::1]") &&
        uri.userInfo == null && uri.rawQuery == null && uri.fragment == null && uri.path.isNullOrEmpty()) {
        "Development licensing requires a loopback HTTP origin."
    }
    val publicKey = environment["AVDBUDDY_LICENSE_PUBLIC_KEY"] ?: saved.getProperty("publicKey") ?: LICENSE_PUBLIC_KEY
    val purchaseUrl = environment["AVDBUDDY_PURCHASE_URL"] ?: saved.getProperty("purchaseUrl")
        ?: "http://127.0.0.1:8080/pricing"
    val purchaseUri = URI(purchaseUrl)
    require(purchaseUri.scheme == "http" && purchaseUri.host in setOf("localhost", "127.0.0.1", "[::1]") &&
        purchaseUri.userInfo == null && purchaseUri.fragment == null) { "Development purchases require a loopback website URL." }
    if (environment["AVDBUDDY_LICENSE_PUBLIC_KEY"] != null) {
        val settings = Properties().apply {
            setProperty("origin", origin)
            setProperty("publicKey", publicKey)
            setProperty("purchaseUrl", purchaseUrl)
        }
        val text = StringWriter().also { settings.store(it, null) }.toString()
        atomicWrite(settingsFile, text)
    }
    return LocalLicenseSettings(origin, publicKey, directory, purchaseUrl)
}

private val localLicensing by lazy {
    // The packaged launcher sets jpackage.app-path. Packaging also runs the app with plain java to train its AOT cache;
    // that run must behave like the shipped app, or the cache would record a development startup.
    localLicenseSettings(System.getenv(),
        development = System.getProperty("jpackage.app-path") == null && !AotRuntime.isTraining())
}
internal val localLicenseTesting get() = localLicensing != null && !System.getenv("AVDBUDDY_LICENSE_ORIGIN").isNullOrBlank()
internal val licenseOrigin get() = localLicensing?.origin ?: LICENSE_ORIGIN
internal val licensePublicKey get() = localLicensing?.publicKey ?: LICENSE_PUBLIC_KEY
internal val licenseDirectory get() = localLicensing?.directory ?: HostPlatform().configDirectory
internal val purchaseLicenseUrl get() = localLicensing?.purchaseUrl ?: "https://robomori.com/pricing"
