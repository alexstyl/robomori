package com.avdbuddy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class PairedDevicesStoreTest {
    @TempDir lateinit var root: File
    private val phone = ConnectedDevice("192.168.1.2:12345", "Pixel 2", "device", identity = "hardware-123", manufacturer = "Google")

    @Test fun `paired devices survive app restart as offline until rediscovered`() {
        val file = File(root, "paired.json")
        PairedDevicesStore(file).save(listOf(phone))
        val remembered = PairedDevicesStore(file).load()
        assertEquals("offline", remembered.single().status)
        assertEquals(phone.identity, remembered.single().identity)
        assertEquals(phone.type, remembered.single().type)
        assertEquals(phone.manufacturer, remembered.single().manufacturer)
        assertEquals(remembered, mergeConnectedDevices(remembered, emptyList()))
    }

    @Test fun `older paired records load without a manufacturer`() {
        val file = File(root, "paired.json")
        file.writeText("""[{"serial":"phone","model":"Pixel 2","type":"PHONE","identity":"hardware-123"}]""")
        val store = PairedDevicesStore(file)
        assertNull(store.load().single().manufacturer)
        assertTrue(store.loadForgotten().isEmpty())
    }

    @Test fun `reconnection replaces the remembered transport without duplicating the device`() {
        val connected = phone.copy(serial = "192.168.1.8:54321")
        val result = mergeConnectedDevices(listOf(phone.copy(status = "offline")), listOf(connected))
        assertEquals(listOf(connected), result)
        assertEquals("offline", mergeConnectedDevices(result, emptyList()).single().status)
    }

    @Test fun `removal remains removed after store reload`() {
        val file = File(root, "paired.json")
        val store = PairedDevicesStore(file)
        store.save(listOf(phone))
        store.save(emptyList())
        assertTrue(PairedDevicesStore(file).load().isEmpty())
    }
}
