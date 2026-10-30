package com.avdbuddy

import com.avdbuddy.components.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import com.avdbuddy.theme.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.HorizontalProgressBar
import org.jetbrains.jewel.ui.component.IndeterminateHorizontalProgressBar
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import com.avdbuddy.iconography.*
import com.avdbuddy.theme.AvdBuddyTheme
import java.io.File

/** Platforms emulators can be created for, in the order the Platforms page lists them. */
private val platforms = listOf(DeviceType.PHONE, DeviceType.WEAR, DeviceType.TV)

private val SystemImage.platform get() = when {
    tag.contains("wear") -> DeviceType.WEAR
    tag.endsWith("-tv") || tag.startsWith("google-tv") -> DeviceType.TV
    else -> DeviceType.PHONE
}

private val DeviceType.platformName get() = when (this) {
    DeviceType.WEAR -> "Watches"
    DeviceType.TV -> "TVs"
    else -> "Phones, Tablets and Foldables"
}

@Composable
fun AndroidImagesPage(state: AppState) {
    // Newest first per platform: the first release is the platform itself, the rest are older versions.
    val catalogs = platforms.associateWith { creationImages(it, state.images) }
    // Downloads started from Add another version show here straight away; failed ones stay so they can be retried.
    val older = catalogs.values.flatMap { releases ->
        releases.drop(1).filter { image ->
            val task = state.downloads.tasks[image.path]
            image.installed || task?.active == true || task?.state is AndroidDownloadState.Failed
        }
    }
    var infoRequest by remember { mutableStateOf<SystemImage?>(null) }
    var addingVersion by remember { mutableStateOf(false) }
    SettingsPageScaffold("Platforms", "AndroidImagesPage") {
        val empty = catalogs.values.all { it.isEmpty() }
        if (state.catalogError.isNotBlank()) {
            Label(state.catalogError, color = JewelTheme.globalColors.text.error)
            ActionButton("Try again", enabled = !state.catalogLoading) { state.loadCatalog(force = true) }
        }
        if (empty && !state.catalogLoading && state.catalogError.isBlank()) {
            Label("No compatible Android versions are available for this computer.")
        }
        // Installed platforms come from disk immediately; the rest wait for the SDK catalog.
        val shown = platforms.filter { catalogs.getValue(it).isNotEmpty() || state.catalogLoading }
        // One child for the page's spacing: the gap above Older Platforms animates with the section itself.
        Column {
            if (shown.isNotEmpty()) Card(contentPadding = PaddingValues(0.dp)) {
                shown.forEachIndexed { index, type ->
                    if (index > 0) CardDivider()
                    val image = catalogs.getValue(type).firstOrNull()
                    if (image == null) PlatformLoadingRow(type)
                    else key(image.path) { AndroidImageRow(state, image, title = type.platformName, onInfo = { infoRequest = image }) }
                }
            }
            AnimatedVisibility(older.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = if (shown.isNotEmpty()) 18.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    org.jetbrains.jewel.ui.component.Text("Older Platforms", style = com.avdbuddy.theme.AvdBuddyTheme.typography.heading,
                        modifier = Modifier.testTag("AndroidImagesOlder"))
                    ImageCard(state, older) { infoRequest = it }
                }
            }
        }
        // Always offered: the dialog shows its own progress while the SDK catalog loads.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton({ addingVersion = true }, Modifier.testTag("AddPlatformVersion")) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Plus, modifier = Modifier.size(14.dp))
                    Label("Add another version…")
                }
            }
        }
    }
    AppDialog(addingVersion.takeIf { it }, onClose = { addingVersion = false }) {
        AddPlatformVersionDialog(state, catalogs) { addingVersion = false }
    }
    AppDialog(infoRequest, onClose = { infoRequest = null }) { image ->
        val title = image.platformTitle
        val closeFocus = remember { FocusRequester() }
        val sdkRoot = expandPath(state.settings.sdk)
        val users = state.devices.filter { it.usesImage(image, sdkRoot) }
        var sizeState by remember(image.path, sdkRoot) { mutableStateOf<LoadState<Long?>>(LoadState.Loading) }
        val size = when (val current = sizeState) {
            LoadState.Loading -> "Calculating…"
            is LoadState.Ready -> current.value?.let(::formatSize) ?: "Unavailable"
            is LoadState.Failed -> "Unavailable"
        }
        LaunchedEffect(image.path, sdkRoot) {
            sizeState = state.imageSize(image)
        }
        AppDialogPanel(title, "AndroidVersionInfoDialog", Modifier.width(480.dp)) {
            LaunchedEffect(Unit) { closeFocus.requestFocus() }
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = AvdBuddyTheme.typography.heading)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("File: ${File(image.directory(sdkRoot), "system.img").path}")
                    Label("API level: ${creationApiLabel(image)}")
                    Label("Size on disk: $size")
                }
                if (users.isNotEmpty()) Label(
                    "This platform is currently in use. Delete any emulators using it before you can remove it.",
                    color = JewelTheme.globalColors.text.info,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton({ infoRequest = null }, Modifier.focusRequester(closeFocus).testTag("CloseAndroidVersionInfo")) { Label("Close") }
                    ActionButton("Delete", dangerous = true, enabled = users.isEmpty() && !state.busy, tag = "DeleteAndroidVersion") {
                        state.deleteImage(image)
                        infoRequest = null
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCard(state: AppState, images: List<SystemImage>, onInfo: (SystemImage) -> Unit) {
    // Rows pad themselves so dividers run edge to edge.
    Card(contentPadding = PaddingValues(0.dp)) {
        AnimatedCardRows(images, key = { it.path }) { image -> AndroidImageRow(state, image, onInfo = { onInfo(image) }) }
    }
}

/** Stands in for a platform with nothing installed until the SDK catalog reports its latest release. */
@Composable
private fun PlatformLoadingRow(type: DeviceType) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).testTag("PlatformLoading-${type.name}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            DeviceGlyph(type, running = false, size = 20.dp)
        }
        Label(type.platformName, modifier = Modifier.weight(1f))
        Box(Modifier.size(34.dp).semantics {
            contentDescription = "Checking for the latest ${type.platformName} version"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AndroidImageRow(state: AppState, image: SystemImage, title: String = image.platformTitle, onInfo: () -> Unit) {
    val task = state.downloads.tasks[image.path]
    val active = task?.active == true
    val progress = task?.progress?.collectAsState()?.value
    val deleting = image.path in state.deletingImagePaths
    val subtitle = when {
        deleting -> "Deleting…"
        active -> task.state.progressMessage(progress)
        else -> "API ${creationApiLabel(image)}"
    }
    val inUse = state.devices.any { it.usesImage(image, expandPath(state.settings.sdk)) }
    Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                DeviceGlyph(image.platform, running = false, size = 20.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(title)
                Label(subtitle, color = JewelTheme.globalColors.text.info, maxLines = 1)
            }
            when {
                active -> IconButton(Icons.XMark, if (task.cancelling) "Cancelling download…" else "Cancel ${image.platformTitle} download", enabled = !task.cancelling, tag = "CancelDownload-${image.api}", ghost = true) { state.downloads.cancel(image.path) }
                deleting -> Box(Modifier.size(34.dp).testTag("DeletingImage-${image.api}").semantics {
                    contentDescription = "Deleting ${image.platformTitle}…"
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                }
                image.installed -> IconButton(
                    Icons.InformationCircle,
                    "Show ${image.platformTitle} details",
                    tag = "ImageInfo-${image.api}",
                    ghost = true,
                    onClick = onInfo,
                )
                else -> IconButton(Icons.ArrowDownTray, "Download ${image.platformTitle}", enabled = !state.busy, tag = "Download-${image.api}", ghost = true) { state.downloadImage(image) }
            }
        }
        if (active) {
            val percent = progress?.percent
            val modifier = Modifier.fillMaxWidth().semantics {
                progressBarRangeInfo = percent?.let { ProgressBarRangeInfo(it / 100f, 0f..1f) }
                    ?: ProgressBarRangeInfo.Indeterminate
            }
            if (percent == null) IndeterminateHorizontalProgressBar(modifier)
            else HorizontalProgressBar(percent / 100f, modifier)
        }
        if (!inUse) state.imageDeleteErrors[image.path]?.let { Label(it, color = JewelTheme.globalColors.text.error) }
        if (!image.installed && task?.state is AndroidDownloadState.Failed) {
            Label(task.state.errorMessage.orEmpty(), color = JewelTheme.globalColors.text.error)
            Label("Try downloading this version again.", color = JewelTheme.globalColors.text.info)
        }
    }
}

/** Downloads releases older than each platform's latest; installed ones then list under Older Platforms. */
@Composable
private fun AddPlatformVersionDialog(state: AppState, catalogs: Map<DeviceType, List<SystemImage>>, onClose: () -> Unit) {
    val choices = platforms.filter { catalogs[it].orEmpty().size > 1 }
    var platform by remember { mutableStateOf<DeviceType?>(null) }
    val selectedPlatform = platform?.takeIf { it in choices } ?: choices.firstOrNull()
    val versions = catalogs[selectedPlatform].orEmpty().drop(1).filter { !it.installed || state.downloads.tasks[it.path]?.active == true }
    // Fixed height so switching platforms doesn't resize the dialog.
    AppDialogPanel("Add another version", "AddPlatformVersionDialog", Modifier.width(480.dp).height(560.dp)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("Add another version")
                Spacer(Modifier.weight(1f))
                IconButton(Icons.XMark, "Close add another version", "CloseAddPlatformVersion", ghost = true, onClick = onClose)
            }
            Rule()
            if (choices.size > 1) Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Label("Platform", color = AvdBuddyTheme.colors.textSecondary)
                Select(selectedPlatform!!, choices, { platform = it }, { it.platformName },
                    "AddVersionPlatform", Modifier.fillMaxWidth())
            }
            // Only the versions scroll; the platform picker stays put above them.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val scroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(start = 24.dp, end = 24.dp, top = if (choices.size > 1) 12.dp else 24.dp, bottom = 24.dp)) {
                    // No arrangement spacing: these swap places, and fixed gaps would jump when one leaves.
                    val enter = expandVertically() + fadeIn()
                    val exit = shrinkVertically() + fadeOut()
                    AnimatedVisibility(versions.isEmpty() && state.catalogLoading, enter = enter, exit = exit) {
                        CircularProgressIndicator(Modifier.size(16.dp))
                    }
                    AnimatedVisibility(versions.isEmpty() && !state.catalogLoading, enter = enter, exit = exit) {
                        Label(when {
                            choices.isNotEmpty() -> "All older versions are installed."
                            state.catalogError.isNotEmpty() -> state.catalogError
                            else -> "No older versions are available."
                        }, color = JewelTheme.globalColors.text.info)
                    }
                    AnimatedVisibility(versions.isNotEmpty(), enter = enter, exit = exit) {
                        ImageCard(state, versions) {}
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}
