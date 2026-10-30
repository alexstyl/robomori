package com.avdbuddy

sealed interface DeviceNameIssue {
    data object Empty : DeviceNameIssue
    data object UnsupportedCharacters : DeviceNameIssue
    data object Reserved : DeviceNameIssue
    data class Duplicate(val name: String) : DeviceNameIssue
}

fun deviceNameError(name: String, existingNames: Collection<String>): DeviceNameIssue? {
    val value = name.trim()
    return when {
        value.isEmpty() -> DeviceNameIssue.Empty
        !Regex("[A-Za-z0-9._-]+").matches(value) -> DeviceNameIssue.UnsupportedCharacters
        isReservedDeviceName(value) -> DeviceNameIssue.Reserved
        existingNames.any { it.equals(value, ignoreCase = true) } -> DeviceNameIssue.Duplicate(value)
        else -> null
    }
}

// Windows reserves these stems even when a suffix such as .avd or .ini is added.
// Keep identifiers portable so devices can be moved between operating systems.
internal fun isReservedDeviceName(name: String): Boolean =
    Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])").matches(name.substringBefore('.'))
