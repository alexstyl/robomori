package com.avdbuddy

import java.io.File
import java.io.InputStream
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

data class ProfileDisplay(val width: Int, val height: Int, val density: Int)
data class ProfileFrame(val directory: String, val width: Int, val height: Int, val screenX: Int, val screenY: Int, val cornerRadius: Int)
/** A foldable's cover screen while closed: the emulator shows this region of the main display. */
data class FoldedProfile(val width: Int, val height: Int, val frame: ProfileFrame)
data class HardwareProfile(
    val id: String, val name: String, val type: DeviceType, val display: ProfileDisplay,
    val ramMiB: Int, val storageGiB: Int, val frame: ProfileFrame?, val folded: FoldedProfile? = null,
) {
    init { require(frame != null || folded == null) { "A foldable needs an open frame." } }

    fun createSpec(name: String, image: SystemImage, existingNames: Collection<String>): CreateSpec {
        require(image in creationImages(type, listOf(image))) { "Choose a compatible system image for this computer." }
        return CreateSpec(availableAvdName(name, existingNames), image, id, ramMiB, storageGiB,
            frame = frame != null, hardware = this, displayName = name.trim())
    }

    /**
     * Returns the skin folder for the AVD's `skin.path`. Foldables get the emulator's posture layout, an open
     * `default` skin beside a `closed` one, and must point at `default`: the parent has no layout of its own,
     * and the emulator then falls back to a 320x480 screen.
     */
    fun installFrame(directory: File): File? {
        val openFrame = frame ?: return null
        val root = File(directory, "avdbuddy-skin")
        if (folded == null) return root.also { installSkin(it, openFrame) }
        installSkin(File(root, "closed"), folded.frame)
        return File(root, "default").also { installSkin(it, openFrame) }
    }

    private fun installSkin(target: File, skin: ProfileFrame) {
        check(target.isDirectory || target.mkdirs()) { "Could not prepare the emulator frame." }
        for (name in listOf("layout", "back.webp", "mask.webp")) {
            File(target, name).outputStream().use { output -> profileResource("${skin.directory}/$name").use { it.copyTo(output) } }
        }
    }
}

fun profileResource(path: String): InputStream = checkNotNull(HardwareProfile::class.java.getResourceAsStream("/$path")) {
    "Missing bundled profile resource: $path"
}

object HardwareProfiles {
    private fun loadAll(): List<HardwareProfile> =
        listOf("pixel-10", "pixel-10a", "pixel-10-pro", "pixel-10-pro-xl", "pixel-tablet",
            "pixel-10-pro-fold", "pixel-watch-4-41mm", "pixel-watch-4-45mm",
            "tv-1080p").map { id ->
            profileResource("profiles/$id.yml").use { parseHardwareProfile(it) }
        }.also { profiles ->
            require(profiles.map { it.id }.distinct().size == profiles.size) { "Duplicate emulator profile IDs." }
            profiles.forEach { profile ->
                for (frame in listOfNotNull(profile.frame, profile.folded?.frame)) {
                    for (name in listOf("layout", "back.webp", "mask.webp")) profileResource("${frame.directory}/$name").close()
                }
            }
        }

    val all: List<HardwareProfile> by lazy(::loadAll)

    fun forType(type: DeviceType): List<HardwareProfile> = forType(type, all)

    internal fun forType(type: DeviceType, known: List<HardwareProfile>): List<HardwareProfile> = known.filter { it.type == type }.ifEmpty {
        // A running development app can retain an older lazy catalog after Hot Reload adds a profile.
        loadAll().filter { it.type == type }
    }
}

fun parseHardwareProfile(input: InputStream): HardwareProfile {
    val options = LoaderOptions().apply { isAllowDuplicateKeys = false; maxAliasesForCollections = 0; codePointLimit = 64 * 1024 }
    val root = Yaml(SafeConstructor(options)).load<Map<String, Any>>(input)
    fun Map<*, *>.text(key: String) = (get(key) as? String)?.takeIf(String::isNotBlank) ?: error("Missing profile field: $key")
    fun Map<*, *>.number(key: String, min: Int = 1, max: Int = 16384): Int {
        val value = get(key) as? Int ?: error("Invalid profile number: $key")
        require(value in min..max) { "Profile field $key is out of range." }
        return value
    }
    fun section(key: String) = root[key] as? Map<*, *> ?: error("Missing profile section: $key")
    require(root.number("schema") == 1) { "Unsupported profile schema." }
    val id = root.text("id")
    require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(id)) { "Invalid profile ID." }
    val display = section("display").let { ProfileDisplay(it.number("width"), it.number("height"), it.number("density", max = 1000)) }
    fun frame(section: Map<*, *>, width: Int, height: Int) = (section["frame"] as? Map<*, *> ?: error("Missing profile section: frame")).let {
        val directory = it.text("directory")
        require(Regex("device-frames/[a-z0-9-]+").matches(directory)) { "Invalid frame directory." }
        ProfileFrame(directory, it.number("width"), it.number("height"), it.number("screenX", min = 0), it.number("screenY", min = 0), it.number("cornerRadius", min = 0))
    }.also { frame ->
        require(frame.screenX + width <= frame.width && frame.screenY + height <= frame.height) { "The screen does not fit its frame." }
        require(frame.cornerRadius <= minOf(width, height) / 2) { "Invalid screen corner radius." }
    }
    val frame = if ("frame" in root) frame(root, display.width, display.height) else null
    val folded = (root["folded"] as? Map<*, *>)?.let { section ->
        val screen = section["display"] as? Map<*, *> ?: error("Missing profile section: folded.display")
        val width = screen.number("width")
        val height = screen.number("height")
        FoldedProfile(width, height, frame(section, width, height))
    }
    val emulator = section("emulator")
    val type = DeviceType.entries.firstOrNull { it.name.equals(root.text("type"), true) } ?: error("Unknown emulator type.")
    return HardwareProfile(id, root.text("name"), type, display, emulator.number("ramMiB", 512, 32768), emulator.number("storageGiB", 1, 1024), frame, folded)
}

fun nativeImageAbi(architecture: String = System.getProperty("os.arch")): String? = when (architecture.lowercase()) {
    "aarch64", "arm64" -> "arm64-v8a"
    "x86_64", "amd64" -> "x86_64"
    else -> null
}

/** Stable, host-native releases with Play services; prefer Play Store images where available. */
fun googlePlayServicesImages(images: List<SystemImage>, architecture: String = System.getProperty("os.arch")): List<SystemImage> =
    releaseImages(images, architecture, listOf("google_apis_playstore", "google_apis"))

/** Stable, host-native Wear OS releases; newer releases ship as signed images. */
fun wearOsImages(images: List<SystemImage>, architecture: String = System.getProperty("os.arch")): List<SystemImage> =
    releaseImages(images, architecture, listOf("android-wear-signed", "android-wear"))

/** Stable, host-native Android TV releases. */
fun tvImages(images: List<SystemImage>, architecture: String = System.getProperty("os.arch")): List<SystemImage> =
    releaseImages(images, architecture, listOf("android-tv"))

fun creationImages(type: DeviceType, images: List<SystemImage>, architecture: String = System.getProperty("os.arch")): List<SystemImage> = when (type) {
    DeviceType.WEAR -> wearOsImages(images, architecture)
    DeviceType.TV -> tvImages(images, architecture)
    DeviceType.PHONE, DeviceType.TABLET, DeviceType.FOLDABLE -> googlePlayServicesImages(images, architecture)
}

/** One image per release, preferring [tags] in order. */
private fun releaseImages(images: List<SystemImage>, architecture: String, tags: List<String>): List<SystemImage> {
    val abi = nativeImageAbi(architecture) ?: return emptyList()
    return images.filter { it.abi == abi && it.tag in tags && Regex("\\d+(?:\\.\\d+)*(?:-ext\\d+)?").matches(it.api) }
        .sortedWith(compareBy<SystemImage> { tags.indexOf(it.tag) }.thenBy { "-ext" in it.api }.thenByDescending { it.installed }.thenBy { it.path })
        .distinctBy { it.api.substringBefore("-ext").removeSuffix(".0") }
        .sortedWith(compareByDescending(androidReleaseOrder) { it.api })
}

fun installedCreationImages(images: List<SystemImage>, architecture: String = System.getProperty("os.arch"),
                            type: DeviceType = DeviceType.PHONE): List<SystemImage> =
    creationImages(type, images.filter { it.installed }, architecture)

fun creationApiLabel(image: SystemImage): String = image.api.substringBefore("-ext").removeSuffix(".0")

fun availableAvdName(displayName: String, existing: Collection<String>): String {
    val candidate = displayName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(80).ifBlank { "Pixel" }
    val base = if (isReservedDeviceName(candidate)) "_$candidate" else candidate
    var name = base
    var suffix = 2
    while (existing.any { it.equals(name, true) }) name = "${base}_${suffix++}"
    return name
}

fun suggestedDeviceName(base: String, existing: Collection<String>): String {
    var name = base
    var suffix = 2
    while (existing.any { it.equals(name, true) }) name = "$base ${suffix++}"
    return name
}

sealed interface CreationNameIssue {
    data object Empty : CreationNameIssue
    data object TooLong : CreationNameIssue
    data object ControlCharacters : CreationNameIssue
    data object Duplicate : CreationNameIssue
}

internal class InvalidCreationName(val issue: CreationNameIssue) : IllegalArgumentException()

fun creationNameError(name: String, existing: Collection<String> = emptyList()): CreationNameIssue? = when {
    name.isBlank() -> CreationNameIssue.Empty
    name.trim().length > 100 -> CreationNameIssue.TooLong
    name.any(Char::isISOControl) -> CreationNameIssue.ControlCharacters
    existing.any { it.equals(name.trim(), true) } -> CreationNameIssue.Duplicate
    else -> null
}
