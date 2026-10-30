@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.avdbuddy

import com.avdbuddy.components.*
import com.avdbuddy.theme.verticalScroll

import com.avdbuddy.iconography.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import com.avdbuddy.theme.AvdBuddyTheme
import androidx.compose.ui.*
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.theme.sliderStyle
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun SdkLocationsPage(state:AppState) {
    val sdkField = rememberTextFieldState(state.settings.sdk)
    val sdk = sdkField.text.toString()
    val avdField = rememberTextFieldState(state.settings.avds)
    val avds = avdField.text.toString()
    val adbField = rememberTextFieldState(state.settings.adb)
    val emulatorField = rememberTextFieldState(state.settings.emulator)
    val sdkmanagerField = rememberTextFieldState(state.settings.sdkmanager)
    val avdmanagerField = rememberTextFieldState(state.settings.avdmanager)
    val draft = AppSettings(sdk, avds, adbField.text.toString(), emulatorField.text.toString(),
        sdkmanagerField.text.toString(), avdmanagerField.text.toString())
    var failure by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(draft) { saved = false; failure = null }
    val defaults = Toolchain(expandPath(sdk))
    SettingsPageScaffold("SDK Locations","SdkLocationsPage") {
        if (state.downloads.activeCount > 0) Label("Finish or cancel Android downloads before saving SDK locations.", color=AvdBuddyTheme.colors.textSecondary)
        LocationField("Android SDK folder", sdkField, "SdkPath",
            onFailure = { failure = it })
        Label("Choose each tool’s executable file. Leave a tool blank to use its SDK location.", color=AvdBuddyTheme.colors.textSecondary)
        listOf(Triple("adb", adbField, defaults.adb), Triple("emulator", emulatorField, defaults.emulator),
            Triple("sdkmanager", sdkmanagerField, defaults.sdkmanager), Triple("avdmanager", avdmanagerField, defaults.avdmanager))
            .forEach { (name, field, fallback) ->
                LocationField(name, field, "${name}Path", placeholder = fallback.path, selectFile = true,
                    onFailure = { failure = it })
            }
        LocationField("Emulator storage folder", avdField, "AvdPath", hint = "Changing this folder leaves existing emulators in place.",
            onFailure = { failure = it })
        failure?.let{Label(it,color=AvdBuddyTheme.colors.danger,modifier=Modifier.testTag("SettingsError"))}
        if (saved) Label("Settings saved.", color = AvdBuddyTheme.colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Save",primary=true,enabled=!state.busy && state.downloads.activeCount == 0,tag="SaveSettings") {
                val result = state.saveSettings(draft)
                failure = result.errorMessage
                saved = result == SettingsSaveResult.Saved
            }
            ActionButton("Discard changes",tag="ResetSettings") {
                sdkField.setTextAndPlaceCursorAtEnd(state.settings.sdk)
                avdField.setTextAndPlaceCursorAtEnd(state.settings.avds)
                adbField.setTextAndPlaceCursorAtEnd(state.settings.adb)
                emulatorField.setTextAndPlaceCursorAtEnd(state.settings.emulator)
                sdkmanagerField.setTextAndPlaceCursorAtEnd(state.settings.sdkmanager)
                avdmanagerField.setTextAndPlaceCursorAtEnd(state.settings.avdmanager)
                failure = null; saved = false
            }
        }
    }
}

@Composable private fun LocationField(label: String, field: androidx.compose.foundation.text.input.TextFieldState,
    tag: String, hint: String? = null, placeholder: String = "", selectFile: Boolean = false, onFailure: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val window = LocalAwtWindow.current
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Label(label)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            org.jetbrains.jewel.ui.component.TextField(state = field, modifier = Modifier.weight(1f).testTag(tag),
                placeholder = { Text(placeholder) })
            ActionButton("Browse…", tag = "Browse$tag", enabled = !picking) {
                scope.launch {
                    picking = true
                    try {
                        val path = expandPath(field.text.toString().ifBlank { placeholder })
                        val folder = generateSequence(path) { it.parentFile }.firstOrNull { it.isDirectory }
                        val settings = FileKitDialogSettings(title = if (selectFile) "Choose $label" else "Choose folder",
                            parent = window?.let { FileKitDialogParent.awt(it) })
                        val directory = folder?.let(::PlatformFile)
                        val selected = if (selectFile) FileKit.openFilePicker(directory = directory, dialogSettings = settings)
                            else FileKit.openDirectoryPicker(directory = directory, dialogSettings = settings)
                        selected?.let { field.setTextAndPlaceCursorAtEnd(it.file.absolutePath) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        onFailure("Could not open the picker. Try again or enter the path directly.")
                    } finally {
                        picking = false
                    }
                }
            }
        }
        hint?.let { Label(it, color = AvdBuddyTheme.colors.textSecondary) }
    }
}

@Composable fun CreateDeviceDialog(state: AppState) {
    val profiles = remember(state.createType) { HardwareProfiles.forType(state.createType) }
    val firstProfile = profiles.firstOrNull()
    if (firstProfile == null) {
        AppDialogPanel("Create emulator", "CreateDeviceDialog", Modifier.width(510.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Label("This emulator profile is unavailable. Restart $APP_NAME and try again.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionButton("Close", tag = "CloseCreate") { state.showCreate = false }
                }
            }
        }
        return
    }
    var profile by remember(profiles) { mutableStateOf(firstProfile) }
    val title = "Create ${when (state.createType) { DeviceType.WEAR, DeviceType.TV -> state.createType.label; else -> state.createType.label.lowercase() }} emulator"
    val images = remember(state.creationImages, state.createType, state.deletingImagePaths) {
        installedCreationImages(state.creationImages, type = state.createType).filter { it.path !in state.deletingImagePaths }
    }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    val image = images.firstOrNull { it.path == selectedPath } ?: images.firstOrNull()
    val nameField = rememberTextFieldState()
    val suggestedName = suggestedDeviceName(profile.name, state.visibleDevices.map(Device::displayName))
    val name = nameField.text.toString().trim().ifBlank { suggestedName }
    val nameIssue = creationNameError(name, state.visibleDevices.map(Device::displayName))
    val spec = image?.let { profile.createSpec(name, it, state.visibleDevices.map(Device::name)) }
    var review by remember { mutableStateOf<LoadState<StoragePlan>>(LoadState.Loading) }
    val plan = (review as? LoadState.Ready)?.value
    val planError = (review as? LoadState.Failed)?.cause?.let { it.message ?: "Could not check available storage." }.orEmpty()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(profile.id, image?.path, image?.installed, state.settings) {
        review = LoadState.Loading
        if (spec != null) review = state.reviewStorage(spec)
    }
    fun close() { state.showCreate = false }
    fun manageImages() {
        close()
        state.openSettings(SettingsSection.Images)
    }
    AppDialogPanel(title, "CreateDeviceDialog", Modifier.width(510.dp).heightIn(max = 650.dp)) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(title)
                Spacer(Modifier.weight(1f))
                IconButton(Icons.XMark, "Close create emulator", "CloseCreate", ghost = true) { close() }
            }
            Rule()
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Label("Name", color = AvdBuddyTheme.colors.textSecondary)
                    org.jetbrains.jewel.ui.component.TextField(
                        state = nameField,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("DeviceName"),
                        placeholder = { Text(suggestedName) },
                    )
                    if (nameIssue != null) Label(nameIssue.message, color = AvdBuddyTheme.colors.danger)
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Label("Device", color = AvdBuddyTheme.colors.textSecondary)
                    Select(profile, profiles, { profile = it }, { it.name }, "DeviceProfile", Modifier.fillMaxWidth())
                }
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Label(when (state.createType) {
                        DeviceType.WEAR -> "Wear OS version"
                        DeviceType.TV -> "Android TV version"
                        else -> "Android version"
                    },
                        color = AvdBuddyTheme.colors.textSecondary)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (image == null) {
                            org.jetbrains.jewel.ui.component.OutlinedButton(onClick = ::manageImages,
                                modifier = Modifier.weight(1f).testTag("SeeAvailableImages")) {
                                Text("See available images")
                            }
                        } else {
                            Select(image, images, { selectedPath = it.path },
                                { "${it.platformTitle} · API ${creationApiLabel(it)}" },
                                "AndroidVersion", Modifier.weight(1f))
                            // With nothing installed, "See available images" already opens the same page.
                            OutlinedIconButton(Icons.Cog6Tooth, "Manage images", "ManageAndroidImages", onClick = ::manageImages)
                        }
                    }
                    when {
                        state.creationImagesLoading && images.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            org.jetbrains.jewel.ui.component.CircularProgressIndicator(Modifier.size(16.dp))
                            Label("Finding installed Android versions…", color = AvdBuddyTheme.colors.textSecondary)
                        }
                        state.creationImagesError.isNotBlank() -> {
                            Label(state.creationImagesError, color = AvdBuddyTheme.colors.danger)
                            ActionButton("Try again", tag = "RetryInstalledImages") { state.resetCreate() }
                        }
                    }
                }
                if (image != null) {
                    when {
                        planError.isNotBlank() -> {
                            Label(planError, color = AvdBuddyTheme.colors.danger)
                        }
                        plan?.canCreate == false -> {
                            Label("Not enough storage. Free space or change the location in Settings.", color = AvdBuddyTheme.colors.danger)
                        }
                    }
                }
            }
            Rule()
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                ActionButton("Cancel", tag = "CancelCreate") { close() }
                ActionButton("Create", primary = true,
                    enabled = !state.busy && !state.creationImagesLoading && state.creationImagesError.isBlank() && spec != null && nameIssue == null && plan?.canCreate == true && planError.isBlank(), tag = "ConfirmCreate") {
                    spec?.let(state::create)
                }
            }
        }
    }
}

@Composable fun RenameDeviceDialog(state:AppState,device:Device) {
    val nameField = rememberTextFieldState(device.name)
    val name = nameField.text.toString().trim()
    val issue = deviceNameError(name, state.devices.filter { it.name != device.name }.map { it.name })
    var failure by remember { mutableStateOf<String?>(null) }
    val running = device.name in state.running || device.name in state.starting
    DialogScaffold("Rename emulator","RenameDeviceDialog",height=350,footer={
        Spacer(Modifier.weight(1f));ActionButton("Cancel",enabled=!state.busy){state.renameDevice=null}
        ActionButton("Rename", primary = true,
            enabled = !state.busy && device.name !in state.deviceOperations && issue == null && name != device.name && !running,
            tag = "ConfirmRename") {
            failure=null
            state.rename(device,nameField.text.toString()) { error -> if(error==null)state.renameDevice=null else failure=error.message ?: "The operation could not be completed." }
        }
    }) {
        Label("Give ${device.displayName} a new name.",color=AvdBuddyTheme.colors.textSecondary)
        Field(nameField,"RenameName",Modifier.fillMaxWidth())
        val hint = when {
            state.busy -> "Renaming emulator… This dialog will close when finished."
            device.name in state.deviceOperations -> state.deviceOperations.getValue(device.name).label
            running -> "Stop this emulator before renaming it."
            issue != null -> issue.message
            name == device.name -> "Enter a different name to rename this emulator."
            else -> "Use letters, numbers, dots, underscores or hyphens."
        }
        Label(hint,color=if(running||issue!=null) AvdBuddyTheme.colors.danger else AvdBuddyTheme.colors.textSecondary)
        failure?.let { Label(it,color=AvdBuddyTheme.colors.danger) }
    }
}
@Composable internal fun DemoDialog(state: AppState) {
    DialogScaffold("You are using a demo version of $APP_NAME", "DemoDialog",
        width = 460, height = 300, scrollableContent = false,
        footer = {
            Spacer(Modifier.weight(1f))
            org.jetbrains.jewel.ui.component.OutlinedButton(onClick = {
                state.showDemoDialog = false
                state.openSettings(SettingsSection.License)
            }, modifier = Modifier.testTag("RestoreDemoLicense")) { Text("Restore License") }
            ActionButton("Buy License", primary = true, tag = "BuyDemoLicense") {
                state.showDemoDialog = false
                state.purchaseLicense()
            }
        }) {
        Label("Please consider buying a license to unlock all features.")
        Label("30-day money back guarantee. No questions asked.")
    }
}

@Composable fun LicensePage(license: AppLicense, onManage: () -> Unit, onPurchase: () -> Unit) {
    var confirmingDeactivate by remember { mutableStateOf(false) }
    LaunchedEffect(license.receipt) { confirmingDeactivate = false }
    SettingsPageScaffold("Your License", "LicensePage") {
        if (license.licensed) {
            Card(contentPadding = PaddingValues(16.dp)) {
                StatusHeader(Icons.Check, AvdBuddyTheme.colors.success, "$APP_NAME is licensed on this computer", licenseUpdatesText(license), Modifier.testTag("LicenseStatus"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton("Manage license", onClick = onManage)
                Spacer(Modifier.weight(1f))
                GhostButton("Unlink this computer", tag = "DeactivateLicense") { license.clearOutcome(); confirmingDeactivate = true }
            }
        } else {
            Card(contentPadding = PaddingValues(16.dp)) {
                StatusHeader(Icons.Key, accentColor(), "Activate $APP_NAME", "Paste the license key from your purchase email to activate your license.",
                    Modifier.testTag("LicenseStatus"))
                Spacer(Modifier.height(16.dp))
                LicenseKeyForm(license, onManage)
            }
            Card(contentPadding = PaddingValues(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("Don't have a license yet?", Modifier.weight(1f))
                    ActionButton("Buy a license", primary = true, tag = "BuyLicense", onClick = onPurchase)
                }
            }
        }
    }
    fun closeDeactivate() { if (!license.working) { confirmingDeactivate = false; license.clearOutcome() } }
    AppDialog(confirmingDeactivate.takeIf { it }, onClose = ::closeDeactivate) {
        AppDialogPanel("Unlink this computer?", "DeactivateLicenseDialog", Modifier.width(440.dp)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Unlink this computer?", style = AvdBuddyTheme.typography.heading)
                Label("You can use your license on another computer after unlinking. To use it here again, just enter your key.")
                LicenseMessage(license)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically) {
                    ActionButton("Cancel", enabled = !license.working, tag = "CancelDeactivateLicense") { closeDeactivate() }
                    ActionButton("Unlink", dangerous = true, enabled = !license.working, tag = "ConfirmDeactivateLicense") { license.deactivate() }
                }
            }
        }
    }
}

private fun licenseUpdatesText(license: AppLicense): String {
    val until = license.updatesUntil ?: return ""
    val date = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
        .withZone(java.time.ZoneOffset.UTC).format(until)
    return if (until.isAfter(java.time.Instant.now())) "Includes updates through $date."
    else "Updates ended on $date. You can keep using versions released before then."
}

@Composable private fun accentColor(): Color = org.jetbrains.jewel.foundation.theme.JewelTheme.sliderStyle.colors.trackFilled

/** Tinted icon badge with a title and secondary line, used at the top of settings cards. */
@Composable private fun StatusHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, subtitle: String,
                                     modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = .12f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = AvdBuddyTheme.typography.heading)
            if (subtitle.isNotEmpty()) Label(subtitle, color = AvdBuddyTheme.colors.textSecondary)
        }
    }
}

@Composable private fun LicenseKeyForm(license: AppLicense, onManage: () -> Unit) {
    val key = rememberTextFieldState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        org.jetbrains.jewel.ui.component.TextField(
            state = key,
            modifier = Modifier.fillMaxWidth().testTag("LicenseKey")
                .semantics { contentDescription = "License key" },
            placeholder = { Text("LIC-XXXXXXXX-XXXXXXXX-XXXXXXXX-XXXXXXXX-XXXXXXXX") },
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            // The portal is where keys are recovered and other computers are unlinked.
            ActionButton("Manage license", onClick = onManage)
            // Status sits beside the button that caused it, so the form needs no empty line reserved for it.
            LicenseMessage(license, Modifier.weight(1f), androidx.compose.ui.text.style.TextAlign.End)
            ActionButton("Activate", primary = true, enabled = !license.working) { license.activate(key.text.toString()) }
        }
    }
}

/** One fixed-height line for progress and errors, so they don't shift the buttons. Success shows as the page changing state. */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun LicenseMessage(license: AppLicense, modifier: Modifier = Modifier.fillMaxWidth(),
                                       textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start) {
    val message = license.message.takeIf { license.working || license.messageIsError }.orEmpty()
    val messageStyle = AvdBuddyTheme.typography.body
    val messageHeight = with(androidx.compose.ui.platform.LocalDensity.current) { messageStyle.lineHeight.toDp() }
    org.jetbrains.jewel.ui.component.Tooltip(tooltip = { Text(message) }, modifier = modifier) {
        Text(message, modifier = Modifier.fillMaxWidth().height(messageHeight).testTag("LicenseMessage"), textAlign = textAlign,
            color = if (license.messageIsError) AvdBuddyTheme.colors.danger else AvdBuddyTheme.colors.textSecondary,
            style = messageStyle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable fun UpdatesPage(state: AppState) {
    val updates = state.updates
    LaunchedEffect(state.license.receipt) { updates.check() }
    SettingsPageScaffold("Updates", "UpdatesPage") {
        Card(contentPadding = PaddingValues(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusHeader(Icons.ArrowDownTray, accentColor(), "$APP_NAME $APP_VERSION", updates.unavailableReason ?: updates.message,
                    Modifier.weight(1f).testTag("UpdateStatus"))
                when {
                    updates.downloaded != null && updates.canInstall -> ActionButton(if (updates.restoring) "Restart and restore" else "Restart and install", primary = true, enabled = !updates.working && !state.busy) { updates.restart(state.busy) }
                    updates.available != null && updates.canInstall -> ActionButton(if (updates.restoring) "Restore" else "Download update", primary = true, enabled = !updates.working) { updates.download() }
                    else -> ActionButton("Check for updates", enabled = !updates.working && updates.unavailableReason == null && updates.downloaded == null, tag = "CheckForUpdates") { state.checkUpdates() }
                }
            }
            if (updates.downloaded != null && state.busy) {
                Spacer(Modifier.height(12.dp))
                Label("Finish the current emulator operation before restarting.", color=AvdBuddyTheme.colors.textSecondary)
            }
        }
        Card(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(updates.automatic,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = updates::changeAutomatic),
                verticalAlignment = Alignment.CenterVertically) {
                Label("Automatically check for updates", Modifier.weight(1f))
                org.jetbrains.jewel.ui.component.Checkbox(checked=updates.automatic, onCheckedChange=updates::changeAutomatic)
            }
        }
    }
}
