package com.avdbuddy

import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.*

class HardwareProfilesTest {
    @TempDir lateinit var root: File

    private fun image(api: String = "37", tag: String = "google_apis_playstore", abi: String = "arm64-v8a", installed: Boolean = false) =
        SystemImage("system-images;android-$api;$tag;$abi", "Android $api", installed)

    @ParameterizedTest
    @ValueSource(strings = ["pixel-10", "pixel-10a", "pixel-10-pro", "pixel-10-pro-xl", "pixel-tablet", "pixel-10-pro-fold", "pixel-watch-4-41mm", "pixel-watch-4-45mm", "tv-1080p"])
    fun `created profiles enable the hardware keyboard`(id: String) {
        val fake = FakeAndroid(root)
        val profile = HardwareProfiles.all.single { it.id == id }
        val tag = when (profile.type) {
            DeviceType.WEAR -> "android-wear-signed"
            DeviceType.TV -> "android-tv"
            else -> "google_apis_playstore"
        }
        val spec = profile.createSpec("Hardware Keyboard", image(tag = tag, abi = nativeImageAbi()!!, installed = true), emptyList())
        AndroidService(fake.settings, fake).create(spec, AtomicBoolean()) {}
        assertEquals("yes", readIni(File(fake.avds, "${spec.name}.avd/config.ini"))["hw.keyboard"])
    }

    @ParameterizedTest
    @ValueSource(strings = ["pixel-10", "pixel-10a", "pixel-10-pro", "pixel-10-pro-xl", "pixel-tablet", "pixel-10-pro-fold", "pixel-watch-4-41mm", "pixel-watch-4-45mm", "tv-1080p"])
    fun `bundled profile screen and actual frame images agree`(id: String) {
        val profile = HardwareProfiles.all.single { it.id == id }
        val frame = assertNotNull(profile.frame)
        for (name in listOf("back.webp", "mask.webp")) {
            profileResource("${frame.directory}/$name").use { stream ->
                Image.makeFromEncoded(stream.readBytes()).use { artwork ->
                    if (name == "mask.webp") {
                        assertEquals(profile.display.width, artwork.width)
                        assertEquals(profile.display.height, artwork.height)
                    } else {
                        // The preview canvas must retain the complete frame, including side buttons.
                        assertEquals(frame.width, artwork.width)
                        assertEquals(frame.height, artwork.height)
                    }
                }
            }
        }
        val layout = profileResource("${frame.directory}/layout").bufferedReader().use { it.readText() }
        assertContains(layout, "width ${profile.display.width}")
        assertContains(layout, "height ${profile.display.height}")
        assertContains(layout, "x ${frame.screenX}")
        assertContains(layout, "y ${frame.screenY}")
    }

    @Test fun `foldable cover screen and its frame images agree`() {
        val folded = assertNotNull(HardwareProfiles.all.single { it.id == "pixel-10-pro-fold" }.folded)
        profileResource("${folded.frame.directory}/mask.webp").use { stream ->
            Image.makeFromEncoded(stream.readBytes()).use { assertEquals(folded.width to folded.height, it.width to it.height) }
        }
        profileResource("${folded.frame.directory}/back.webp").use { stream ->
            Image.makeFromEncoded(stream.readBytes()).use { assertEquals(folded.frame.width to folded.frame.height, it.width to it.height) }
        }
        val layout = profileResource("${folded.frame.directory}/layout").bufferedReader().use { it.readText() }
        assertContains(layout, "width ${folded.width}")
        assertContains(layout, "height ${folded.height}")
        assertContains(layout, "x ${folded.frame.screenX}")
        assertContains(layout, "y ${folded.frame.screenY}")
    }

    @Test fun `foldables get a hinge that folds to the cover screen and both posture skins`() {
        val fake = FakeAndroid(root)
        val fold = HardwareProfiles.all.single { it.id == "pixel-10-pro-fold" }
        val spec = fold.createSpec("My Fold", image(abi = nativeImageAbi()!!, installed = true), emptyList())
        AndroidService(fake.settings, fake).create(spec, AtomicBoolean()) {}
        val directory = File(fake.avds, "${spec.name}.avd")
        val config = readIni(File(directory, "config.ini"))
        assertEquals("yes", config["hw.sensor.hinge"])
        assertEquals("1038-0-0-2152", config["hw.sensor.hinge.areas"])
        assertEquals("1", config["hw.sensor.hinge.fold_to_displayRegion.0.1_at_posture"])
        assertEquals("1080", config["hw.displayRegion.0.1.width"])
        assertEquals("2364", config["hw.displayRegion.0.1.height"])
        // skin.path must be the open skin itself: its parent has no layout and the emulator would fall back to 320x480.
        val skin = File(config.getValue("skin.path"))
        assertEquals("default", skin.name)
        assertTrue(File(skin, "layout").isFile)
        assertTrue(File(skin.parentFile, "closed/layout").isFile)
        assertEquals(DeviceType.FOLDABLE, Device(spec.name, directory, config, 0).type)
    }

    @Test fun `bundled profiles define RAM and ten GiB emulator storage`() {
        assertEquals(listOf("Pixel 10", "Pixel 10a", "Pixel 10 Pro", "Pixel 10 Pro XL", "Pixel Tablet", "Pixel 10 Pro Fold",
            "Pixel Watch 4 (41mm)", "Pixel Watch 4 (45mm)", "Android TV (1080p)"), HardwareProfiles.all.map { it.name })
        assertEquals(listOf(12288 to 10, 8192 to 10, 16384 to 10, 16384 to 10, 8192 to 10, 16384 to 10,
            2048 to 10, 2048 to 10, 2048 to 10),
            HardwareProfiles.all.map { it.ramMiB to it.storageGiB })
        assertEquals(listOf("pixel-tablet"), HardwareProfiles.all.filter { it.type == DeviceType.TABLET }.map { it.id })
        assertEquals(listOf("pixel-10-pro-fold"), HardwareProfiles.all.filter { it.type == DeviceType.FOLDABLE }.map { it.id })
        assertEquals(listOf("pixel-watch-4-41mm", "pixel-watch-4-45mm"), HardwareProfiles.all.filter { it.type == DeviceType.WEAR }.map { it.id })
    }

    @Test fun `wear catalog offers native Wear OS releases and prefers signed images`() {
        val images = listOf(image("37.0", "android-wear-signed"), image("36", "android-wear-signed"), image("36", "android-wear"),
            image("35-ext15", "android-wear"), image("34", "android-wear", abi = "x86_64"), image("37.2-beta1", "android-wear-signed"),
            image("36", "android-wear-cn"), image())
        assertEquals(listOf("37.0", "36", "35-ext15"), wearOsImages(images, "aarch64").map { it.api })
        assertEquals("android-wear-signed", wearOsImages(images, "aarch64")[1].tag)
        assertEquals(listOf("37"), creationImages(DeviceType.PHONE, images, "aarch64").map { it.api })
        assertEquals(wearOsImages(images, "aarch64"), creationImages(DeviceType.WEAR, images, "aarch64"))
        val watch = HardwareProfiles.all.first { it.type == DeviceType.WEAR }
        assertFails { watch.createSpec("Watch", image(abi = nativeImageAbi()!!, installed = true), emptyList()) }
        assertFails { HardwareProfiles.all.first().createSpec("Phone", image(tag = "android-wear-signed", abi = nativeImageAbi()!!), emptyList()) }
    }

    @Test fun `tv catalog offers Android TV and never offers Google TV or phone images`() {
        val images = listOf(image("36", "google-tv"), image("36", "android-tv"), image("34", "android-tv"),
            image("36", "google-tv-ps16k"), image("36"))
        assertEquals(listOf("36" to "android-tv", "34" to "android-tv"), tvImages(images, "aarch64").map { it.api to it.tag })
        assertEquals(tvImages(images, "aarch64"), creationImages(DeviceType.TV, images, "aarch64"))
        assertEquals("Android TV 16", image("36", "android-tv").platformTitle)
        assertEquals("Google Play", image("36", "android-tv").services)
        val tv = HardwareProfiles.all.single { it.type == DeviceType.TV }
        assertFails { tv.createSpec("TV", image(abi = nativeImageAbi()!!, installed = true), emptyList()) }
        assertFails { tv.createSpec("TV", image("36", "google-tv", nativeImageAbi()!!, installed = true), emptyList()) }
    }

    @Test fun `unsupported platform emulators are hidden and cannot be launched`() {
        val fake = FakeAndroid(root)
        val service = AndroidService(fake.settings, fake)
        fake.device("Phone")
        for ((name, tag) in listOf("Auto" to "android-automotive", "Desktop" to "android-desktop", "XR" to "google-xr")) {
            val directory = File(fake.avds, "$name.avd").apply { mkdirs() }
            writeIni(File(fake.avds, "$name.ini"), mapOf("path" to directory.path))
            val config = mapOf("tag.id" to tag, "hw.device.name" to name.lowercase())
            writeIni(File(directory, "config.ini"), config)
            val device = Device(name, directory, config, 0)
            assertFailsWith<IllegalArgumentException> { service.launch(device) }
            assertFailsWith<IllegalArgumentException> { service.duplicate(device) }
            assertFailsWith<IllegalArgumentException> {
                service.create(CreateSpec(name, image(tag = tag), name.lowercase()), AtomicBoolean()) {}
            }
        }
        assertEquals(listOf("Phone"), service.devices().map { it.name })
    }

    @Test fun `tv emulators have no touchscreen and are not mistaken for tablets`() {
        val fake = FakeAndroid(root)
        val tv = HardwareProfiles.all.single { it.id == "tv-1080p" }
        val spec = tv.createSpec("Living Room", image("36", "android-tv", nativeImageAbi()!!, installed = true), emptyList())
        AndroidService(fake.settings, fake).create(spec, AtomicBoolean()) {}
        val directory = File(fake.avds, "${spec.name}.avd")
        val config = readIni(File(directory, "config.ini"))
        assertEquals("no-touch", config["hw.screen"])
        assertEquals("yes", config["hw.dPad"])
        assertEquals("landscape", config["hw.initialOrientation"])
        assertEquals("true", config["PlayStore.enabled"])
        assertEquals("device-frames/tv-1080p", tv.frame?.directory)
        assertTrue(File(config.getValue("skin.path"), "back.webp").isFile)
        assertEquals("tv-1080p", config["avdbuddy.profile"])
        val device = Device(spec.name, directory, config, 0)
        assertEquals(DeviceType.TV, device.type)
        assertEquals("Android TV 16", device.platformTitle)
    }

    @Test fun `wear emulators are round with a crown and no cameras`() {
        val fake = FakeAndroid(root)
        val watch = HardwareProfiles.all.single { it.id == "pixel-watch-4-45mm" }
        val spec = watch.createSpec("My Watch", image("36", "android-wear-signed", nativeImageAbi()!!, installed = true), emptyList())
        AndroidService(fake.settings, fake).create(spec, AtomicBoolean()) {}
        val directory = File(fake.avds, "${spec.name}.avd")
        val config = readIni(File(directory, "config.ini"))
        assertEquals("true", config["hw.lcd.circular"])
        assertEquals("yes", config["hw.rotaryInput"])
        assertEquals("none", config["hw.camera.back"])
        assertEquals("none", config["hw.camera.front"])
        assertEquals("true", config["PlayStore.enabled"])
        assertEquals("ro.emulator.circular=true", File(directory, "boot.prop").readText().trim())
        val device = Device(spec.name, directory, config, 0)
        assertEquals(DeviceType.WEAR, device.type)
        assertEquals("Wear OS 6", device.platformTitle)
    }

    @Test fun `version catalog includes downloadable play images and never falls back across architectures`() {
        val images = listOf(image(), image("36", installed = true), image("35", "google_apis"),
            image("34", "default"), image("37", abi = "x86_64"), image("99-beta1"), image("canary"), image("36.1"), image("36-ext19"))
        assertEquals(listOf("37", "36.1", "36", "35"), googlePlayServicesImages(images, "aarch64").map { it.api })
        assertEquals(listOf("37"), googlePlayServicesImages(images, "amd64").map { it.api })
        assertTrue(googlePlayServicesImages(images.filter { it.abi == "arm64-v8a" }, "amd64").isEmpty())
        assertTrue(googlePlayServicesImages(images, "riscv64").isEmpty())
    }

    @Test fun `creation offers only installed compatible releases newest first`() {
        val images = listOf(image("37"), image("36", installed = true), image("35", "google_apis", installed = true),
            image("34", "default", installed = true), image("33", abi = "x86_64", installed = true),
            image("99-beta1", installed = true))
        assertEquals(listOf("36", "35"), installedCreationImages(images, "aarch64").map { it.api })
        assertEquals(listOf("33"), installedCreationImages(images, "amd64").map { it.api })
        assertTrue(installedCreationImages(listOf(image()), "aarch64").isEmpty())
    }

    @Test fun `creation keeps installed variants when preferred catalog variants are not downloaded`() {
        val installedApis = image("36", "google_apis", installed = true)
        val installedExtension = image("35-ext19", installed = true)
        val images = listOf(image("36"), installedApis, image("35"), installedExtension)
        assertEquals(listOf(installedApis, installedExtension), installedCreationImages(images, "aarch64"))
        assertEquals(listOf(image("36"), image("35")), googlePlayServicesImages(images, "aarch64"))
    }

    @Test fun `catalog includes older native releases with services and prefers Play Store when available`() {
        val older = (21..27).map { image(it.toString(), "google_apis") }
        val play = image("28")
        val catalog = googlePlayServicesImages(older + play + image("28", "google_apis", installed = true) +
            image("19", "default") + image("20", "google_apis", abi = "x86_64"), "aarch64")
        assertEquals((28 downTo 21).map(Int::toString), catalog.map { it.api })
        assertEquals(play, catalog.first())
        assertTrue(catalog.all { it.abi == "arm64-v8a" && it.tag in setOf("google_apis", "google_apis_playstore") })
    }

    @Test fun `yaml rejects invalid schema duplicate keys and screens outside the frame`() {
        val yaml = profileResource("profiles/pixel-10.yml").bufferedReader().use { it.readText() }
        fun parse(text: String) = parseHardwareProfile(ByteArrayInputStream(text.toByteArray()))
        assertFails { parse(yaml.replace("schema: 1", "schema: 2")) }
        assertFails { parse(yaml + "\nname: Duplicate\n") }
        assertFails { parse(yaml.replace("screenX: 59", "screenX: 1000")) }
        assertFails { parse(yaml.replace("device-frames/pixel-10", "../../outside")) }
        val fold = profileResource("profiles/pixel-10-pro-fold.yml").bufferedReader().use { it.readText() }
        assertFails { parse(fold.replace("    screenX: 84", "    screenX: 400")) }
    }

    @Test fun `friendly names remain display names and produce unique safe avd identifiers`() {
        assertEquals("My_Pixel_2", availableAvdName("My Pixel", listOf("my_pixel")))
        assertEquals("Pixel", availableAvdName("日本語", emptyList()))
        assertNull(creationNameError("My Pixel"))
        assertNotNull(creationNameError("  "))
        assertNotNull(creationNameError("Name\nInjected=value"))
        assertNotNull(creationNameError("My Pixel", listOf("my pixel")))
        assertEquals("My Pixel 3", suggestedDeviceName("My Pixel", listOf("my pixel", "My Pixel 2")))
    }

    @ParameterizedTest
    @ValueSource(strings = ["pixel-10", "pixel-10a", "pixel-10-pro", "pixel-10-pro-xl", "pixel-tablet", "pixel-10-pro-fold", "pixel-watch-4-41mm", "pixel-watch-4-45mm", "tv-1080p"])
    fun `fixed profile creates without SDK device definitions or SDK skins`(id: String) {
        val fake = FakeAndroid(root)
        val service = AndroidService(fake.settings, fake)
        val profile = HardwareProfiles.all.single { it.id == id }
        val tag = when (profile.type) {
            DeviceType.WEAR -> "android-wear-signed"
            DeviceType.TV -> "android-tv"
            else -> "google_apis_playstore"
        }
        val spec = profile.createSpec("My Pixel", image(tag = tag, abi = nativeImageAbi()!!, installed = true), emptyList())
        service.create(spec, AtomicBoolean()) {}
        val create = fake.commands.single { it.args.take(2) == listOf("create", "avd") }
        assertFalse("-d" in create.args)
        assertFalse(fake.commands.any { it.args == listOf("list", "device") || it.args.firstOrNull() == "--install" })
        val directory = File(fake.avds, "${spec.name}.avd")
        val config = readIni(File(directory, "config.ini"))
        assertEquals("My Pixel", config["avd.ini.displayname"])
        assertEquals(profile.display.width.toString(), config["hw.lcd.width"])
        assertEquals(profile.display.height.toString(), config["hw.lcd.height"])
        assertEquals(profile.display.density.toString(), config["hw.lcd.density"])
        assertEquals(profile.ramMiB.toString(), config["hw.ramSize"])
        assertEquals("${profile.storageGiB}G", config["disk.dataPartition.size"])
        assertEquals("true", config["PlayStore.enabled"])
        assertEquals(id, config["avdbuddy.profile"])
        assertEquals(if (profile.display.width > profile.display.height) "landscape" else "portrait", config["hw.initialOrientation"])
        assertEquals(profile.type == DeviceType.WEAR, File(directory, "boot.prop").isFile)
        assertEquals(profile.type, DeviceType.from(config))
        if (profile.frame == null) {
            assertEquals("no", config["showDeviceFrame"])
            assertFalse(File(directory, "avdbuddy-skin").exists())
        } else {
            assertTrue(File(config.getValue("skin.path"), "layout").isFile)
            assertTrue(File(config.getValue("skin.path"), "mask.webp").isFile)
        }
        assertFalse(File(fake.sdk, "skins").exists())
    }

    @Test fun `missing image downloads before creation and modified builtins are rejected`() {
        val fake = FakeAndroid(root)
        val service = AndroidService(fake.settings, fake)
        val spec = HardwareProfiles.all.single { it.id == "pixel-10" }.createSpec("Download Pixel", image(abi = nativeImageAbi()!!), emptyList())
        service.create(spec, AtomicBoolean()) {}
        assertEquals(listOf("--install", spec.image.path), fake.commands.first().args)
        val count = fake.commands.size
        assertFailsWith<IllegalArgumentException> { service.create(spec.copy(name = "Tampered", ram = 8192), AtomicBoolean()) {} }
        assertEquals(count, fake.commands.size)
    }

    @Test fun `older Google APIs images create without claiming the Play Store is installed`() {
        val fake = FakeAndroid(root)
        val spec = HardwareProfiles.all.single { it.id == "pixel-10" }.createSpec("Older Android",
            image("21", "google_apis", abi = nativeImageAbi()!!, installed = true), emptyList())
        AndroidService(fake.settings, fake).create(spec, AtomicBoolean()) {}
        assertEquals("false", readIni(File(fake.avds, "${spec.name}.avd/config.ini"))["PlayStore.enabled"])
        assertTrue(fake.commands.any { it.args.contains(spec.image.path) })
    }

    @Test fun `orphaned folder is preserved while creation uses a free storage identifier`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val orphan = File(fake.avds, "Pixel_10.avd").apply { mkdirs() }
        val savedData = File(orphan, "userdata-qemu.img").apply { writeText("keep existing data") }
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake) { spec, settings ->
            calculateStoragePlan(settings, spec, ImageFootprint(0, 0)) { DiskSpace("disk", 512 * GIB) }
        }
        val request = HardwareProfiles.all.single { it.id == "pixel-10" }.createSpec("Pixel 10", image(abi = nativeImageAbi()!!, installed = true), emptyList())
        assertEquals("Pixel_10", request.name)
        state.create(request)
        withTimeout(5000) { while (state.creating) delay(20) }

        assertEquals(CreationState.Created, state.creation)
        assertEquals("Pixel_10_2", state.creationSpec?.name)
        assertEquals("Pixel 10", state.creationSpec?.displayName)
        assertEquals("Pixel_10_2", state.previewName)
        assertEquals(setOf("Pixel_10_2"), state.selected)
        assertEquals("Pixel 10", state.devices.single().displayName)
        assertEquals("keep existing data", savedData.readText())
        assertFalse(File(fake.avds, "Pixel_10.ini").exists())
        assertEquals("Pixel 10", readIni(File(fake.avds, "Pixel_10_2.avd/config.ini"))["avd.ini.displayname"])
    }

    @Test fun `stale UI identifiers cannot overwrite a registered device with a different display name`() {
        val fake = FakeAndroid(root)
        val old = fake.device("Pixel_10")
        writeIni(File(old.directory, "config.ini"), old.config + ("avd.ini.displayname" to "Old phone"))
        val service = AndroidService(fake.settings, fake)
        val request = HardwareProfiles.all.single { it.id == "pixel-10" }.createSpec("Pixel 10", image(abi = nativeImageAbi()!!, installed = true), emptyList())
        val created = service.create(request, AtomicBoolean()) {}
        assertEquals("Pixel_10_2", created.name)
        assertEquals("Pixel 10", created.displayName)
        assertEquals("Old phone", readIni(File(old.directory, "config.ini"))["avd.ini.displayname"])
        assertEquals("fixture-data", File(old.directory, "userdata-qemu.img").readText())
    }
}
