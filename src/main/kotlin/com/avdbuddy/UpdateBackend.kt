package com.avdbuddy

import dev.nucleusframework.updater.*
import dev.nucleusframework.updater.provider.GenericProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

internal data class UpdateTransfer(val percent: Int, val file: File? = null)

internal interface UpdateBackend {
    suspend fun check(): UpdateResult
    fun download(info: UpdateInfo): Flow<UpdateTransfer>
    suspend fun install(file: File)
}

internal class NucleusUpdateBackend(baseUrl: String, allowRestore: Boolean) : UpdateBackend {
    private val updater = NucleusUpdater {
        currentVersion = APP_VERSION
        allowDowngrade = allowRestore
        provider = GenericProvider(baseUrl)
        executableType = when (HostOs.detect()) { HostOs.MAC -> "zip"; HostOs.WINDOWS -> "exe"; HostOs.LINUX -> "AppImage" }
        differentialDownload = false
    }

    override suspend fun check() = updater.checkForUpdates()
    override fun download(info: UpdateInfo) = updater.downloadUpdate(info).map { UpdateTransfer(it.percent.toInt(), it.file) }
    override suspend fun install(file: File) { updater.installAndRestart(file) }
}
