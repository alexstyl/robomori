package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class LicenseOperationTest {
    @TempDir lateinit var root: File

    @Test fun `blank key produces missing key state`(): Unit = runBlocking {
        val license = AppLicense(this, root, request = { _, _ -> error("Unexpected request") })
        license.activate("  ")
        assertEquals(LicenseOperation.MissingKey, license.operation)
    }

    @Test fun `activation failure exposes the service status`(): Unit = runBlocking {
        val license = AppLicense(this, root, request = { _, _ -> throw LicenseServiceException(409) })
        license.activate("test-key")
        coroutineContext.job.children.toList().joinAll()
        val failure = assertIs<LicenseOperation.ActivationFailed>(license.operation)
        assertEquals(409, assertIs<LicenseServiceException>(failure.cause).statusCode)
    }

    @Test fun `activation stays running while awaiting the service`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val license = AppLicense(this, root, request = { _, _ ->
            entered.complete(Unit)
            release.await()
            throw LicenseServiceException(403)
        })
        license.activate("test-key")
        try {
            withTimeout(3000) { entered.await() }
            assertEquals(LicenseOperation.Activating, license.operation)
        } finally { release.complete(Unit) }
        coroutineContext.job.children.toList().joinAll()
    }

    @Test fun `cancelling activation clears the running state`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val license = AppLicense(this, root, request = { _, _ ->
            entered.complete(Unit)
            awaitCancellation()
        })
        license.activate("test-key")
        withTimeout(3000) { entered.await() }
        coroutineContext.job.children.toList().forEach { it.cancelAndJoin() }
        assertEquals(LicenseOperation.Idle, license.operation)
    }
}
