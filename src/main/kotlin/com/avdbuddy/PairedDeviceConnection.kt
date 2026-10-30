package com.avdbuddy

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

internal fun AndroidService.awaitPairedConnection(
    device: WirelessDevice,
    guid: String?,
    cancelled: AtomicBoolean,
    attempts: Int = 40,
    pause: () -> Unit = { Thread.sleep(500) },
): WirelessDevice {
    var connection = guid?.let { device.copy(name = it) } ?: device
    val host = device.address.substringBeforeLast(':')
    repeat(attempts) { attempt ->
        if (cancelled.get()) throw CancellationException("Pairing cancelled.")
        try {
            if (connectedDevices(cancelled).any { it.available && connection.matches(it) }) return connection
            if (guid != null) {
                tryConnectPairedDevice(guid, cancelled)
                if (connectedDevices(cancelled).any { it.available && connection.matches(it) }) return connection
            }
            wirelessDevices(cancelled).firstOrNull {
                !it.pairing && it.address.substringBeforeLast(':') == host
            }?.let {
                connection = it
                connectDevice(it.address, cancelled)
            }
            if (connectedDevices(cancelled).any { it.available && connection.matches(it) }) return connection
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
        }
        if (attempt < attempts - 1) pause()
    }
    error("Could not connect to your device.")
}
