package com.avdbuddy

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import com.avdbuddy.theme.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.*
import com.avdbuddy.iconography.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

sealed class SettingsSection(val title: String, val id: String) {
    data object License : SettingsSection("Your License", "License")
    data object Images : SettingsSection("Platforms", "Images")
    data object Storage : SettingsSection("SDK Locations", "Storage")
    data object Updates : SettingsSection("Updates", "Updates")
}

@Composable
fun SettingsScreen(state: AppState, topInset: Dp) {
    val pages = rememberSaveableStateHolder()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val modifier = Modifier.testTag("SettingsScreen").onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            state.showSettings = false
            true
        } else false
    }.focusRequester(focus).focusable()
    AppScaffold(topInset, modifier, sidebarPinned = true, contentTopInset = 0.dp, sidebar = { sidebarInset ->
        Column(Modifier.fillMaxSize().padding(top = sidebarInset).padding(horizontal = 8.dp, vertical = 12.dp)) {
            SidebarListItem(selected = false, height = 36.dp,
                modifier = Modifier.fillMaxWidth().testTag("BackToEmulators")
                    .clickable(role = Role.Button) { state.showSettings = false }) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.ArrowLeft, tint = JewelTheme.globalColors.text.info, modifier = Modifier.size(16.dp))
                    Label("Back to app", color = JewelTheme.globalColors.text.info)
                }
            }
            Spacer(Modifier.height(16.dp))
            listOf(SettingsSection.License, SettingsSection.Storage, SettingsSection.Images, SettingsSection.Updates)
                .forEach { section ->
                    val title = when {
                        section == SettingsSection.Updates && state.updates.available != null -> "Updates (${state.updates.available!!.version})"
                        else -> section.title
                    }
                    val icon = when (section) {
                        SettingsSection.License -> Icons.Key
                        SettingsSection.Images -> Icons.Android
                        SettingsSection.Storage -> Icons.Cog6Tooth
                        SettingsSection.Updates -> Icons.ArrowDownTray
                    }
                    SidebarListItem(selected = state.settingsSection == section, height = 32.dp,
                        modifier = Modifier.fillMaxWidth().testTag("SettingsNav-${section.id}")
                            .semantics { selected = state.settingsSection == section }
                            .clickable(role = Role.Tab) { state.openSettings(section) }) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(icon, modifier = Modifier.size(16.dp))
                            Label(title)
                            if (section == SettingsSection.Images && state.downloads.activeCount > 0) {
                                Spacer(Modifier.weight(1f))
                                CircularProgressIndicator(Modifier.size(16.dp).testTag("AndroidImagesDownloadSpinner").semantics {
                                    contentDescription = "Downloading Android images"
                                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                                })
                            }
                        }
                    }
                }
        }
    }) {
        pages.SaveableStateProvider(state.settingsSection.id) {
            when (state.settingsSection) {
                SettingsSection.License -> LicensePage(state.license, state::manageLicense, state::purchaseLicense)
                SettingsSection.Images -> AndroidImagesPage(state)
                SettingsSection.Storage -> SdkLocationsPage(state)
                SettingsSection.Updates -> UpdatesPage(state)
            }
        }
    }
}

@Composable
fun SettingsPageScaffold(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().testTag(tag)) {
        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
            org.jetbrains.jewel.ui.component.Text(title, style = com.avdbuddy.theme.AvdBuddyTheme.typography.heading,
                modifier = Modifier.testTag("SettingsPageTitle"))
        }
        Rule()
        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            // The scroll area spans the page so the wheel works anywhere; only the content is capped.
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp)) {
                Column(Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}
