package com.avdbuddy

internal val DeviceNameIssue.message: String get() = when (this) {
    DeviceNameIssue.Empty -> "Enter an emulator name."
    DeviceNameIssue.UnsupportedCharacters -> "Use letters, numbers, dots, underscores or hyphens. Spaces are not supported."
    DeviceNameIssue.Reserved -> "This name is reserved by Windows. Choose a different name."
    is DeviceNameIssue.Duplicate -> "An emulator named $name already exists. Choose a different name."
}

internal val CreationNameIssue.message: String get() = when (this) {
    CreationNameIssue.Empty -> "Enter a name for your emulator."
    CreationNameIssue.TooLong -> "Use a name with 100 characters or fewer."
    CreationNameIssue.ControlCharacters -> "Use a name without control characters."
    CreationNameIssue.Duplicate -> "An emulator with this name already exists. Choose another name."
}

internal val DisplayFailure.message: String get() = when (this) {
    is DisplayFailure.Connection -> cause.message ?: "Could not connect to the emulator screen."
    is DisplayFailure.Touch -> "Could not send touch input. Try restarting the emulator."
}

internal val AppState.imageDeleteErrors: Map<String, String> get() = imageDeletions.mapNotNull { (path, state) ->
    state.failure?.let { path to (it.message ?: "Could not delete this Android version. Try again.") }
}.toMap()

internal val CaptureNotice.message: String get() = when (this) {
    is CaptureNotice.Saved -> when (kind) {
        CaptureKind.Screenshot -> "Screenshot Captured"
        CaptureKind.Recording -> "Recording Captured"
    }
    is CaptureNotice.Failed -> when (step) {
        CaptureStep.Screenshot -> cause.message ?: "Could not capture the screenshot."
        CaptureStep.StartRecording -> cause.message ?: "Could not start recording."
        CaptureStep.StopRecording -> cause.message ?: "Could not stop recording."
        CaptureStep.SaveRecording -> cause.message ?: "Could not save the recording."
        CaptureStep.OpenLocation -> "Could not open the file location. The capture is saved on Desktop."
    }
}

/** Names one or two devices, and counts larger batches, so a notice stays short. */
private fun List<String>.subject(): String = when (size) {
    1 -> single()
    2 -> "${this[0]} and ${this[1]}"
    else -> "$size emulators"
}

internal val FailedOperation.message: String get() = when (this) {
    is FailedOperation.Start -> "${devices.subject()} did not start. Try again."
    is FailedOperation.WifiSetup -> "${devices.subject()} started, but Wi-Fi could not be turned on."
    is FailedOperation.Stop -> "Could not stop ${devices.subject()}. Try again."
    is FailedOperation.Restart -> "Could not restart ${devices.subject()}. Try again."
    is FailedOperation.Duplicate -> "Could not duplicate ${devices.subject()}. Try again."
    is FailedOperation.Remove -> "Could not remove ${devices.subject()}. Try again."
    is FailedOperation.Reveal -> "Could not show ${devices.subject()} in ${HostPlatform().fileManagerName}."
    FailedOperation.RefreshDevices -> "Could not refresh your devices."
    is FailedOperation.OpenWebsite -> "Could not open your browser. Go to $url"
}

internal val SettingsSaveResult.errorMessage: String? get() = when (this) {
    SettingsSaveResult.Saved -> null
    SettingsSaveResult.DownloadsActive -> "Finish or cancel Android downloads before changing SDK locations."
    SettingsSaveResult.WorkspaceBusy -> "Wait for the current operation to finish."
    SettingsSaveResult.MissingFolders -> "Choose both an SDK folder and an emulator storage folder."
    SettingsSaveResult.InvalidSdkFolder -> "Choose an available, writable SDK folder."
    SettingsSaveResult.InvalidAvdFolder -> "Choose an available, writable emulator folder."
    is SettingsSaveResult.InvalidExecutables -> "Choose executable files for: ${tools.joinToString(", ")}."
    is SettingsSaveResult.Failed -> cause.message ?: "Could not save settings."
}

internal val DeviceControlsState.error: String? get() = when (val current = snapshot) {
    is DeviceControlsSnapshot.ReadFailed -> "Couldn’t load device settings. ${current.cause.message.orEmpty()}".trim()
    is DeviceControlsSnapshot.WriteFailed -> "Couldn’t apply device settings. ${current.cause.message.orEmpty()}".trim()
    else -> null
}

internal val AppUpdates.unavailableReason: String? get() = if (canCheck) null else "Updates are available in installed builds of Robomori."
internal val AppUpdates.message: String get() = if (preferences is OperationState.Failed) "Could not save update preferences." else when (val current = state) {
    AppUpdateState.Idle -> ""
    is AppUpdateState.Checking -> "Checking for updates…"
    AppUpdateState.NoRelease -> "No updates are available for this installation yet."
    AppUpdateState.Current -> if (canInstall) "You're up to date. Robomori $APP_VERSION is installed." else "You're up to date. Robomori $APP_VERSION is running."
    is AppUpdateState.Offered -> if (!canInstall) "Robomori ${current.info.version} is available. This development run cannot install updates."
        else if (restoring) "Robomori ${current.info.version} is the latest version included with your license."
        else "Robomori ${current.info.version} is available."
    is AppUpdateState.Downloading -> "Downloading Robomori ${current.info.version} — ${current.percent}%"
    is AppUpdateState.Ready, is AppUpdateState.Installing -> if (restoring) "Robomori ${available?.version} is ready. Restart to restore it."
        else "Update ready. Restart Robomori to install it."
    is AppUpdateState.CheckFailed -> "Could not check for updates. Please try again later."
    is AppUpdateState.DownloadFailed -> "The update could not be downloaded or verified. Please try again."
    is AppUpdateState.InstallFailed -> "Could not install the update. Please try again."
}

internal val AppLicense.messageIsError get() = operation == LicenseOperation.MissingKey || operation is LicenseOperation.ActivationFailed || operation is LicenseOperation.UnlinkFailed
internal val AppLicense.message: String get() = when (val state = operation) {
    LicenseOperation.Idle -> ""
    LicenseOperation.Activating -> "Activating your license…"
    LicenseOperation.Unlinking -> "Unlinking…"
    LicenseOperation.Activated -> "License activated. Thank you for supporting Robomori."
    LicenseOperation.MissingKey -> "Enter your license key."
    is LicenseOperation.ActivationFailed -> licenseFailureMessage(state.cause) ?: "Could not activate your license. Check your connection and try again."
    is LicenseOperation.UnlinkFailed -> licenseFailureMessage(state.cause) ?: "Couldn't reach the server. Try again"
}

private fun licenseFailureMessage(cause: Throwable): String? = when (cause) {
    is LicenseServiceException -> when (cause.statusCode) {
        403 -> "Could not verify your license. Check the key or manage it in the license portal."
        409 -> "All device slots are in use. Remove a device in the license portal, then try again."
        429 -> "Too many requests. Please wait a few minutes and try again."
        else -> "Could not reach the license service. Please try again later."
    }
    is IllegalStateException -> cause.message
    else -> null
}

internal val EmulatorOperation.label: String get() = when (this) {
    EmulatorOperation.Stopping -> "Shutting down…"
    EmulatorOperation.Restarting -> "Restarting…"
    EmulatorOperation.Duplicating -> "Duplicating…"
    EmulatorOperation.Renaming -> "Renaming…"
    EmulatorOperation.Deleting -> "Deleting…"
}

internal val AppState.catalogError: String get() = catalogState.failure?.let { it.message ?: "Could not load Android versions." }.orEmpty()
internal val AppState.creationImagesError: String get() = creationImagesState.failure?.let { it.message ?: "Could not read installed Android versions." }.orEmpty()
internal val AppState.createError: String get() = (creation as? CreationState.Failed)?.let {
    (it.cause as? InvalidCreationName)?.issue?.message ?: it.cause.message ?: "Could not set up your emulator. Try again."
}.orEmpty()

internal val OperationState.deviceActionMessage: String?
    get() = failure?.let { it.message ?: "Could not complete this device action." }

internal val AndroidDownloadState.errorMessage: String?
    get() = (this as? AndroidDownloadState.Failed)?.let { it.cause.message ?: "Could not download this Android version. Try again." }

internal fun AndroidDownloadState.progressMessage(progress: DownloadProgress?): String = when (this) {
    AndroidDownloadState.CheckingStorage -> "Checking available storage…"
    AndroidDownloadState.WaitingToInstall -> "100% · Waiting to install…"
    AndroidDownloadState.Cancelling -> "Cancelling…"
    AndroidDownloadState.Installed -> "Installed"
    AndroidDownloadState.Cancelled -> "Download cancelled"
    AndroidDownloadState.Installing, AndroidDownloadState.Downloading -> listOfNotNull(
        progress?.percent?.let { "$it%" },
        progress?.message?.takeIf { it.isNotBlank() } ?: if (this == AndroidDownloadState.Installing) "Installing…" else "Downloading…",
    ).joinToString(" · ")
    is AndroidDownloadState.Failed -> errorMessage.orEmpty()
}
