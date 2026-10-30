package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class ConnectedDevicesTest {
    @TempDir lateinit var root: File

    @Test fun `USB stream failure produces USB disconnected state`() {
        val phone = ConnectedDevice("USB123", "Pixel_2", "device")
        assertEquals(DeviceConnectionState.UsbDisconnected, phone.connectionState(sharingFailed = true))
    }

    @Test fun `offline USB device produces USB disconnected state`() {
        val phone = ConnectedDevice("USB123", "Pixel_2", "offline")
        assertEquals(DeviceConnectionState.UsbDisconnected, phone.connectionState(sharingFailed = false))
    }

    @Test fun `offline USB device with a failed stream remains USB disconnected`() {
        val phone = ConnectedDevice("USB123", "Pixel_2", "offline")
        assertEquals(DeviceConnectionState.UsbDisconnected, phone.connectionState(sharingFailed = true))
    }

    @Test fun `available USB device is ready`() {
        val phone = ConnectedDevice("USB123", "Pixel_2", "device")
        assertEquals(DeviceConnectionState.Ready, phone.connectionState(sharingFailed = false))
    }

    @Test fun `authorization requirement takes precedence over stream failure`() {
        val phone = ConnectedDevice("USB123", "Pixel_2", "unauthorized")
        assertEquals(DeviceConnectionState.AuthorizationRequired, phone.connectionState(sharingFailed = true))
    }

    @Test fun `wireless stream failure produces wireless disconnected state`() {
        val phone = ConnectedDevice("192.168.1.2:12345", "Pixel", "device")
        assertEquals(DeviceConnectionState.WirelessDisconnected, phone.connectionState(sharingFailed = true))
    }

    @Test fun `offline wireless device produces wireless disconnected state`() {
        val phone = ConnectedDevice("192.168.1.2:12345", "Pixel", "offline")
        assertEquals(DeviceConnectionState.WirelessDisconnected, phone.connectionState(sharingFailed = false))
    }

    @Test fun `available wireless device is ready`() {
        val phone = ConnectedDevice("192.168.1.2:12345", "Pixel", "device")
        assertEquals(DeviceConnectionState.Ready, phone.connectionState(sharingFailed = false))
    }

    @Test fun `manufacturer accompanies the model without repeating an existing vendor`() {
        val phone = ConnectedDevice("phone", "Pixel_2", "device", manufacturer = "google")
        assertEquals("Google Pixel 2", phone.manufacturerAndModel)
        assertEquals("Pixel 2", phone.displayName)
        assertEquals("Google Pixel 2", phone.copy(model = "Google Pixel 2").manufacturerAndModel)
        assertEquals("Pixel 2", phone.copy(manufacturer = null).manufacturerAndModel)
    }

    @Test fun `device description reads the manufacturer from Android`() {
        val fake = FakeAndroid(root)
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ ->
            when (command.args.takeLast(2)) {
                listOf("getprop", "ro.product.manufacturer") -> "Google\n"
                listOf("getprop", "ro.serialno") -> "hardware-123\n"
                else -> ""
            }
        })
        val device = service.describeConnectedDevice(ConnectedDevice("phone", "Pixel_2", "device"))
        assertEquals("Google", device.manufacturer)
        assertEquals("hardware-123", device.identity)
    }

    @Test fun `hardware capabilities choose the matching controls and icon`() {
        assertEquals(DeviceType.PHONE, connectedDeviceType("default", "", 411))
        assertEquals(DeviceType.TABLET, connectedDeviceType("default", "", 800))
        assertEquals(DeviceType.FOLDABLE, connectedDeviceType("default", "feature:android.hardware.sensor.hinge_angle", 800))
        assertEquals(DeviceType.WEAR, connectedDeviceType("watch", "", 200))
        assertEquals(DeviceType.TV, connectedDeviceType("tv", "", 960))
    }

    @Test fun `wireless discovery distinguishes pairing and connection services`() {
        val services = parseWirelessDevices("""
            List of discovered mdns services
            adb-PIXEL-abc _adb-tls-pairing._tcp. 192.168.1.2:37123
            adb-PIXEL-def _adb-tls-connect._tcp. 192.168.1.2:41123
            ignored _adb._tcp. 192.168.1.3:5555
        """.trimIndent())
        assertEquals(2, services.size)
        assertTrue(services[0].pairing)
        assertFalse(services[1].pairing)
        assertEquals("PIXEL", services[0].displayName)
    }

    @Test fun `discovery separates hardware from emulators and keeps authorization state`() {
        val devices = parseConnectedDevices("""
            List of devices attached
            emulator-5554 device product:sdk model:sdk_gphone64
            USB123 device product:panther model:Pixel_7 transport_id:2
            192.168.1.4:40123 device model:Pixel_8
            LOCKED unauthorized transport_id:4
            MISSING offline
        """.trimIndent())
        assertEquals(4, devices.size)
        assertEquals("Pixel 7", devices[0].displayName)
        assertTrue(devices[1].wireless)
        assertFalse(devices[2].available)
        assertEquals("LOCKED", devices[2].displayName)
    }

    @Test fun `wireless addresses require a valid host and port`() {
        assertEquals("192.168.1.2:32123", wirelessAddress(" 192.168.1.2:32123 "))
        assertEquals("[fe80::1]:32123", wirelessAddress("[fe80::1]:32123"))
        listOf("--help", "192.168.1.2", "host:0", "host:65536", "host:22 extra").forEach {
            assertFailsWith<IllegalArgumentException> { wirelessAddress(it) }
        }
    }

    @Test fun `pairing sends the code over stdin and rejects adb failure output`() {
        val fake = FakeAndroid(root)
        val commands = mutableListOf<Command>()
        var response = "Successfully paired to 192.168.1.2:32123"
        val service = AndroidService(fake.settings, CommandExecutor { command, _, _ -> commands += command; response })
        service.pairDevice("192.168.1.2:32123", "123456")
        assertEquals(listOf("pair", "192.168.1.2:32123"), commands.single().args)
        assertEquals("123456\n", commands.single().input)
        response = "* daemon started successfully\nEnter pairing code: Successfully paired to 192.168.1.2:32123 [guid=adb-pixel-abc]\n"
        assertEquals("adb-pixel-abc", service.pairDevice("192.168.1.2:32123", "123456"))
        response = "Enter pairing code: Failed: Successfully paired but server returned unknown response=0"
        assertFailsWith<IllegalStateException> { service.pairDevice("192.168.1.2:32123", "123456") }
        response = "failed to connect to 192.168.1.2:32124"
        assertFailsWith<IllegalStateException> { service.connectDevice("192.168.1.2:32124") }
    }
}
