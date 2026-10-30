package com.avdbuddy

/** The lifecycle of an operation; wording belongs to the UI that presents it. */
sealed interface OperationState {
    data object Idle : OperationState
    data object Running : OperationState
    data object Succeeded : OperationState
    data class Failed(val cause: Throwable) : OperationState
}

val OperationState.isRunning get() = this == OperationState.Running
val OperationState.failure get() = (this as? OperationState.Failed)?.cause
