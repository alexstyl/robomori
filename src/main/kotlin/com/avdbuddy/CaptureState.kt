package com.avdbuddy

import java.io.File

sealed interface CaptureKind {
    data object Screenshot : CaptureKind
    data object Recording : CaptureKind
}

sealed interface CaptureStep {
    data object Screenshot : CaptureStep
    data object StartRecording : CaptureStep
    data object StopRecording : CaptureStep
    data object SaveRecording : CaptureStep
    data object OpenLocation : CaptureStep
}

sealed interface CaptureNotice {
    val file: File?
    data class Saved(val kind: CaptureKind, override val file: File) : CaptureNotice
    data class Failed(val step: CaptureStep, val cause: Throwable, override val file: File? = null) : CaptureNotice
}

/** An operation the user started that did not complete, shown once at the top of the window. [devices] are the display names it failed for. */
sealed interface FailedOperation {
    data class Start(val devices: List<String>) : FailedOperation
    /** The emulators started, but their first-boot Wi-Fi setup did not finish. */
    data class WifiSetup(val devices: List<String>) : FailedOperation
    data class Stop(val devices: List<String>) : FailedOperation
    data class Restart(val devices: List<String>) : FailedOperation
    data class Duplicate(val devices: List<String>) : FailedOperation
    data class Remove(val devices: List<String>) : FailedOperation
    data class Reveal(val devices: List<String>) : FailedOperation
    data object RefreshDevices : FailedOperation
    data class OpenWebsite(val url: String) : FailedOperation
}

/** Some devices in a batch failed. Carries their display names so the failure can name them. */
internal class DevicesFailed(val devices: List<String>) : Exception("Failed for ${devices.joinToString()}.")

sealed interface RecordingState {
    data object Starting : RecordingState
    data class Recording(val startedAt: Long) : RecordingState
    data class Saving(val startedAt: Long) : RecordingState
}
