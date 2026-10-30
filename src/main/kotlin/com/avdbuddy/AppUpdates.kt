package com.avdbuddy

import androidx.compose.runtime.*
import dev.nucleusframework.updater.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.util.Properties

internal fun appBundle(javaHome: File = File(System.getProperty("java.home"))): File? =
    generateSequence(javaHome) { it.parentFile }.firstOrNull { it.extension == "app" }

internal fun installedForUpdates(os: HostOs = HostOs.detect(), bundle: File? = appBundle(),
                                 appImage: String? = System.getenv("APPIMAGE"),
                                 executable: String? = System.getProperty("jpackage.app-path")): Boolean = when (os) {
    HostOs.MAC -> bundle?.let { generateSequence(it) { it.parentFile }.none { f -> f.name == "build" } } == true
    HostOs.LINUX -> !appImage.isNullOrBlank()
    HostOs.WINDOWS -> executable?.let { File(it).extension.equals("exe", true) } == true
}

internal fun updatePlatform(os: HostOs = HostOs.detect(), arch: String = System.getProperty("os.arch")): String? {
    val cpu = when (arch.lowercase()) { "aarch64", "arm64" -> "arm64"; "x86_64", "amd64", "x64" -> "x64"; else -> return null }
    return when (os) { HostOs.MAC -> "macos-$cpu"; HostOs.WINDOWS -> "windows-$cpu"; HostOs.LINUX -> "linux-$cpu" }
        .takeIf { it in setOf("macos-arm64", "macos-x64", "windows-x64", "linux-x64") }
}

internal fun isOlderRelease(target: String, installed: String): Boolean =
    Version.fromString(target) < Version.fromString(installed)

internal fun canCheckForUpdates(installed: Boolean, localLicenseTesting: Boolean, platform: String?): Boolean =
    platform != null && (installed || localLicenseTesting)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Checking(val offer: UpdateInfo? = null) : AppUpdateState
    data object NoRelease : AppUpdateState
    data object Current : AppUpdateState
    data class Offered(val info: UpdateInfo) : AppUpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : AppUpdateState
    data class Ready(val info: UpdateInfo, val file: File) : AppUpdateState
    data class Installing(val info: UpdateInfo, val file: File) : AppUpdateState
    sealed interface Failed : AppUpdateState { val cause: Throwable? }
    data class CheckFailed(override val cause: Throwable? = null) : Failed
    data class DownloadFailed(val info: UpdateInfo, override val cause: Throwable) : Failed
    data class InstallFailed(val info: UpdateInfo, val file: File, override val cause: Throwable) : Failed
}

class AppUpdates internal constructor(private val scope: CoroutineScope, private val license: AppLicense,
    val canInstall: Boolean = installedForUpdates(),
    private val platform: String? = updatePlatform(),
    val canCheck: Boolean = canCheckForUpdates(canInstall, localLicenseTesting, platform),
    private val preference: File = File(HostPlatform().configDirectory, "updates.properties"),
    private val request: suspend (JsonObject) -> JsonObject = { licenseRequest("/api/v1/updates", it) },
    private val updaterFactory: (String, Boolean) -> UpdateBackend = ::NucleusUpdateBackend,
) {
    private var updater: UpdateBackend? = null
    private var checkedReceipt: JsonObject? = null
    var automatic by mutableStateOf(runCatching { Properties().apply { preference.inputStream().use(::load) }.getProperty("automatic", "true").toBoolean() }.getOrDefault(true)); private set
    var state by mutableStateOf<AppUpdateState>(AppUpdateState.Idle); private set
    var preferences by mutableStateOf<OperationState>(OperationState.Idle); private set
    /** Forgets the outcome of saving update preferences, so it does not show again later. */
    fun clearOutcome() { if (!preferences.isRunning) preferences = OperationState.Idle }
    val working get() = state is AppUpdateState.Checking || state is AppUpdateState.Downloading || state is AppUpdateState.Installing
    val available: UpdateInfo? get() = when (val current = state) {
        is AppUpdateState.Offered -> current.info
        is AppUpdateState.Downloading -> current.info
        is AppUpdateState.Ready -> current.info
        is AppUpdateState.Installing -> current.info
        is AppUpdateState.Checking -> current.offer
        is AppUpdateState.DownloadFailed -> current.info
        is AppUpdateState.InstallFailed -> current.info
        else -> null
    }
    val downloaded: File? get() = when (val current = state) {
        is AppUpdateState.Ready -> current.file
        is AppUpdateState.Installing -> current.file
        is AppUpdateState.InstallFailed -> current.file
        else -> null
    }
    val restoring: Boolean get() = available?.let { isOlderRelease(it.version, APP_VERSION) } == true

    fun changeAutomatic(value: Boolean) {
        runCatching {
            preference.parentFile.mkdirs()
            Properties().apply { setProperty("automatic", value.toString()) }.let { props -> preference.outputStream().use { props.store(it, null) } }
            automatic = value
            preferences = OperationState.Succeeded
        }.onFailure { preferences = OperationState.Failed(it) }
    }
    suspend fun monitor() {
        if (!canCheck) return
        var nextUpdateCheck: Long? = null
        while (currentCoroutineContext().isActive) {
            val now = System.nanoTime()
            if (automatic && (nextUpdateCheck == null || now - nextUpdateCheck >= 0)) {
                check()
                nextUpdateCheck = now + 12 * 60 * 60 * 1_000_000_000L
            } else {
                val before = license.receipt
                license.refresh()
                if (automatic && before != license.receipt) check()
            }
            delay(15 * 60 * 1000L)
        }
    }
    fun check() {
        if (working) return
        if (checkedReceipt != license.receipt) {
            state = AppUpdateState.Idle; updater = null
        }
        if (downloaded != null) return
        if (!canCheck) return
        preferences = OperationState.Idle
        state = AppUpdateState.Checking(available)
        scope.launch {
            var receipt = license.receipt
            try {
                license.refresh()
                receipt = license.receipt
                val response = request(buildJsonObject {
                    put("product", "avdbuddy"); put("platform", checkNotNull(platform))
                    receipt?.let { put("receipt", it) }
                })
                if (receipt != license.receipt) return@launch
                checkedReceipt = receipt
                val release = response["release"]?.takeUnless { it is JsonNull }?.jsonObject
                if (release == null) { state = AppUpdateState.NoRelease; return@launch }
                val baseUrl = release.getValue("base_url").jsonPrimitive.content
                require(baseUrl.startsWith("https://downloads.avdbuddy.com/releases/"))
                val updater = updaterFactory(baseUrl, receipt != null)
                this@AppUpdates.updater = updater
                when (val result = updater.check()) {
                    is UpdateResult.Available -> {
                        require(result.info.version == release.getValue("version").jsonPrimitive.content)
                        if (receipt != license.receipt) return@launch
                        state = AppUpdateState.Offered(result.info)
                    }
                    is UpdateResult.NotAvailable -> state = AppUpdateState.Current
                    is UpdateResult.Error -> state = AppUpdateState.CheckFailed()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = AppUpdateState.CheckFailed(e) }
            finally {
                (state as? AppUpdateState.Checking)?.let { state = it.offer?.let(AppUpdateState::Offered) ?: AppUpdateState.Idle }
                if (currentCoroutineContext().isActive && receipt != license.receipt) check()
            }
        }
    }
    fun download() {
        if (!canCheck || !canInstall) return
        if (checkedReceipt != license.receipt) { check(); return }
        val updater = updater ?: return
        val info = available ?: return
        if (working) return
        state = AppUpdateState.Downloading(info, 0)
        scope.launch {
            var verifiedFile: File? = null
            try {
                updater.download(info).collect { progress ->
                    state = AppUpdateState.Downloading(info, progress.percent)
                    progress.file?.let {
                        if (checkedReceipt == license.receipt) {
                            verifiedFile = it
                        }
                    }
                }
                if (checkedReceipt == license.receipt) {
                    state = verifiedFile?.let { AppUpdateState.Ready(info, it) } ?: AppUpdateState.Offered(info)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = AppUpdateState.DownloadFailed(info, e) }
            finally {
                if (state is AppUpdateState.Downloading) state = AppUpdateState.Offered(info)
                if (currentCoroutineContext().isActive && checkedReceipt != license.receipt) check()
            }
        }
    }
    fun restart(busy: Boolean) {
        if (busy || working || !canCheck || !canInstall) return
        if (checkedReceipt != license.receipt) { check(); return }
        val updater = updater ?: return
        val file = downloaded ?: return
        val info = available ?: return
        state = AppUpdateState.Installing(info, file)
        scope.launch {
            try { updater.install(file) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { state = AppUpdateState.InstallFailed(info, file, e) }
            finally { if (state is AppUpdateState.Installing) state = AppUpdateState.Ready(info, file) }
        }
    }
}
