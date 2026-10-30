package com.avdbuddy

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val cause: Throwable) : LoadState<Nothing>
}
