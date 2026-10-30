package com.avdbuddy

import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.Channel
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

private val CatalogMaxAge = 1.days
private val CatalogCheckInterval = 1.hours

class AppState(val scope: CoroutineScope, private val store: SettingsStore = SettingsStore(),
               initial: AppSettings = store.load(), private val executor: CommandExecutor = ProcessExecutor(),
               val display: DeviceDisplay = AdbDeviceDisplay(),
               /** Where the SDK catalog is kept between launches; none keeps it in memory only. */
               private val catalogCache: File? = null,
               private val capture: DeviceCapture = AdbDeviceCapture(),
               val license: AppLicense = AppLicense(scope),
               private val storagePlanner: (CreateSpec, AppSettings) -> StoragePlan = ::planDeviceStorage) {
    var settings by mutableStateOf(initial); private set
    var devices by mutableStateOf(emptyList<Device>()); private set
    var running by mutableStateOf(emptyMap<String, String>()); private set
    var selected by mutableStateOf(setOf<String>())
    var previewName by mutableStateOf<String?>(null)
    val connected = ConnectedDevicesState(scope, settings = { settings },
        licensed = { license.licensed }, onLocked = { showDemoDialog = true })
    var pendingCreation by mutableStateOf<Device?>(null); private set
    val visibleDevices get() = pendingCreation?.let { pending ->
        devices.filterNot { it.name == pending.name || it.displayName == pending.displayName } + pending
    } ?: devices
    val previewDevice get() = if (connected.selected != null) null else visibleDevices.firstOrNull { it.name == previewName } ?: selectedDevices.firstOrNull() ?: visibleDevices.firstOrNull()
    val frames = mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()
    val frameRotations = mutableStateMapOf<String, Int>()
    /** Whether each running foldable was last seen folded, to show the right screen before its stream starts. */
    val foldedDevices = mutableStateMapOf<String, Boolean>()
    val displayErrors = mutableStateMapOf<String, DisplayFailure>()
    val recordingStates = mutableStateMapOf<String, RecordingState>()
    private val screenshots = mutableStateMapOf<String, OperationState>()
    val recordingDevices get() = recordingStates.filterValues { it is RecordingState.Recording || it is RecordingState.Saving }.mapValues { true }
    val recordingStartedAt get() = recordingStates.mapNotNull { (name, state) ->
        when (state) {
            RecordingState.Starting -> null
            is RecordingState.Recording -> name to state.startedAt
            is RecordingState.Saving -> name to state.startedAt
        }
    }.toMap()
    val savingCaptures get() = (screenshots.filterValues { it.isRunning }.keys +
        recordingStates.filterValues { it == RecordingState.Starting || it is RecordingState.Saving }.keys).associateWith { true }
    private val recordings = mutableMapOf<String, Recording>()
    var starting by mutableStateOf(setOf<String>()); private set
    /** Emulators this app started, which count toward the unlicensed limit of one at a time. */
    private var launchedHere = setOf<String>()
    var stopping by mutableStateOf(setOf<String>()); private set
    val deviceOperations = mutableStateMapOf<String, EmulatorOperation>()
    private var selectionAnchor: String? = null
    fun selectPreview(device: Device) = selectDevice(device)
    fun selectDevice(device: Device, toggle: Boolean = false, extend: Boolean = false,
                     orderedNames: List<String> = devices.map { it.name }) {
        connected.selected = null
        val anchor = orderedNames.indexOf(selectionAnchor)
        val target = orderedNames.indexOf(device.name)
        selected = when {
            extend && anchor >= 0 && target >= 0 -> {
                val range = orderedNames.subList(minOf(anchor, target), maxOf(anchor, target) + 1).toSet()
                if (toggle) selected + range else range
            }
            toggle -> if (device.name in selected) selected - device.name else selected + device.name
            else -> setOf(device.name)
        }
        if (!extend || anchor < 0) selectionAnchor = device.name
        previewName = if (device.name in selected) device.name else orderedNames.firstOrNull { it in selected }
    }
    fun selectForContextMenu(device: Device) {
        if (device.name !in selected) selectDevice(device)
    }
    suspend fun observeDisplays() {
        snapshotFlow {
            val device = previewDevice
            Triple(settings, device?.directory?.absolutePath, device?.takeUnless { it.name in stopping }?.let { running[it.name] })
        }.collectLatest { (currentSettings, path, serial) ->
            val device = devices.firstOrNull { it.directory.absolutePath == path } ?: return@collectLatest
            val frameKey = device.directory.absolutePath
            frames.remove(frameKey)
            displayErrors.remove(device.name)
            if (serial == null) return@collectLatest
            try {
                while (currentCoroutineContext().isActive) {
                    try {
                        display.stream(currentSettings, device, serial).collect { frame ->
                            frames[device.directory.absolutePath] = frame.bitmap
                            frameRotations[device.directory.absolutePath] = frame.rotation
                            displayErrors.remove(device.name)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (device.name !in stopping) {
                            displayErrors[device.name] = DisplayFailure.Connection(e)
                        }
                    } finally {
                        frames.remove(frameKey)
                    }
                    delay(1000)
                }
            } finally {
                frames.remove(frameKey)
                displayErrors.remove(device.name)
            }
        }
    }
    private val inputMutex = Mutex()
    fun toggleSoftwareKeyboard() {
        if (connected.selected?.available != true && previewDevice?.let { running[it.name] } == null) return
        if (!unlocked()) return
        val backend = deviceSettingsService
        connected.selected?.takeIf { it.available }?.let { phone ->
            val keyboard = connected.softwareKeyboard(phone) ?: return
            scope.launch {
                try {
                    inputMutex.withLock { withContext(Dispatchers.IO) { keyboard.toggle() } }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
            return
        }
        val serial = previewDevice?.let { running[it.name] } ?: return
        scope.launch {
            // Like other input, a failed toggle shows no error over the device's screen.
            try { inputMutex.withLock { withContext(Dispatchers.IO) { backend.toggleSoftwareKeyboard(serial) } } }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }

    val rotations = mutableStateMapOf<String, EmulatorRotation>()
    private val rotationJobs = mutableMapOf<String, Job>()
    private val rotationBases = mutableMapOf<String, Int>()
    private val appliedRotations = mutableMapOf<String, EmulatorRotation>()
    fun rotateCurrent(clockwise: Boolean): Boolean {
        val device = previewDevice ?: return false
        if (!device.type.supportsRotation) return false
        val serial = running[device.name] ?: return false
        val previous = rotations[device.name]?.takeIf { it.serial == serial }
        if (previous == null) rotationJobs.remove(device.name)?.cancel()
        // Publish before doing any IO so every press immediately retargets the preview.
        rotations[device.name] = EmulatorRotation(
            turns = (previous?.turns ?: 0) + if (clockwise) 1 else -1,
            initialRotation = previous?.initialRotation ?: frameRotations[device.directory.absolutePath],
            serial = serial,
        )
        if (rotationJobs[device.name]?.isActive == true) return true
        val backend = service
        rotationJobs[device.name] = scope.launch {
            try {
                val base = rotationBases[serial] ?: (
                    rotations[device.name]?.initialRotation ?: withContext(Dispatchers.IO) {
                        backend.displayRotation(serial)
                    }
                ).also { rotationBases[serial] = it }
                while (running[device.name] == serial) {
                    val latest = rotations[device.name]?.takeIf { it.serial == serial } ?: break
                    val applied = appliedRotations[device.name]?.takeIf { it.serial == serial }
                    if (latest.turns == (applied?.turns ?: 0)) break
                    val target = Math.floorMod(base - latest.turns, 4)
                    withContext(Dispatchers.IO) {
                        display.rotate(serial, target)
                    }
                    appliedRotations[device.name] = latest
                    // Re-read the latest target instead of queuing intermediate keypresses.
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (rotations[device.name]?.serial == serial) {
                    val applied = appliedRotations[device.name]?.takeIf { it.serial == serial }
                    if (applied == null) rotations.remove(device.name) else rotations[device.name] = applied
                }
            }
        }
        return true
    }
    fun sendTouch(device: Device, x: Int, y: Int, pressed: Boolean) {
        val serial = running[device.name] ?: return
        scope.launch {
            try { inputMutex.withLock { withContext(Dispatchers.IO) { display.touch(serial, x, y, pressed) } } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                displayErrors[device.name] = DisplayFailure.Touch(e)
            }
        }
    }
    fun fold(device: Device, folded: Boolean) {
        val serial = running[device.name] ?: return
        val current = settings
        scope.launch {
            // Input fails quietly: it happens too often to report each failure.
            try { inputMutex.withLock { withContext(Dispatchers.IO) { display.fold(current, serial, folded) } } }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    fun sendInput(device: Device, arguments: List<String>) {
        val serial = running[device.name] ?: return
        val current = settings
        scope.launch {
            try { inputMutex.withLock { withContext(Dispatchers.IO) { display.input(current, serial, arguments) } } }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    fun captureScreenshot(device: DeviceTarget) {
        val serial = if (device is ConnectedDevice) device.serial.takeIf { device.available } else running[device.name]
        if (serial == null) return
        if (!unlocked()) return
        if (savingCaptures[device.name] == true) return
        screenshots[device.name] = OperationState.Running
        dismissCaptureNotice(device.name)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { capture.screenshot(settings, serial, device.displayName) }
                showCapture(device.name, CaptureNotice.Saved(CaptureKind.Screenshot, file))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showCapture(device.name, CaptureNotice.Failed(CaptureStep.Screenshot, e))
            } finally { screenshots.remove(device.name) }
        }
    }
    /**
     * Whether a licensed feature may be used. Without a license this opens the purchase prompt instead.
     * The free path is creating, running and using one emulator at a time, and screen sharing over USB.
     */
    fun unlocked(): Boolean {
        if (license.licensed) return true
        showDemoDialog = true
        return false
    }
    /** Wireless pairing is a licensed feature; USB devices share without it. */
    fun pairDeviceWirelessly() {
        if (unlocked()) connected.openPairing()
    }
    fun openRename(device: Device) { if (unlocked()) renameDevice = device }
    fun openWipeData(device: Device) { if (unlocked()) wipeDataDevice = device }
    fun toggleRecording(device: DeviceTarget) {
        val existing = recordings[device.name]
        if (existing != null) {
            if (savingCaptures[device.name] == true) return
            val startedAt = recordingStartedAt[device.name] ?: return
            recordingStates[device.name] = RecordingState.Saving(startedAt)
            scope.launch {
                try { withContext(Dispatchers.IO) { existing.stop() } }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    showCapture(device.name, CaptureNotice.Failed(CaptureStep.StopRecording, e))
                    if (recordings[device.name] === existing) recordingStates[device.name] = RecordingState.Recording(startedAt)
                }
            }
            return
        }
        val serial = (if (device is ConnectedDevice) device.serial.takeIf { device.available } else running[device.name]) ?: return
        if (!license.licensed) {
            showDemoDialog = true
            return
        }
        if (savingCaptures[device.name] == true) return
        recordingStates[device.name] = RecordingState.Starting
        dismissCaptureNotice(device.name)
        scope.launch {
            try {
                val recording = withContext(Dispatchers.IO) { capture.startRecording(settings, serial, device.displayName, device.type) }
                recordings[device.name] = recording
                recordingStates[device.name] = RecordingState.Recording(System.nanoTime())
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) { recording.save() }
                        showCapture(device.name, CaptureNotice.Saved(CaptureKind.Recording, file))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        showCapture(device.name, CaptureNotice.Failed(CaptureStep.SaveRecording, e))
                    } finally {
                        recordings.remove(device.name)
                        recordingStates.remove(device.name)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showCapture(device.name, CaptureNotice.Failed(CaptureStep.StartRecording, e))
            } finally {
                if (recordingStates[device.name] == RecordingState.Starting) recordingStates.remove(device.name)
            }
        }
    }
    fun openCapture(device: DeviceTarget, file: File) {
        dismissCaptureNotice(device.name)
        scope.launch {
            try { withContext(Dispatchers.IO) { revealInFileManager(file) } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                showCapture(device.name, CaptureNotice.Failed(CaptureStep.OpenLocation, e, file))
            }
        }
    }
    fun type(device: Device, text: String) {
        val serial = running[device.name] ?: return
        scope.launch {
            try { inputMutex.withLock { withContext(Dispatchers.IO) { display.type(serial, text) } } }
            catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    fun paste(device: Device) {
        val serial = running[device.name] ?: return
        val current = settings
        scope.launch {
            try {
                inputMutex.withLock { withContext(Dispatchers.IO) {
                    val text = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
                    if (text.isNotEmpty()) display.paste(current, serial, text)
                } }
            } catch (e: Exception) { if (e is CancellationException) throw e }
        }
    }
    var images by mutableStateOf(emptyList<SystemImage>()); private set
    var creationImages by mutableStateOf(emptyList<SystemImage>()); private set
    var creationImagesState by mutableStateOf<OperationState>(OperationState.Idle); private set
    val creationImagesLoading get() = creationImagesState.isRunning
    private var workspaceOperation by mutableStateOf<OperationState>(OperationState.Idle)
    val busy get() = workspaceOperation.isRunning || creating
    var catalogState by mutableStateOf<OperationState>(OperationState.Idle); private set
    val catalogLoading get() = catalogState.isRunning
    var showSettings by mutableStateOf(false)
    var settingsSection by mutableStateOf<SettingsSection>(SettingsSection.Images); private set
    fun openSettings(section: SettingsSection = settingsSection) {
        // Outcomes from an earlier visit to Settings no longer apply.
        license.clearOutcome()
        updates.clearOutcome()
        settingsSection = section
        showSettings = true
        if (section == SettingsSection.Images) {
            clearImageDeleteFailures()
            loadCatalog()
        }
    }
    val downloads = AndroidDownloads(scope)
    /** Images being uninstalled. Only their own rows are blocked; SDK changes queue on [AndroidDownloads.sdkLock]. */
    val imageDeletions = mutableStateMapOf<String, OperationState>()
    val deletingImagePaths get() = imageDeletions.filterValues { it.isRunning }.keys
    private fun clearImageDeleteFailures() {
        imageDeletions.keys.filter { imageDeletions[it] is OperationState.Failed }.forEach(imageDeletions::remove)
    }
    fun deleteImage(image: SystemImage) {
        if (!image.installed || image.path in deletingImagePaths) return
        val backend = service
        imageDeletions[image.path] = OperationState.Running
        scope.launch {
            try {
                downloads.sdkLock.withLock { withContext(Dispatchers.IO) { backend.deleteImage(image) } }
                if (settings == backend.settings) {
                    images = images.map { if (it.path == image.path) it.copy(installed = false) else it }
                    downloads.tasks.remove(image.path)
                    imageDeletions[image.path] = OperationState.Succeeded
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (settings == backend.settings) imageDeletions[image.path] = OperationState.Failed(e)
            } finally {
                if (settings == backend.settings && imageDeletions[image.path]?.isRunning == true) imageDeletions.remove(image.path)
            }
        }
    }
    fun downloadImage(image: SystemImage) {
        val backend = service
        downloads.start(image, backend) {
            if (settings == backend.settings) images = images.map { if (it.path == image.path) it.copy(installed = true) else it }
        }
    }
    val hasOpenDialog get() = showSettings || showCreate || renameDevice != null ||
        deleteDevices.isNotEmpty() || wipeDataDevice != null || showDemoDialog || connected.showConnect ||
        connected.renameTarget != null || connected.unpairTarget != null
    var showCreate by mutableStateOf(false)
    var createType by mutableStateOf(DeviceType.PHONE); private set
    var renameDevice by mutableStateOf<Device?>(null)
    var deleteDevices by mutableStateOf(emptyList<Device>())
    var wipeDataDevice by mutableStateOf<Device?>(null)
    var showDemoDialog by mutableStateOf(false)
    var creation by mutableStateOf<CreationState>(CreationState.Idle); private set
    val creating get() = creation == CreationState.SettingUp || creation == CreationState.Finalizing || creation == CreationState.Cancelling
    val createCancelling get() = creation == CreationState.Cancelling
    var creationSpec by mutableStateOf<CreateSpec?>(null); private set
    private var cancellation = AtomicBoolean()
    private var catalogJob: Job? = null
    private var catalogRevision = 0L
    private var creationImagesRevision = 0L
    val service get() = AndroidService(settings, executor)
    val deviceSettingsService get() = DeviceSettingsService(settings, executor)
    val ready get() = service.toolchain.hasTools("emulator", "adb", "avdmanager")
    val selectedDevices get() = devices.filter { it.name in selected }
    val totalBytes get() = devices.sumOf(Device::bytes)

    suspend fun refresh() {
        val backend = service
        val list = withContext(Dispatchers.IO) { backend.devices() }
        if (settings == backend.settings) {
            if (devices != list) clearImageDeleteFailures()
            devices = list
            selected = selected.intersect(visibleDevices.map { it.name }.toSet())
        }
        val active = withContext(Dispatchers.IO) { runCatching { backend.running() }.getOrDefault(emptyMap()) }
        if (settings == backend.settings) {
            running.filter { (name, serial) -> active[name] != serial }.forEach { (name, serial) ->
                launchedHere = launchedHere - name
                rotationJobs.remove(name)?.cancel()
                rotations.remove(name)
                appliedRotations.remove(name)
                rotationBases.remove(serial)
                devices.firstOrNull { it.name == name }?.let {
                    frames.remove(it.directory.absolutePath)
                    frameRotations.remove(it.directory.absolutePath)
                    foldedDevices.remove(it.directory.absolutePath)
                }
                displayErrors.remove(name)
            }
            running = active
            stopping = stopping.intersect(active.keys)
        }
    }
    fun refreshAsync() = scope.launch {
        try { refresh() }
        catch (e: Exception) { if (e is CancellationException) throw e; notifyFailure(FailedOperation.RefreshDevices) }
    }
    /** Operations the user started that did not complete, each delivered once to whoever shows them. */
    internal val failureEvents = Channel<FailedOperation>(Channel.UNLIMITED)
    val failures: Flow<FailedOperation> = failureEvents.receiveAsFlow()
    private fun notifyFailure(operation: FailedOperation) { failureEvents.trySend(operation) }

    /** The latest capture result for each device, shown above its toolbar until it is dismissed or replaced. */
    val captureNotices = mutableStateMapOf<String, CaptureNotice>()
    private fun showCapture(device: String, notice: CaptureNotice) { captureNotices[device] = notice }
    fun dismissCaptureNotice(device: String) { captureNotices.remove(device) }
    /** Dismisses [notice] only if it is still the one showing, so an older timer cannot hide a newer result. */
    fun dismissCaptureNotice(device: String, notice: CaptureNotice) { captureNotices.remove(device, notice) }
    /**
     * Runs a device operation. On failure, [failed] turns the display names it failed for into a notice;
     * operations that show their own error, such as rename in its dialog, pass none.
     */
    private fun action(onResult: (Throwable?) -> Unit = {}, operations: Map<String, EmulatorOperation> = emptyMap(),
                       blocksWorkspace: Boolean = true, failed: ((List<String>) -> FailedOperation)? = null,
                       block: (AndroidService) -> Unit) {
        if (busy || operations.keys.any { it in deviceOperations }) return
        val backend = service
        val titles = operations.keys.map { name -> devices.firstOrNull { it.name == name }?.displayName ?: name }
        deviceOperations.putAll(operations)
        if (blocksWorkspace) workspaceOperation = OperationState.Running
        scope.launch {
            try { withContext(Dispatchers.IO) { block(backend) }; refresh(); onResult(null) }
            catch (t: Exception) {
                if (t is CancellationException) throw t
                runCatching { refresh() }
                failed?.let { notifyFailure(it((t as? DevicesFailed)?.devices ?: titles)) }
                onResult(t)
            }
            finally {
                operations.keys.forEach(deviceOperations::remove)
                if (blocksWorkspace) workspaceOperation = OperationState.Idle
            }
        }
    }
    fun launch(device: Device, wipeData: Boolean = false) {
        if (busy || device.name in running || device.name in starting || device.name in deviceOperations) return
        selectPreview(device)
        launch(listOf(device), wipeData = wipeData)
    }
    fun launch(devices: List<Device>, coldBoot: Boolean = false, wipeData: Boolean = false) {
        if (busy) return
        val targets = devices.distinctBy { it.name }.filter {
            it.name !in running && it.name !in starting && it.name !in deviceOperations && !it.missingStorage
        }
        if (targets.isEmpty()) return
        val names = targets.map { it.name }.toSet()
        // Without a license, AvdBuddy runs one emulator at a time. Emulators started elsewhere do not count.
        if ((starting + launchedHere.intersect(running.keys) + names).size > 1 && !unlocked()) return
        starting = starting + names
        workspaceOperation = OperationState.Running
        val backend = service
        scope.launch {
            val failedToStart = mutableListOf<String>()
            val failedWifi = mutableListOf<String>()
            val launched = mutableSetOf<String>()
            try {
                for (device in targets) {
                    try {
                        withContext(Dispatchers.IO) { backend.launch(device, coldBoot, wipeData) }
                        launched += device.name
                        launchedHere = launchedHere + device.name
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failedToStart += device.displayName
                        starting = starting - device.name
                    }
                }
            } catch (cancelled: CancellationException) {
                starting = starting - names
                throw cancelled
            } finally { workspaceOperation = OperationState.Idle }
            try {
                val started = withTimeoutOrNull(90_000) {
                    while (!running.keys.containsAll(launched)) { refresh(); delay(1000) }
                    true
                } ?: false
                if (!started) failedToStart += targets.filter { it.name in launched && it.name !in running }.map { it.displayName }
                if (started) targets.filter { it.name in launched && it.needsInitialWifi }.forEach { device ->
                    val serial = running[device.name] ?: return@forEach
                    val booted = withTimeoutOrNull(90_000) {
                        while (!withContext(Dispatchers.IO) { runCatching { backend.bootCompleted(serial) }.getOrDefault(false) }) delay(1000)
                        true
                    } ?: false
                    if (!booted) failedWifi += device.displayName
                    else try { withContext(Dispatchers.IO) { backend.enableInitialWifi(device, serial) } }
                    catch (e: Exception) {
                        if (e is CancellationException) throw e
                        failedWifi += device.displayName
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failedToStart += targets.filter { it.name !in running }.map { it.displayName }
            } finally { starting = starting - names }
            // A failed start matters more than Wi-Fi setup, and only one notice shows at a time.
            if (failedToStart.isNotEmpty()) notifyFailure(FailedOperation.Start(failedToStart.distinct()))
            else if (failedWifi.isNotEmpty()) notifyFailure(FailedOperation.WifiSetup(failedWifi))
        }
    }
    private fun forEachDevice(devices: List<Device>, block: (Device) -> Unit) {
        val failures = mutableListOf<String>()
        devices.distinctBy { it.name }.forEach { device ->
            try { block(device) }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                failures += device.displayName
            }
        }
        if (failures.isNotEmpty()) throw DevicesFailed(failures)
    }
    fun stop(device: Device) = stop(listOf(device))
    fun stop(devices: List<Device>) {
        val targets = devices.filter { it.name in running && it.name !in starting && it.name !in stopping }
        stopRunning(targets.associate { it.name to running.getValue(it.name) })
    }
    private fun stopRunning(serials: Map<String, String>) {
        if (busy || serials.isEmpty() || serials.keys.any { it in deviceOperations }) return
        val titles = devices.associate { it.name to it.displayName }
        stopping = stopping + serials.keys
        serials.keys.forEach(displayErrors::remove)
        action(onResult = { failure ->
            if (failure != null) stopping = stopping - serials.keys
        }, operations = serials.mapValues { EmulatorOperation.Stopping }, blocksWorkspace = false,
            failed = FailedOperation::Stop) { backend ->
            val failures = mutableListOf<String>()
            serials.forEach { (name, serial) ->
                try { backend.stop(serial) }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failures += titles[name] ?: name
                }
            }
            if (failures.isNotEmpty()) throw DevicesFailed(failures)
        }
    }
    fun restart(device: Device) = restart(listOf(device))
    fun restart(devices: List<Device>) {
        val targets = devices.filter { it.name in running && it.name !in starting }
        val serials = running.toMap()
        action(operations = targets.associate { it.name to EmulatorOperation.Restarting }, failed = FailedOperation::Restart) { backend ->
            forEachDevice(targets) { device -> serials[device.name]?.let(backend::restart) }
        }
    }
    fun stopAll() = stopRunning(running.filterKeys { it !in stopping })
    fun duplicate(device: Device) { if (unlocked()) duplicateUnlocked(device) }
    private fun duplicateUnlocked(device: Device) = action(
        operations = mapOf(device.name to EmulatorOperation.Duplicating), failed = FailedOperation::Duplicate) {
        require(device.name !in running && device.name !in starting) { "Stop ${device.displayName} before duplicating it." }
        it.duplicate(device)
    }
    fun rename(device: Device, name: String, onResult: (Throwable?) -> Unit = {}) = action(onResult,
        operations = mapOf(device.name to EmulatorOperation.Renaming)) {
        require(device.name !in running && device.name !in starting) { "Stop ${device.displayName} before renaming it." }; it.rename(device, name.trim())
    }
    fun delete(devices: List<Device>) = action(
        operations = devices.associate { it.name to EmulatorOperation.Deleting }, blocksWorkspace = false,
        failed = FailedOperation::Remove) { backend ->
        forEachDevice(devices) { device ->
            require(device.name !in starting) { "Wait for this emulator to finish starting before removing it." }
            running[device.name]?.let(backend::stop); backend.delete(device)
        }
    }
    fun reveal(device: Device) = reveal(listOf(device))
    fun reveal(devices: List<Device>) = action(failed = { FailedOperation.Reveal(it.ifEmpty { devices.map(Device::displayName) }) }) { backend ->
        forEachDevice(devices) { device ->
            val file = device.directory.takeIf(File::exists) ?: File(backend.avdRoot, "${device.name}.ini")
            require(file.exists()) { "The emulator's storage is unavailable." }
            revealInFileManager(file)
        }
    }
    fun saveSettings(sdk: String, avds: String): SettingsSaveResult = saveSettings(settings.copy(sdk = sdk, avds = avds))

    fun saveSettings(draft: AppSettings): SettingsSaveResult {
        if (downloads.activeCount > 0) return SettingsSaveResult.DownloadsActive
        if (busy) return SettingsSaveResult.WorkspaceBusy
        if (draft.sdk.isBlank() || draft.avds.isBlank()) return SettingsSaveResult.MissingFolders
        val sdkFolder = expandPath(draft.sdk); val avdFolder = expandPath(draft.avds)
        if (!sdkFolder.isDirectory || !sdkFolder.canWrite()) return SettingsSaveResult.InvalidSdkFolder
        val tools = Toolchain(draft)
        val required = listOfNotNull(
            "adb".takeIf { draft.adb.isNotBlank() },
            "emulator".takeIf { draft.emulator.isNotBlank() },
            "sdkmanager".takeIf { draft.sdkmanager.isNotBlank() },
            "avdmanager".takeIf { draft.avdmanager.isNotBlank() })
        val missing = tools.tools.filterKeys { it in required }.filterValues { !it.isFile || !it.canExecute() }.keys
        if (missing.isNotEmpty()) return SettingsSaveResult.InvalidExecutables(missing)
        if (!avdFolder.isDirectory || !avdFolder.canWrite()) return SettingsSaveResult.InvalidAvdFolder
        fun normalize(path: String) = if (path.isBlank()) "" else expandPath(path).path
        val updated = draft.copy(sdk = sdkFolder.path, avds = avdFolder.path,
            adb = normalize(draft.adb), emulator = normalize(draft.emulator),
            sdkmanager = normalize(draft.sdkmanager), avdmanager = normalize(draft.avdmanager))
        return runCatching {
            store.save(updated)
            if (settings != updated) {
                settings = updated; selected = emptySet(); images = emptyList()
                creationImagesRevision++
                creationImages = emptyList(); creationImagesState = OperationState.Idle
                downloads.tasks.clear()
                imageDeletions.clear()
                catalogRevision++
                catalogJob?.cancel(); catalogState = OperationState.Idle
            }
            refreshAsync(); SettingsSaveResult.Saved
        }.getOrElse { SettingsSaveResult.Failed(it) }
    }
    /**
     * Shows the cached SDK catalog with what is installed on disk now, and fetches a new one from Google's
     * repository once it is a day old, or when [force]d.
     */
    fun loadCatalog(force: Boolean = false) {
        if (catalogLoading) return
        val backend = service
        val revision = ++catalogRevision
        catalogState = OperationState.Running
        catalogJob = scope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { readCatalogCache(backend.settings) }
                val local = runCatching { withContext(Dispatchers.IO) { backend.installedImages() } }.getOrNull()
                val known = cached?.let { parseImages(it.text) } ?: images
                if (settings == backend.settings && revision == catalogRevision) images = local?.let { withInstalled(known, it) } ?: known
                if (force || cached == null || cached.age >= CatalogMaxAge) {
                    backend.toolchain.requireTools("sdkmanager")
                    val text = downloads.sdkLock.withLock { withContext(Dispatchers.IO) { backend.catalog() } }
                    withContext(Dispatchers.IO) { writeCatalogCache(backend.settings, text) }
                    if (settings == backend.settings && revision == catalogRevision) images = parseImages(text)
                }
                if (settings == backend.settings && revision == catalogRevision) catalogState = OperationState.Succeeded
            } catch (t: Exception) {
                if (t is CancellationException) throw t
                if (settings == backend.settings && revision == catalogRevision) catalogState = OperationState.Failed(t)
            } finally {
                if (revision == catalogRevision && catalogState.isRunning) catalogState = OperationState.Idle
            }
        }
    }
    /** Keeps the catalog no more than a day old while the app runs, so Platforms is ready when opened. */
    fun keepCatalogFresh() = scope.launch {
        while (isActive) {
            if (service.toolchain.hasTools("sdkmanager")) loadCatalog()
            delay(CatalogCheckInterval)
        }
    }
    private class CachedCatalog(val text: String, val age: kotlin.time.Duration)
    // The first line names the SDK the catalog was listed for: its installed flags and paths are specific to it.
    private fun readCatalogCache(settings: AppSettings): CachedCatalog? {
        val file = catalogCache?.takeIf { it.isFile } ?: return null
        val lines = runCatching { file.readText() }.getOrNull()?.lines() ?: return null
        if (lines.firstOrNull() != "sdk=${settings.sdk}") return null
        val age = (System.currentTimeMillis() - file.lastModified()).coerceAtLeast(0).milliseconds
        return CachedCatalog(lines.drop(1).joinToString("\n"), age)
    }
    private fun writeCatalogCache(settings: AppSettings, text: String) {
        val file = catalogCache ?: return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText("sdk=${settings.sdk}\n$text")
        }
    }
    suspend fun reviewStorage(spec: CreateSpec): LoadState<StoragePlan> = try {
        val current = settings
        LoadState.Ready(withContext(Dispatchers.IO) { storagePlanner(spec, current) })
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        LoadState.Failed(e)
    }

    suspend fun imageSize(image: SystemImage): LoadState<Long?> = try {
        val sdk = expandPath(settings.sdk)
        LoadState.Ready(withContext(Dispatchers.IO) { image.sizeOnDisk(sdk) })
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        LoadState.Failed(e)
    }

    fun purchaseLicense() = openWebsite(purchaseLicenseUrl)
    fun manageLicense() = openWebsite(licenseOrigin)
    private fun openWebsite(url: String) {
        scope.launch {
            try { withContext(Dispatchers.IO) { java.awt.Desktop.getDesktop().browse(URI(url)) } }
            catch (e: Exception) { if (e is CancellationException) throw e; notifyFailure(FailedOperation.OpenWebsite(url)) }
        }
    }
    fun create(spec: CreateSpec) {
        if (busy) return
        val resumeCreated = (creation as? CreationState.Failed)?.created == true && pendingCreation != null && creationSpec == spec
        creationSpec = spec
        cancellation = AtomicBoolean(); val token = cancellation; val backend = service
        pendingCreation = Device(spec.name, File(backend.avdRoot, "${spec.name}.avd"), buildMap {
            put("avd.ini.displayname", spec.displayName)
            put("hw.device.name", spec.profile.replace('-', '_'))
            put("image.sysdir.1", spec.image.path.replace(';', '/') + "/")
            spec.hardware?.let { profile ->
                put("avdbuddy.profile", profile.id)
                put("showDeviceFrame", "yes")
                put("hw.lcd.width", profile.display.width.toString())
                put("hw.lcd.height", profile.display.height.toString())
                put("hw.lcd.density", profile.display.density.toString())
            }
        }, 0)
        selectPreview(pendingCreation!!)
        showCreate = false
        creation = CreationState.SettingUp
        scope.launch {
            var startWhenReady: Device? = null
            var createdOnDisk = resumeCreated
            try {
                val created = if (resumeCreated) spec else downloads.sdkLock.withLock {
                    val currentSpec = spec.copy(image = creationImages.firstOrNull { it.path == spec.image.path }
                        ?: images.firstOrNull { it.path == spec.image.path } ?: spec.image)
                    withContext(Dispatchers.IO) {
                        val plan = storagePlanner(currentSpec, backend.settings)
                        check(plan.canCreate) { "Not enough available disk space to create this emulator. Free space and review the storage requirements, then retry." }
                        backend.create(currentSpec, token) { }
                    }
                }
                createdOnDisk = true
                creationSpec = created
                pendingCreation = pendingCreation?.copy(name = created.name, directory = File(backend.avdRoot, "${created.name}.avd"))
                pendingCreation?.let(::selectPreview)
                creation = CreationState.Finalizing
                refresh(); selected = setOf(created.name); previewName = created.name
                images = images.map { if (it.path == spec.image.path) it.copy(installed = true) else it }
                val device = devices.firstOrNull { it.name == created.name } ?: error("Your emulator was created but could not be loaded. Try again.")
                pendingCreation = null
                creation = CreationState.Created
                if (!token.get()) startWhenReady = device
            } catch (t: Exception) {
                if (token.get()) {
                    discardPendingCreation()
                    refreshAsync()
                } else {
                    if (t is CancellationException) throw t
                    creation = CreationState.Failed(t, created = createdOnDisk)
                }
            }
            finally { if (creating) creation = CreationState.Idle }
            startWhenReady?.let(::launch)
        }
    }
    fun cancelCreate() { if (creating) { creation = CreationState.Cancelling; cancellation.set(true) } }
    fun resetCreate(type: DeviceType = createType) {
        createType = type
        showCreate = true
        if (creationImagesLoading) return
        val backend = service
        val revision = ++creationImagesRevision
        creationImagesState = OperationState.Running
        scope.launch {
            try {
                val installed = withContext(Dispatchers.IO) { backend.installedImages() }
                if (settings == backend.settings && revision == creationImagesRevision) {
                    creationImages = installed
                    creationImagesState = OperationState.Succeeded
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (settings == backend.settings && revision == creationImagesRevision) {
                    creationImages = emptyList()
                    creationImagesState = OperationState.Failed(e)
                }
            } finally {
                if (revision == creationImagesRevision && creationImagesState.isRunning) creationImagesState = OperationState.Idle
            }
        }
    }

    fun retryCreation() { if (!busy) creationSpec?.let(::create) }
    fun discardPendingCreation() {
        pendingCreation?.let { pending ->
            selected = selected - pending.name
            if (previewName == pending.name) previewName = devices.firstOrNull()?.name
        }
        pendingCreation = null; creationSpec = null; creation = CreationState.Idle
    }
    internal val licenseReminder = LicenseReminder()
    fun windowFocusChanged(focused: Boolean) {
        if (!focused) connected.releaseKeyboard()
        if (licenseReminder.focusChanged(focused, licensed = license.licensed, canShow = !hasOpenDialog && !busy)) showDemoDialog = true
    }
    val updates = AppUpdates(scope, license)
    fun checkUpdates() {
        openSettings(SettingsSection.Updates)
        updates.check()
    }
}
