package com.avdbuddy

sealed interface CreationState {
    data object Idle : CreationState
    data object SettingUp : CreationState
    data object Finalizing : CreationState
    data object Cancelling : CreationState
    data object Created : CreationState
    data class Failed(val cause: Throwable, val created: Boolean) : CreationState
}
