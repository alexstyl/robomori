package com.avdbuddy.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avdbuddy.iconography.ExclamationCircle
import com.avdbuddy.iconography.Icons
import com.avdbuddy.theme.AvdBuddyTheme
import com.composeunstyled.AnnouncementHost
import com.composeunstyled.AnnouncementPolicy
import com.composeunstyled.AnnouncementPoliteness
import com.composeunstyled.AnnouncementState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What an app toast says, and its tone. */
data class ToastMessage(val text: String, val tone: ToastTone = ToastTone.Neutral)

sealed interface ToastTone {
    data object Neutral : ToastTone
    /** Something the person started did not work. */
    data object Error : ToastTone
}

/** How an app toast behaves: where it sits, how long it stays, and how it moves. */
class ToastPresentation(
    val alignment: Alignment,
    val duration: Duration,
    val enter: EnterTransition,
    val exit: ExitTransition,
) {
    companion object {
        /** Drops in from above the top edge. For messages about the whole app, such as failures. */
        val Top = ToastPresentation(Alignment.TopCenter, 5.seconds,
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { -it },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it })

        /** Rises gently from the bottom. */
        val Bottom = ToastPresentation(Alignment.BottomCenter, 3.seconds,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { 16 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { 16 })
    }
}

/** The app toasts a [ToastHost] shows. A new toast replaces the current one: only the latest is worth reading. */
class ToastState {
    internal val announcements = AnnouncementState<ToastMessage>(AnnouncementPolicy.Replace)

    /** Shows [message] and suspends until it goes away. Errors interrupt screen readers; other messages wait their turn. */
    suspend fun show(message: ToastMessage, presentation: ToastPresentation = ToastPresentation.Bottom) {
        announcements.announce(message, presentation.duration,
            alignment = presentation.alignment, enter = presentation.enter, exit = presentation.exit,
            politeness = when (message.tone) {
                ToastTone.Error -> AnnouncementPoliteness.Assertive
                ToastTone.Neutral -> AnnouncementPoliteness.Polite
            })
    }

    /** Hides the toast showing now. */
    fun dismiss() = announcements.dismiss()
}

@Composable fun rememberToastState(): ToastState = remember { ToastState() }

/** An app toast, styled from the theme. */
@Composable fun Toast(message: ToastMessage, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 500.dp)
        .background(AvdBuddyTheme.colors.toastBackground, RoundedCornerShape(percent = 50))
        .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
        .testTag("Toast"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (message.tone == ToastTone.Error) {
            Icon(Icons.ExclamationCircle, tint = AvdBuddyTheme.colors.toastText)
        }
        Label(message.text, maxLines = 2, color = AvdBuddyTheme.colors.toastText)
    }
}

/**
 * Shows app toasts over [content]. [insets] keep toasts clear of the window edges, such as the macOS title bar.
 * They move with the toast, so a toast sliding in from an edge starts beyond it.
 */
@Composable fun ToastHost(
    state: ToastState,
    modifier: Modifier = Modifier,
    insets: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) = AnnouncementHost(state.announcements, modifier, announcement = { Toast(it.value, Modifier.padding(insets)) }, content = content)
