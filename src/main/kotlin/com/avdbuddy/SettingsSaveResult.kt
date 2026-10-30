package com.avdbuddy

sealed interface SettingsSaveResult {
    data object Saved : SettingsSaveResult
    data object DownloadsActive : SettingsSaveResult
    data object WorkspaceBusy : SettingsSaveResult
    data object MissingFolders : SettingsSaveResult
    data object InvalidSdkFolder : SettingsSaveResult
    data object InvalidAvdFolder : SettingsSaveResult
    data class InvalidExecutables(val tools: Set<String>) : SettingsSaveResult
    data class Failed(val cause: Throwable) : SettingsSaveResult
}
