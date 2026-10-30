package com.avdbuddy

import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.security.MessageDigest
import java.util.Base64

data class WirelessDevice(val name: String, val address: String, val pairing: Boolean) {
    val displayName get() = name.removeSuffix(".").removePrefix("adb-").substringBeforeLast('-')
}

internal fun WirelessDevice.matches(device: ConnectedDevice): Boolean =
    device.serial.startsWith("${address.substringBeforeLast(':')}:") ||
        device.serial.startsWith(name.substringBeforeLast('-'))

internal fun parseWirelessDevices(output: String): List<WirelessDevice> = output.lineSequence().mapNotNull { line ->
    val fields = line.trim().split(Regex("\\s+"))
    if (fields.size != 3) return@mapNotNull null
    val type = fields[1].removeSuffix(".")
    if (type !in setOf("_adb-tls-pairing._tcp", "_adb-tls-connect._tcp")) return@mapNotNull null
    val address = runCatching { wirelessAddress(fields[2]) }.getOrNull() ?: return@mapNotNull null
    WirelessDevice(fields[0], address, type == "_adb-tls-pairing._tcp")
}.distinctBy { it.address to it.pairing }.toList()

fun AndroidService.wirelessDevices(cancelled: AtomicBoolean = AtomicBoolean()): List<WirelessDevice> =
    parseWirelessDevices(command(toolchain.adb, listOf("mdns", "services"), cancelled))

data class ConnectedDevice(val serial: String, val model: String, val status: String,
    override val type: DeviceType = DeviceType.PHONE, val identity: String = serial, val customName: String? = null,
    val manufacturer: String? = null) : DeviceTarget {
    override val name get() = "connected:$serial"
    val available get() = status == "device"
    val factoryName get() = model.replace('_', ' ').ifBlank { serial }
    val manufacturerAndModel: String get() {
        val vendor = manufacturer.orEmpty().trim().replaceFirstChar { it.titlecase() }
        return if (vendor.isEmpty() || factoryName.startsWith(vendor, ignoreCase = true)) factoryName
            else "$vendor $factoryName"
    }
    override val displayName get() = customName ?: factoryName
    val wireless get() = ':' in serial || "_adb-tls-connect._tcp" in serial
}

internal fun connectedDeviceType(characteristics: String, features: String, smallestWidthDp: Int): DeviceType = when {
    "watch" in characteristics || "android.hardware.type.watch" in features -> DeviceType.WEAR
    "tv" in characteristics || "android.software.leanback" in features -> DeviceType.TV
    "android.hardware.sensor.hinge_angle" in features -> DeviceType.FOLDABLE
    "tablet" in characteristics || smallestWidthDp >= 600 -> DeviceType.TABLET
    else -> DeviceType.PHONE
}

internal sealed interface DeviceConnectionState {
    data object Ready : DeviceConnectionState
    data object AuthorizationRequired : DeviceConnectionState
    data object WirelessDisconnected : DeviceConnectionState
    data object UsbDisconnected : DeviceConnectionState
}

internal fun ConnectedDevice.connectionState(sharingFailed: Boolean): DeviceConnectionState = when {
    available && !sharingFailed -> DeviceConnectionState.Ready
    status == "unauthorized" -> DeviceConnectionState.AuthorizationRequired
    wireless -> DeviceConnectionState.WirelessDisconnected
    else -> DeviceConnectionState.UsbDisconnected
}

fun AndroidService.describeConnectedDevice(device: ConnectedDevice): ConnectedDevice {
    fun shell(vararg args: String) = command(toolchain.adb, listOf("-s", device.serial, "shell") + args)
    val features = shell("pm", "list", "features")
    val characteristics = shell("getprop", "ro.build.characteristics")
    val size = Regex("(\\d+)x(\\d+)").findAll(shell("wm", "size")).lastOrNull()
    val density = Regex("density: (\\d+)").findAll(shell("wm", "density")).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
    val smallest = if (size != null && density != null && density > 0)
        minOf(size.groupValues[1].toInt(), size.groupValues[2].toInt()) * 160 / density else 0
    fun usable(value: String) = value.trim().takeIf { it.isNotEmpty() && it != "unknown" && it != "null" }
    val identity = usable(shell("getprop", "ro.serialno")) ?: usable(shell("getprop", "ro.boot.serialno"))
        ?: usable(shell("settings", "get", "secure", "android_id")) ?: device.serial
    val manufacturer = usable(shell("getprop", "ro.product.manufacturer")).orEmpty()
    return device.copy(type = connectedDeviceType(characteristics, features, smallest), identity = identity, manufacturer = manufacturer)
}

internal fun adbKeyFingerprint(publicKey: String): String = MessageDigest.getInstance("MD5")
    .digest(Base64.getDecoder().decode(publicKey.trim().substringBefore(' ')))
    .joinToString(":") { "%02X".format(it) }

fun AndroidService.unpairDevice(device: ConnectedDevice) {
    val directory = System.getenv("ANDROID_USER_HOME")?.let(::File) ?: File(userHome, ".android")
    val keyFiles = buildList {
        add(File(directory, "adbkey.pub"))
        System.getenv("ADB_VENDOR_KEYS")?.split(File.pathSeparator)?.forEach { path ->
            val file = File(path)
            if (file.isDirectory) addAll(file.listFiles().orEmpty().filter { it.extension == "pub" })
            else add(if (file.extension == "pub") file else File("$path.pub"))
        }
    }.filter(File::isFile)
    check(keyFiles.isNotEmpty()) { "Could not find this computer’s pairing key. Forget this computer in Wireless debugging on your device." }
    val fingerprints = keyFiles.map { adbKeyFingerprint(it.readText()) }.distinct()
    revokeDeviceTrust(device.serial, fingerprints)
}

internal fun AndroidService.revokeDeviceTrust(serial: String, fingerprints: List<String>) {
    fingerprints.forEach { fingerprint ->
        val response = try {
            command(toolchain.adb, listOf("-s", serial, "shell", "service", "call", "adb", "7", "s16", fingerprint))
        } catch (failure: AndroidCommandException) {
            // Revoking trust can disconnect ADB after the successful Binder reply.
            failure.output
        }
        check(Regex("""Result:\s*Parcel\(\s*00000000\s+'\.{4}'\s*\)""").matches(response.trim())) {
            "Could not remove this computer from the device’s trusted computers."
        }
    }
}

internal fun parseConnectedDevices(output: String): List<ConnectedDevice> = output.lineSequence().mapNotNull { line ->
    val fields = line.trim().split(Regex("\\s+"))
    val serial = fields.firstOrNull().orEmpty()
    val status = fields.getOrNull(1) ?: return@mapNotNull null
    if (serial.startsWith("emulator-") || status !in setOf("device", "offline", "unauthorized", "no")) return@mapNotNull null
    val model = fields.firstOrNull { it.startsWith("model:") }?.substringAfter(':').orEmpty()
    ConnectedDevice(serial, model, if (status == "no") "unauthorized" else status)
}.toList()

fun AndroidService.connectedDevices(cancelled: AtomicBoolean = AtomicBoolean()): List<ConnectedDevice> {
    if (!toolchain.adb.canExecute()) return emptyList()
    return parseConnectedDevices(command(toolchain.adb, listOf("devices", "-l"), cancelled))
}

internal fun wirelessAddress(value: String): String {
    val address = value.trim()
    val match = Regex("(?:[A-Za-z0-9][A-Za-z0-9.-]*|\\[[0-9A-Fa-f:]+])[:]([0-9]{1,5})").matchEntire(address)
    require(match != null && match.groupValues[1].toInt() in 1..65535) {
        "Enter the IP address and port shown on your device, such as 192.168.1.10:37123."
    }
    return address
}

fun AndroidService.pairDevice(address: String, code: String, cancelled: AtomicBoolean = AtomicBoolean()): String? {
    val pairingCode = code.trim()
    require(pairingCode.matches(Regex("[0-9]{6}"))) { "Enter the six-digit pairing code shown on your device." }
    return pairDeviceWithSecret(address, pairingCode, cancelled)
}

internal fun AndroidService.pairDeviceWithSecret(address: String, secret: String, cancelled: AtomicBoolean = AtomicBoolean()): String? {
    val endpoint = wirelessAddress(address)
    require(secret.isNotEmpty() && secret.length <= 128 && secret.all { it.code in 33..126 }) { "Invalid pairing code." }
    toolchain.requireTools("adb")
    val result = command(toolchain.adb, listOf("pair", endpoint), cancelled, input = "$secret\n")
    val success = result.lineSequence()
        .map { it.trim().removePrefix("Enter pairing code:").trim() }
        .firstOrNull { it.startsWith("Successfully paired to ", ignoreCase = true) }
        ?: error("Could not pair with your device.")
    return Regex("""\[guid=([^\]\r\n]+)]""").find(success)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

fun AndroidService.connectDevice(address: String, cancelled: AtomicBoolean = AtomicBoolean()) {
    val endpoint = wirelessAddress(address)
    toolchain.requireTools("adb")
    val result = command(toolchain.adb, listOf("connect", endpoint), cancelled)
    check(result.contains("connected to $endpoint", ignoreCase = true)) {
        "Could not connect to your device. Check its connection address and that both devices use the same Wi-Fi network."
    }
}

internal fun AndroidService.tryConnectPairedDevice(guid: String, cancelled: AtomicBoolean): Boolean {
    require(guid.matches(Regex("[A-Za-z0-9_-]{1,63}"))) { "Invalid device connection identifier." }
    val serviceName = "$guid._adb-tls-connect._tcp"
    val response = try {
        command(toolchain.adb, listOf("connect", serviceName), cancelled)
    } catch (_: AndroidCommandException) {
        return false
    }
    return response.contains("connected to $serviceName", ignoreCase = true)
}

/** Reconnects a paired wireless device at the address it last used. Pairing survives a disconnect. */
internal fun AndroidService.reconnectDevice(serial: String, cancelled: AtomicBoolean = AtomicBoolean()) {
    val serviceSuffix = "._adb-tls-connect._tcp"
    val reconnected = if (serial.endsWith(serviceSuffix)) tryConnectPairedDevice(serial.removeSuffix(serviceSuffix), cancelled)
        else runCatching { connectDevice(serial, cancelled) }.isSuccess
    check(reconnected) {
        "Could not reconnect to your device. Turn on Wireless debugging and connect it to the same Wi-Fi as this computer."
    }
}

fun AndroidService.disconnectDevice(serial: String) {
    command(toolchain.adb, listOf("disconnect", serial))
}
