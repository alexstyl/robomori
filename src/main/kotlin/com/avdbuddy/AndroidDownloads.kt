package com.avdbuddy

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

data class DownloadProgress(val percent: Int? = null, val message: String = "")

/** SDK output can split a percentage or a carriage-return progress line across chunks. */
internal class DownloadProgressParser {
    private var tail = ""
    fun append(chunk: String): DownloadProgress? {
        tail = (tail + chunk).takeLast(8192)
        val match = Regex("(\\d{1,3})%[^\\r\\n]*").findAll(tail).lastOrNull() ?: return null
        val percent = match.groupValues[1].toInt().takeIf { it in 0..100 } ?: return null
        val detail = match.value.substringAfter('%').trim().take(160)
        return DownloadProgress(percent, detail)
    }
}

sealed interface AndroidDownloadState {
    data object CheckingStorage : AndroidDownloadState
    data object Downloading : AndroidDownloadState
    data object WaitingToInstall : AndroidDownloadState
    data object Installing : AndroidDownloadState
    data object Cancelling : AndroidDownloadState
    data object Installed : AndroidDownloadState
    data object Cancelled : AndroidDownloadState
    data class Failed(val cause: Throwable) : AndroidDownloadState
}

class AndroidDownload {
    val progress = MutableStateFlow(DownloadProgress())
    val cancellation = AtomicBoolean()
    var state by mutableStateOf<AndroidDownloadState>(AndroidDownloadState.CheckingStorage); internal set
    val active get() = state !is AndroidDownloadState.Failed && state != AndroidDownloadState.Installed && state != AndroidDownloadState.Cancelled
    val cancelling get() = state == AndroidDownloadState.Cancelling
    internal var job: Job? = null
}

class AndroidDownloads(
    private val scope: CoroutineScope,
    private val fetchArchive: (SystemImage, AppSettings, AtomicBoolean, (DownloadProgress) -> Unit) -> DownloadedImage = ::downloadImageArchive,
    private val installArchive: (AndroidService, SystemImage, DownloadedImage, AtomicBoolean, (String) -> Unit) -> Unit =
        { backend, image, archive, cancelled, output -> backend.installDownloadedImage(image, archive, cancelled, output) },
    private val checkStorage: (SystemImage, AppSettings) -> Unit = ::requireImageStorage,
) {
    val tasks = mutableStateMapOf<String, AndroidDownload>()
    val activeCount get() = tasks.values.count { it.active }
    // SDK installs also share this lock with device creation.
    val sdkLock = Mutex()

    fun start(image: SystemImage, backend: AndroidService, onInstalled: () -> Unit) {
        if (image.installed || tasks[image.path]?.active == true) return
        val task = AndroidDownload()
        tasks[image.path] = task
        task.job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var archive: DownloadedImage? = null
            try {
                runInterruptible(Dispatchers.IO) {
                    if (task.cancellation.get()) throw CancellationException()
                    checkStorage(image, backend.settings)
                }
                ensureActive()
                task.state = AndroidDownloadState.Downloading
                runInterruptible(Dispatchers.IO) {
                    archive = fetchArchive(image, backend.settings, task.cancellation) { task.progress.value = it }
                }
                task.state = AndroidDownloadState.WaitingToInstall
                task.progress.value = DownloadProgress(100)
                sdkLock.withLock {
                    task.state = AndroidDownloadState.Installing
                    task.progress.value = DownloadProgress()
                    runInterruptible(Dispatchers.IO) {
                        val parser = DownloadProgressParser()
                        installArchive(backend, image, checkNotNull(archive), task.cancellation) { chunk ->
                            parser.append(chunk)?.let { task.progress.value = it }
                        }
                    }
                    task.progress.value = DownloadProgress(100)
                    onInstalled()
                    task.state = AndroidDownloadState.Installed
                }
            } catch (e: Exception) {
                if (e is CancellationException && !task.cancellation.get()) throw e
                task.state = if (task.cancellation.get()) AndroidDownloadState.Cancelled else AndroidDownloadState.Failed(e)
            } finally {
                try { archive?.close() }
                finally { if (task.active) task.state = AndroidDownloadState.Cancelled }
            }
        }
    }

    fun cancel(path: String) {
        tasks[path]?.takeIf { it.active }?.let {
            it.state = AndroidDownloadState.Cancelling
            it.cancellation.set(true)
            it.job?.cancel()
        }
    }
}
