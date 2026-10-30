package com.avdbuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.ActionButton
import com.avdbuddy.components.Card
import com.avdbuddy.components.CardDivider
import com.avdbuddy.components.GhostButton
import com.avdbuddy.components.Label
import com.avdbuddy.components.Icon
import com.avdbuddy.iconography.Icons
import com.avdbuddy.iconography.AdjustmentsHorizontal
import com.avdbuddy.iconography.AppWindow
import com.avdbuddy.iconography.ArrowRightLeft
import com.avdbuddy.iconography.Sparkles
import com.avdbuddy.iconography.Contrast
import com.avdbuddy.iconography.PersonStanding
import com.avdbuddy.iconography.ALargeSmall
import com.avdbuddy.theme.AvdBuddyTheme
import com.avdbuddy.theme.verticalScroll
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.composeunstyled.UnstyledSwitch
import com.composeunstyled.Track
import com.composeunstyled.Thumb
import com.avdbuddy.theme.appFocusRing
import com.avdbuddy.components.Slider
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.sliderStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private val controlsPanelPadding = 16.dp
private val settingsCardPadding = 12.dp
internal val deviceControlsPanelWidth = 280.dp + controlsPanelPadding * 2 + settingsCardPadding * 2

@Composable
internal fun rememberDeviceControls(state: AppState, visible: Boolean): DeviceControlsState? {
    val device = state.previewDevice
    val connected = state.connected.selected
    // A wireless device that is not sharing its screen is not connected as far as the user is concerned.
    val serial = connected?.takeIf { it.available && (!it.wireless || state.connected.sharing(it)) }?.serial
        ?: device?.let { state.running[it.name] } ?: return null
    return key(serial, state.settings) {
        val scope = rememberCoroutineScope()
        val controls = remember {
            DeviceControlsState(state.deviceSettingsService, serial, scope,
                canEdit = { state.license.licensed }, onLocked = { state.showDemoDialog = true })
        }
        LaunchedEffect(controls, visible) {
            controls.observe(visible)
        }
        controls
    }
}

@Composable
private fun SettingsPlaceholder(text: String) {
    Box(Modifier.fillMaxSize().padding(controlsPanelPadding).testTag("DeviceControlsPanel"),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AdjustmentsHorizontal, tint = AvdBuddyTheme.colors.textSecondary, modifier = Modifier.size(32.dp))
            Text(text, modifier = Modifier.fillMaxWidth(), color = AvdBuddyTheme.colors.textSecondary,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun DeviceControlsPanel(state: AppState, controls: DeviceControlsState?) {
    val device = state.previewDevice
    val connected = state.connected.selected
    val serial = connected?.takeIf { it.available }?.serial ?: device?.let { state.running[it.name] }
    if (connected != null && (!connected.available || (connected.wireless && !state.connected.sharing(connected)))) {
        // A wireless device that is not sharing its screen is not connected as far as the user is concerned.
        SettingsPlaceholder("Settings for this device will appear here, once it is connected")
        return
    }
    if (state.selected.size <= 1 && device != null && device.name !in state.stopping && serial == null) {
        SettingsPlaceholder("Settings for this emulator will appear here, once it is running")
        return
    }
    if (connected == null && device == null && state.selected.size <= 1) {
        Box(Modifier.fillMaxSize().padding(controlsPanelPadding).testTag("DeviceControlsPanel"), contentAlignment = Alignment.Center) {
            Text("No Selection", color = AvdBuddyTheme.colors.textSecondary)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(controlsPanelPadding).testTag("DeviceControlsPanel"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            connected != null -> {
                if (controls?.booted == false) Label("Waiting for your device to be ready…")
                else if (controls != null) RunningDeviceControls(controls, connected.type, state.license.licensed) { state.showDemoDialog = true }
            }
            state.selected.size > 1 -> Label("Select one emulator to change its settings.")
            device == null -> Unit
            else -> {
                when {
                    device.name in state.stopping -> Label("Wait for the emulator to shut down.")
                    else -> {
                        when {
                            controls?.booted == false -> Label("The emulator is booting. These settings will become available once it goes live")
                            controls != null -> RunningDeviceControls(controls, device.type, state.license.licensed) {
                                state.showDemoDialog = true
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningDeviceControls(controls: DeviceControlsState, type: DeviceType, licensed: Boolean,
                                  onLockedClick: () -> Unit) {
    val settings = controls.settings
    Card(contentPadding = PaddingValues(horizontal = settingsCardPadding)) {
        // Wear OS and Android TV are always dark, so they have no theme to switch.
        if (type != DeviceType.WEAR && type != DeviceType.TV) {
            DeviceToggle("Dark theme", settings?.darkMode ?: false, "DeviceDarkMode", controls::setDarkMode,
                enabled = settings != null, icon = Icons.Contrast)
            CardDivider()
        }
        DeviceScaleSlider("Font Size", settings?.fontScale ?: 1f, textSizeOptions,
            label = { if (settings == null) "—" else "${(it * 100).roundToInt()}%" }, tag = "DeviceTextSize",
            onChange = controls::setFontScale, enabled = settings != null, locked = !licensed,
            onLockedClick = onLockedClick, icon = Icons.ALargeSmall)
        CardDivider()
        DeviceToggle("TalkBack", settings?.talkBackEnabled ?: false, "DeviceTalkBack", controls::setTalkBack,
            enabled = settings != null && (!licensed || settings.talkBackInstalled), icon = Icons.PersonStanding)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = settingsCardPadding),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Label("Animations", color = AvdBuddyTheme.colors.textSecondary)
            GhostButton("Reset", tag = "ResetAnimations", enabled = settings != null) {
                controls.resetAnimations()
            }
        }
        Card(contentPadding = PaddingValues(horizontal = settingsCardPadding)) {
            AnimationScaleSetting.entries.forEachIndexed { index, setting ->
                if (index > 0) CardDivider()
                val (title, icon) = when (setting) {
                    AnimationScaleSetting.WINDOW -> "Window" to Icons.AppWindow
                    AnimationScaleSetting.TRANSITION -> "Transition" to Icons.ArrowRightLeft
                    AnimationScaleSetting.ANIMATOR -> "Animator" to Icons.Sparkles
                }
                DeviceScaleSlider(title, settings?.animationScales?.getValue(setting) ?: 1f, animationScaleOptions,
                    label = { if (settings == null) "—" else animationScaleLabel(it) }, tag = "DeviceAnimation-${setting.key}",
                    onChange = { controls.setAnimationScale(setting, it) }, enabled = settings != null,
                    locked = !licensed, onLockedClick = onLockedClick, description = setting.label, icon = icon)
            }
        }
    }
    controls.error?.let { message ->
        Label(message, modifier = Modifier.testTag("DeviceSettingsError"), color = AvdBuddyTheme.colors.danger)
        ActionButton("Retry", tag = "RetryDeviceSettings") {
            controls.retry()
        }
    }
}

@Composable
private fun DeviceToggle(title: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit, enabled: Boolean = true,
                         icon: ImageVector) {
    val interaction = remember { MutableInteractionSource() }
    UnstyledSwitch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().testTag(tag)
            .appFocusRing(interaction, RoundedCornerShape(4.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            SettingLabel(title, icon)
            Track(Modifier.size(36.dp, 22.dp)
                .background(if (checked) JewelTheme.sliderStyle.colors.trackFilled else AvdBuddyTheme.colors.shelfBorder, CircleShape)) {
                Thumb(Modifier.padding(3.dp).size(16.dp).background(AvdBuddyTheme.colors.panelBackground, CircleShape),
                    animationSpec = tween(150))
            }
        }
    }
}

@Composable
private fun SettingLabel(title: String, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, tint = AvdBuddyTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
        Label(title, maxLines = 1)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun DeviceScaleSlider(title: String, value: Float, options: List<Float>, label: (Float) -> String,
                              tag: String, onChange: (Float) -> Unit, enabled: Boolean = true,
                              locked: Boolean = false, onLockedClick: () -> Unit = {},
                              description: String = title, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Tooltip(tooltip = { Label(description) }) {
                SettingLabel(title, icon)
            }
        }
        Box(Modifier.weight(1f)) {
            Tooltip(tooltip = { Label(label(value)) }) {
                Slider(
                    enabled = enabled && !locked,
                    value = options.indices.minBy { abs(options[it] - value) }.toFloat(),
                    onValueChange = {
                        val next = options[it.roundToInt().coerceIn(options.indices)]
                        if (next != value) onChange(next)
                    },
                    valueRange = 0f..options.lastIndex.toFloat(),
                    steps = options.size - 2,
                    modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
                        contentDescription = description
                        stateDescription = label(value)
                    },
                )
            }
            if (enabled && locked) {
                Box(Modifier.matchParentSize().clickable(role = Role.Button, onClick = onLockedClick)
                    .semantics { contentDescription = "Unlock $description" })
            }
        }
    }
}

internal sealed interface DeviceControlsSnapshot {
    data object WaitingForBoot : DeviceControlsSnapshot
    data object Loading : DeviceControlsSnapshot
    data class Ready(val settings: DeviceSettings) : DeviceControlsSnapshot
    data class ReadFailed(val cause: Throwable, val settings: DeviceSettings?) : DeviceControlsSnapshot
    data class WriteFailed(val cause: Throwable, val settings: DeviceSettings?) : DeviceControlsSnapshot
}

internal class DeviceControlsState(
    private val service: DeviceSettingsService,
    private val serial: String,
    private val scope: CoroutineScope,
    private val canEdit: () -> Boolean = { true },
    private val onLocked: () -> Unit = {},
) {
    var snapshot by mutableStateOf<DeviceControlsSnapshot>(DeviceControlsSnapshot.WaitingForBoot); private set
    val booted get() = snapshot != DeviceControlsSnapshot.WaitingForBoot
    val settings: DeviceSettings? get() = when (val current = snapshot) {
        is DeviceControlsSnapshot.Ready -> current.settings
        is DeviceControlsSnapshot.ReadFailed -> current.settings
        is DeviceControlsSnapshot.WriteFailed -> current.settings
        else -> null
    }
    private val commands = Mutex()
    private var pendingChanges = 0
    private var revision = 0L
    private val latestChanges = mutableMapOf<String, Long>()

    fun retry() { scope.launch { refresh() } }

    suspend fun observe(visible: Boolean) {
        refresh()
        while (visible && currentCoroutineContext().isActive) {
            delay(3000)
            if (!booted || (snapshot !is DeviceControlsSnapshot.ReadFailed && snapshot !is DeviceControlsSnapshot.WriteFailed)) refresh()
        }
    }

    suspend fun refresh(clearError: Boolean = true) {
        if (pendingChanges > 0) return
        commands.withLock {
            if (pendingChanges > 0) return@withLock
            if (!booted) {
                val ready = try {
                    runInterruptible(Dispatchers.IO) { service.isBooted(serial) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                if (!ready) return@withLock
                snapshot = DeviceControlsSnapshot.Loading
            }
            val requestedRevision = revision
            try {
                val actual = runInterruptible(Dispatchers.IO) { service.read(serial) }
                if (requestedRevision == revision) {
                    snapshot = when (val current = snapshot) {
                        is DeviceControlsSnapshot.ReadFailed -> if (clearError) DeviceControlsSnapshot.Ready(actual) else current.copy(settings = actual)
                        is DeviceControlsSnapshot.WriteFailed -> if (clearError) DeviceControlsSnapshot.Ready(actual) else current.copy(settings = actual)
                        else -> DeviceControlsSnapshot.Ready(actual)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestedRevision == revision) snapshot = DeviceControlsSnapshot.ReadFailed(failure, settings)
            }
        }
    }

    fun setDarkMode(enabled: Boolean) = change(
        key = "darkMode",
        update = { it.copy(darkMode = enabled) },
        write = { service.setDarkMode(serial, enabled) },
    )

    fun setAnimationScale(setting: AnimationScaleSetting, value: Float) = change(
        key = setting.key,
        update = { it.copy(animationScales = it.animationScales + (setting to value)) },
        write = { service.setAnimationScale(serial, setting, value) },
    )

    fun setFontScale(value: Float) = change(
        key = "fontScale",
        update = { it.copy(fontScale = value) },
        write = { service.setFontScale(serial, value) },
    )

    fun setTalkBack(enabled: Boolean) = change(
        key = "talkBack",
        update = { it.copy(talkBackEnabled = enabled) },
        write = { service.setTalkBack(serial, enabled) },
    )

    fun resetAnimations() {
        if (!canEdit()) { onLocked(); return }
        AnimationScaleSetting.entries.forEach { setAnimationScale(it, 1f) }
    }

    private fun change(key: String, update: (DeviceSettings) -> DeviceSettings, write: () -> Unit) {
        if (!canEdit()) { onLocked(); return }
        val current = settings ?: return
        val next = update(current)
        if (next == current) return
        snapshot = DeviceControlsSnapshot.Ready(next)
        revision++
        val changeRevision = revision
        latestChanges[key] = changeRevision
        pendingChanges++
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                commands.withLock {
                    if (latestChanges[key] == changeRevision) {
                        runInterruptible(Dispatchers.IO) { write() }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (latestChanges[key] == changeRevision) {
                    snapshot = DeviceControlsSnapshot.WriteFailed(failure, settings)
                }
            } finally {
                if (latestChanges[key] == changeRevision) latestChanges.remove(key)
                pendingChanges--
            }
            if (pendingChanges == 0) refresh(clearError = false)
        }
    }
}
