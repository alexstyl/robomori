package com.avdbuddy

import dev.nucleusframework.updater.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class AppUpdateStateTest {
    @TempDir lateinit var root: File
    private val updateFile = UpdateFile("update.zip", "checksum", 10, null, "update.zip")
    private val info = UpdateInfo("9.0.1", "2026-10-29", listOf(updateFile), updateFile)

    private inner class Backend : UpdateBackend {
        var transfer: Flow<UpdateTransfer> = flowOf(UpdateTransfer(100, File(root, "update.zip")))
        var installFailure: Throwable? = null
        var installs = 0
        override suspend fun check() = UpdateResult.Available(info, UpdateLevel.PATCH)
        override fun download(info: UpdateInfo) = transfer
        override suspend fun install(file: File) {
            installs++
            installFailure?.let { throw it }
        }
    }

    private fun updates(scope: CoroutineScope, backend: Backend = Backend(),
        preference: File = File(root, "updates.properties"),
        request: suspend (JsonObject) -> JsonObject = { buildJsonObject {
            put("release", buildJsonObject { put("base_url", "https://downloads.avdbuddy.com/releases/9.0.1/test"); put("version", info.version) })
        } },
    ) = AppUpdates(scope, AppLicense(scope, File(root, "license")), canInstall = true,
        platform = "macos-arm64", canCheck = true, preference = preference, request = request,
        updaterFactory = { _, _ -> backend })

    private suspend fun CoroutineScope.settle() { coroutineContext.job.children.toList().joinAll() }

    @Test fun `no entitled release produces no release state`(): Unit = runBlocking {
        val updates = updates(this, request = { buildJsonObject { put("release", JsonNull) } })
        updates.check()
        settle()
        assertEquals(AppUpdateState.NoRelease, updates.state)
    }

    @Test fun `check failure preserves its cause`(): Unit = runBlocking {
        val cause = IllegalStateException()
        val updates = updates(this, request = { throw cause })
        updates.check()
        settle()
        assertSame(cause, assertIs<AppUpdateState.Failed>(updates.state).cause)
    }

    @Test fun `available release produces an offer`(): Unit = runBlocking {
        val updates = updates(this)
        updates.check()
        settle()
        assertEquals(AppUpdateState.Offered(info), updates.state)
    }

    @Test fun `download reports its progress while the transfer is running`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val backend = Backend().apply { transfer = flow {
            emit(UpdateTransfer(42))
            entered.complete(Unit)
            release.await()
        } }
        val updates = updates(this, backend)
        updates.check()
        settle()
        updates.download()
        try {
            withTimeout(3000) { entered.await() }
            assertEquals(AppUpdateState.Downloading(info, 42), updates.state)
        } finally { release.complete(Unit) }
        settle()
    }

    @Test fun `completed download retains the verified file`(): Unit = runBlocking {
        val updates = updates(this)
        updates.check()
        settle()
        updates.download()
        settle()
        assertEquals(AppUpdateState.Ready(info, File(root, "update.zip")), updates.state)
    }

    @Test fun `failed download retains the release for retry`(): Unit = runBlocking {
        val cause = IllegalStateException()
        val backend = Backend().apply { transfer = flow { throw cause } }
        val updates = updates(this, backend)
        updates.check()
        settle()
        updates.download()
        settle()
        assertEquals(AppUpdateState.DownloadFailed(info, cause), updates.state)
    }

    @Test fun `failed install retains the verified file for retry`(): Unit = runBlocking {
        val cause = IllegalStateException()
        val updates = updates(this, Backend().apply { installFailure = cause })
        updates.check()
        settle()
        updates.download()
        settle()
        updates.restart(busy = false)
        settle()
        assertEquals(AppUpdateState.InstallFailed(info, File(root, "update.zip"), cause), updates.state)
    }

    @Test fun `busy workspace prevents installation`(): Unit = runBlocking {
        val backend = Backend()
        val updates = updates(this, backend)
        updates.check()
        settle()
        updates.download()
        settle()
        updates.restart(busy = true)
        settle()
        assertEquals(0, backend.installs)
    }

    @Test fun `preference save failure does not replace a running download`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val backend = Backend().apply { transfer = flow {
            emit(UpdateTransfer(42))
            entered.complete(Unit)
            release.await()
        } }
        val blocked = File(root, "blocked").apply { writeText("file") }
        val updates = updates(this, backend, preference = File(blocked, "updates.properties"))
        updates.check()
        settle()
        updates.download()
        try {
            withTimeout(3000) { entered.await() }
            updates.changeAutomatic(false)
            assertEquals(AppUpdateState.Downloading(info, 42), updates.state)
        } finally { release.complete(Unit) }
        settle()
    }
}
