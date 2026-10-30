package com.avdbuddy

sealed interface DisplayFailure {
    data class Connection(val cause: Throwable) : DisplayFailure
    data class Touch(val cause: Throwable) : DisplayFailure
}
