package com.avdbuddy

enum class AnimationScaleSetting(val key: String, val label: String) {
    WINDOW("window_animation_scale", "Window animation scale"),
    TRANSITION("transition_animation_scale", "Transition animation scale"),
    ANIMATOR("animator_duration_scale", "Animator duration scale"),
}

val animationScaleOptions = listOf(0f, .5f, 1f, 1.5f, 2f, 5f, 10f)
val textSizeOptions = listOf(.85f, 1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)

fun animationScaleLabel(value: Float): String {
    if (value == 0f) return "Animation off"
    val scale = value.toString().removeSuffix(".0").removePrefix("0.").let {
        if (value > 0f && value < 1f) ".$it" else it
    }
    return "Animation scale ${scale}x"
}

data class DeviceSettings(
    val darkMode: Boolean,
    val animationScales: Map<AnimationScaleSetting, Float>,
    val fontScale: Float,
    val talkBackInstalled: Boolean,
    val talkBackEnabled: Boolean,
)

class DeviceSettingsService(settings: AppSettings, executor: CommandExecutor = ProcessExecutor()) {
    private val android = AndroidService(settings, executor)

    fun isBooted(serial: String): Boolean = shell(serial, "getprop", "sys.boot_completed").trim() == "1"

    fun read(serial: String): DeviceSettings {
        // The active configuration reflects scheduled/automatic night mode too.
        val config = shell(serial, "am", "get-config").lineSequence()
            .map(String::trim).firstOrNull { it.startsWith("config:") }
            ?.substringAfter("config:")?.trim()?.split('-').orEmpty()
        val darkMode = when {
            "night" in config -> true
            "notnight" in config -> false
            else -> error("Could not read the device's appearance. Try again after Android finishes starting.")
        }
        val scales = AnimationScaleSetting.entries.associateWith { setting ->
            val value = shell(serial, "settings", "get", "global", setting.key).trim()
            val scale = if (value == "null") 1f else value.toFloatOrNull()
            check(scale != null && scale.isFinite() && scale >= 0f) {
                "Could not read ${setting.label.lowercase()}. Try again."
            }
            scale
        }
        val fontValue = shell(serial, "settings", "--user", "current", "get", "system", "font_scale").trim()
        val fontScale = if (fontValue == "null") 1f else fontValue.toFloatOrNull()
        check(fontScale != null && fontScale.isFinite() && fontScale > 0f) {
            "Could not read text size. Try again."
        }
        val talkBack = talkBackComponent(serial)
        val talkBackEnabled = talkBack != null && enabledAccessibilityServices(serial).any {
            normalizedComponent(it) == normalizedComponent(talkBack)
        } && secureSetting(serial, "accessibility_enabled") == "1"
        return DeviceSettings(darkMode, scales, fontScale, talkBack != null, talkBackEnabled)
    }

    fun setDarkMode(serial: String, enabled: Boolean) {
        val mode = if (enabled) "yes" else "no"
        val response = shell(serial, "cmd", "uimode", "night", mode)
        check(response.lineSequence().any { it.trim() == "Night mode: $mode" }) {
            "Could not change the device's appearance. ${response.trim()}".trim()
        }
    }

    fun setAnimationScale(serial: String, setting: AnimationScaleSetting, value: Float) {
        require(value in animationScaleOptions) { "Choose an Android animation scale." }
        shell(serial, "settings", "put", "global", setting.key, value.toString())
    }

    fun setFontScale(serial: String, value: Float) {
        require(value in textSizeOptions) { "Choose an Android text size." }
        shell(serial, "settings", "--user", "current", "put", "system", "font_scale", value.toString())
    }

    fun toggleSoftwareKeyboard(serial: String): Boolean {
        val snapshot = shell(serial, "dumpsys", "input_method")
        val show = !softwareKeyboardVisible(snapshot)
        shell(serial, "settings", "--user", "current", "put", "secure", "show_ime_with_hard_keyboard", if (show) "1" else "0")
        // The preference alone does not change an already open keyboard (notably Gboard).
        // DPAD_CENTER requests the IME from the focused editor without inserting text.
        // Recheck visibility because some IMEs apply the preference immediately.
        val current = shell(serial, "dumpsys", "input_method")
        if (softwareKeyboardVisible(current) != show) {
            shell(serial, "input", "dpad", "keyevent", if (show) "KEYCODE_DPAD_CENTER" else "KEYCODE_BACK")
        }
        return show
    }

    private fun softwareKeyboardVisible(snapshot: String): Boolean =
        Regex("\\bmInputShown=(true|false)\\b").find(snapshot)?.groupValues?.get(1)?.toBooleanStrict()
            ?: error("Could not read software keyboard visibility.")

    fun setTalkBack(serial: String, enabled: Boolean) {
        val component = talkBackComponent(serial) ?: error("TalkBack is not installed on this device.")
        val services = enabledAccessibilityServices(serial).filterNot {
            normalizedComponent(it) == normalizedComponent(component)
        }.toMutableList()
        if (enabled) services += component
        if (services.isEmpty()) {
            shell(serial, "settings", "--user", "current", "delete", "secure", "enabled_accessibility_services")
        } else {
            shell(serial, "settings", "--user", "current", "put", "secure", "enabled_accessibility_services", services.joinToString(":"))
        }
        if (enabled || services.isEmpty()) {
            shell(serial, "settings", "--user", "current", "put", "secure", "accessibility_enabled", if (enabled) "1" else "0")
        }
    }

    private fun talkBackComponent(serial: String): String? =
        shell(serial, "pm", "query-services", "--components", "--user", "current", "-a", "android.accessibilityservice.AccessibilityService")
            .lineSequence().map(String::trim).firstOrNull {
                normalizedComponent(it).substringAfter('/', "") == "com.google.android.marvin.talkback.TalkBackService"
            }

    private fun secureSetting(serial: String, key: String) =
        shell(serial, "settings", "--user", "current", "get", "secure", key).trim()

    private fun enabledAccessibilityServices(serial: String): List<String> =
        secureSetting(serial, "enabled_accessibility_services").takeUnless { it == "null" }.orEmpty()
            .split(':').filter(String::isNotBlank)

    private fun normalizedComponent(value: String): String {
        val parts = value.split('/', limit = 2)
        if (parts.size != 2) return value
        return parts[0] + "/" + if (parts[1].startsWith('.')) parts[0] + parts[1] else parts[1]
    }

    private fun shell(serial: String, vararg arguments: String): String {
        require(serial.isNotBlank()) { "Select a running device." }
        return android.command(android.toolchain.adb, listOf("-s", serial, "shell") + arguments)
    }
}
