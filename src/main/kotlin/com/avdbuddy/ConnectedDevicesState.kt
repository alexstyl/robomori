package com.avdbuddy

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectedDevicesState(private val scope: CoroutineScope, private val settings: () -> AppSettings,
    private val namesStore: DeviceNamesStore = DeviceNamesStore(),
    private val pairedStore: PairedDevicesStore = PairedDevicesStore(),
    private val mirrorFactory: () -> DeviceMirror = { ScrcpyMirror() },
    private val serviceFactory: (AppSettings) -> AndroidService = { AndroidService(it) },
    /** Everything beyond screen sharing over USB is a licensed feature. */
    private val licensed: () -> Boolean = { true },
    private val onLocked: () -> Unit = {}) {
    private var names = namesStore.load()
    private var remembered by mutableStateOf(pairedStore.load())
    private var forgotten = pairedStore.loadForgotten()
    private val registryLock = Mutex()
    private var registryRevision = 0L
    var devices by mutableStateOf(remembered.map { it.copy(customName = names[it.identity]) }); private set
    var selected by mutableStateOf<ConnectedDevice?>(null)
    var showConnect by mutableStateOf(false)
    var pairing by mutableStateOf<OperationState>(OperationState.Idle); private set
    /** The latest device action, kept with its device so its outcome never shows for another device. */
    private class DeviceActionState(val identity: String, val state: OperationState)
    private var latestAction by mutableStateOf<DeviceActionState?>(null)
    /** The latest device action's state, whichever device it was for. */
    val action: OperationState get() = latestAction?.state ?: OperationState.Idle
    fun actionFor(device: ConnectedDevice): OperationState =
        latestAction?.takeIf { it.identity == device.identity }?.state ?: OperationState.Idle
    val working get() = pairing.isRunning || action.isRunning
    var nearby by mutableStateOf(emptyList<WirelessDevice>()); private set
    var discovery by mutableStateOf<OperationState>(OperationState.Idle); private set
    var renameTarget by mutableStateOf<ConnectedDevice?>(null)
    var unpairTarget by mutableStateOf<ConnectedDevice?>(null)
    fun clearActionError() { if (!action.isRunning) latestAction = null }
    private val sharingSessions = mutableStateMapOf<String, ScreenSharingSession>()
    private val sharingToResume = mutableStateMapOf<String, Unit>()
    internal fun sharingSession(device: ConnectedDevice) = sharingSessions[device.identity]?.takeIf { it.serial == device.serial }
    fun sharing(device: ConnectedDevice) = device.available && sharingSession(device) != null
    fun releaseKeyboard() { sharingSessions.values.forEach { it.mirror.releaseKeyboard() } }
    /** Only shared phones have the hardware keyboard that [PhoneSoftwareKeyboard] relies on. */
    internal fun softwareKeyboard(device: ConnectedDevice): PhoneSoftwareKeyboard? {
        if (!device.available) return null
        val session = sharingSession(device) ?: return null
        return PhoneSoftwareKeyboard(device.serial, serviceFactory(settings()), session.mirror)
    }
    fun sharingRequested(device: ConnectedDevice) = device.identity in sharingSessions || device.identity in sharingToResume
    fun isPaired(device: ConnectedDevice) = remembered.any { it.identity == device.identity }
    private fun suspendSharing(identity: String) {
        sharingSessions.remove(identity)?.let { session ->
            sharingToResume[identity] = Unit
            session.stop()
        }
    }
    /**
     * Only wireless devices can stop sharing, and stopping also disconnects them until sharing starts again.
     * USB devices share for as long as they are plugged in.
     */
    fun stopSharing(device: ConnectedDevice) {
        if (!device.wireless) return
        suspendSharing(device.identity)
        sharingToResume.remove(device.identity)
        if (!device.available) return
        markOffline(device)
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { serviceFactory(settings()).disconnectDevice(device.serial) } }
            try { refresh() } catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    /** A paired wireless device that was disconnected is reconnected at its last address before sharing. */
    fun canReconnect(device: ConnectedDevice) = device.wireless && device.status == "offline" && isPaired(device)
    fun startSharing(device: ConnectedDevice) {
        if (device.wireless && !licensed()) { onLocked(); return }
        if (!device.available) {
            if (canReconnect(device)) reconnect(device)
            return
        }
        share(device)
    }
    /** Starts or restarts the stream without asking the user anything, as refresh does on its own. */
    private fun share(device: ConnectedDevice) {
        if (!device.available) return
        if (sharing(device) && sharingSession(device)?.failure == null) return
        suspendSharing(device.identity)
        sharingToResume.remove(device.identity)
        // A failed reconnect no longer applies once the device is sharing, however it came back.
        clearActionError()
        sharingSessions[device.identity] = ScreenSharingSession(device.serial, settings(), mirrorFactory(), scope)
    }

    fun rename(device: ConnectedDevice, name: String) = deviceAction(device) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Enter a name for this device." }
        val updated = names + (device.identity to trimmed)
        withContext(Dispatchers.IO) { namesStore.save(updated) }
        names = updated
        devices = devices.map { if (it.identity == device.identity) it.copy(customName = trimmed) else it }
        selected = selected?.let { if (it.identity == device.identity) it.copy(customName = trimmed) else it }
        renameTarget = null
    }

    private fun reconnect(device: ConnectedDevice) = deviceAction(device) {
        withContext(Dispatchers.IO) { serviceFactory(settings()).reconnectDevice(device.serial) }
        sharingToResume[device.identity] = Unit
        refresh()
    }

    /** Shows the device as offline right away, without waiting for ADB to notice. */
    private fun markOffline(device: ConnectedDevice) {
        devices = devices.map { if (it.identity == device.identity) it.copy(status = "offline") else it }
        selected = selected?.let { if (it.identity == device.identity) it.copy(status = "offline") else it }
        registryRevision++
    }

    fun openRename(device: ConnectedDevice) {
        if (!licensed()) { onLocked(); return }
        clearActionError()
        renameTarget = device
    }

    fun restart(device: ConnectedDevice) {
        if (working) return
        if (!licensed()) { onLocked(); return }
        suspendSharing(device.identity)
        markOffline(device)
        deviceAction(device) {
            withContext(Dispatchers.IO) {
                val backend = serviceFactory(settings())
                backend.command(backend.toolchain.adb, listOf("-s", device.serial, "reboot"))
            }
        }
    }

    fun unpair(device: ConnectedDevice) {
        if (!isPaired(device)) return
        deviceAction(device) {
            withContext(Dispatchers.IO) {
                val backend = serviceFactory(settings())
                try {
                    backend.unpairDevice(device)
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                } finally {
                    runCatching { backend.disconnectDevice(device.serial) }
                }
            }
            registryLock.withLock {
                val updated = remembered.filterNot { it.identity == device.identity }
                val ignored = forgotten + device.identity
                withContext(Dispatchers.IO) { pairedStore.save(updated, ignored) }
                remembered = updated
                forgotten = ignored
                registryRevision++
            }
            stopSharing(device)
            devices = devices.filterNot { it.identity == device.identity }
            if (selected?.identity == device.identity) selected = null
            unpairTarget = null
        }
    }

    /** Closing a dialog also clears the outcome it showed. */
    fun dismissUnpair() { unpairTarget = null; clearActionError() }
    fun dismissRename() { renameTarget = null; clearActionError() }

    private fun deviceAction(device: ConnectedDevice, block: suspend () -> Unit) {
        if (working) return
        fun record(state: OperationState) { latestAction = DeviceActionState(device.identity, state) }
        record(OperationState.Running)
        scope.launch {
            try { block(); record(OperationState.Succeeded) }
            catch (e: Exception) { if (e is CancellationException) throw e; record(OperationState.Failed(e)) }
            finally { if (action.isRunning) latestAction = null }
        }
    }
    private var cancellation = AtomicBoolean()
    private val descriptions = remembered.associateBy { it.serial }.toMutableMap()
    fun cancel() { cancellation.set(true) }

    fun openPairing() {
        if (!pairing.isRunning) pairing = OperationState.Idle
        nearby = emptyList()
        discovery = OperationState.Idle
        showConnect = true
    }

    suspend fun refresh(restoring: WirelessDevice? = null) {
        val current = settings()
        val revision = registryRevision
        val found = withContext(Dispatchers.IO) {
            val backend = serviceFactory(current)
            backend.connectedDevices().map { device ->
                descriptions[device.serial]?.takeIf { it.manufacturer != null || !device.available }?.copy(status = device.status)
                    ?: if (!device.available) device
                    else backend.describeConnectedDevice(device).also { descriptions[device.serial] = it }
            }
        }
        registryLock.withLock {
            if (current != settings() || revision != registryRevision) return
            val restored = found.filter { candidate -> restoring != null && candidate.available && restoring.matches(candidate) }
                .map { it.identity }.toSet()
            val ignored = forgotten - restored
            val visible = found.filterNot { candidate -> candidate.wireless &&
                (candidate.identity in ignored || (!candidate.available && remembered.none { it.identity == candidate.identity })) }
            val updated = (remembered + visible.filter { it.wireless && it.available }).associateBy { it.identity }.values.toList()
            if (updated != remembered || ignored != forgotten) {
                withContext(Dispatchers.IO) { pairedStore.save(updated, ignored) }
                remembered = updated
                forgotten = ignored
            }
            devices =mergeConnectedDevices(remembered, visible).map { it.copy(customName = names[it.identity]) }
            sharingSessions.toMap().forEach { (identity, session) ->
                if (session.failure != null || session.settings != current || devices.none { it.identity == identity && it.serial == session.serial && it.available }) {
                    suspendSharing(identity)
                }
            }
            selected = selected?.let { previous -> devices.firstOrNull { it.identity == previous.identity } ?: previous.copy(status = "offline") }
            if (!licensed()) devices.filter { it.wireless }.forEach { device ->
                sharingSessions.remove(device.identity)?.stop()
                sharingToResume.remove(device.identity)
            }
            devices.filter { it.available && (!it.wireless || sharingRequested(it)) }
                .forEach(::share)
        }
    }

    suspend fun discover(qrServiceName: String? = null) {
        if (working) return
        try {
            nearby = withContext(Dispatchers.IO) {
                serviceFactory(settings()).wirelessDevices().filter {
                    it.pairing && if (qrServiceName == null) it.name.startsWith("adb-")
                        else it.name.removeSuffix(".") == qrServiceName
                }
            }
            discovery = OperationState.Succeeded
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            discovery = OperationState.Failed(e)
        }
    }

    internal suspend fun observePairing(qr: QrPairing? = null) {
        var attemptedQr = false
        while (currentCoroutineContext().isActive && pairing !is OperationState.Failed) {
            discover(qr?.serviceName)
            if (qr != null && !attemptedQr && !working) {
                nearby.firstOrNull()?.let { device ->
                    attemptedQr = true
                    pairQr(device, qr.password)
                }
            }
            delay(2000)
        }
    }

    fun pair(device: WirelessDevice, code: String) = pair(device, code, qr = false)
    internal fun pairQr(device: WirelessDevice, password: String) = pair(device, password, qr = true)

    private fun pair(device: WirelessDevice, secret: String, qr: Boolean) = operation { service, token ->
        val guid = if (qr) service.pairDeviceWithSecret(device.address, secret, token)
        else service.pairDevice(device.address, secret, token)
        service.awaitPairedConnection(device, guid, token)
    }
    private fun operation(block: (AndroidService, AtomicBoolean) -> WirelessDevice) {
        if (working) return
        pairing = OperationState.Running
        val token = AtomicBoolean().also { cancellation = it }
        scope.launch {
            val timeout = launch { delay(20000); token.set(true) }
            try {
                val device = withContext(Dispatchers.IO) { block(serviceFactory(settings()), token) }
                refresh(restoring = device)
                if (token.get()) throw CancellationException("Pairing stopped.")
                val paired = checkNotNull(devices.firstOrNull { it.available && device.matches(it) }) {
                    "The device disconnected before screen sharing could start."
                }
                selected = paired
                startSharing(paired)
                showConnect = false
                pairing = OperationState.Succeeded
            } catch (failure: Exception) {
                if (failure is CancellationException && !currentCoroutineContext().isActive) throw failure
                pairing = OperationState.Failed(failure)
            } finally {
                timeout.cancel()
                if (pairing.isRunning) pairing = OperationState.Idle
            }
        }
    }
}
