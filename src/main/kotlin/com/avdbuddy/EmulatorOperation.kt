package com.avdbuddy

sealed interface EmulatorOperation {
    data object Stopping : EmulatorOperation
    data object Restarting : EmulatorOperation
    data object Duplicating : EmulatorOperation
    data object Renaming : EmulatorOperation
    data object Deleting : EmulatorOperation
}
