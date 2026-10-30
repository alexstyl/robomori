package com.composeunstyled

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** How an announcement ended. */
sealed interface AnnouncementResult {
    /** The person used the announcement's action, such as Undo. */
    data object ActionPerformed : AnnouncementResult
    /** The person dismissed it, the caller dismissed it, or a newer announcement replaced it. */
    data object Dismissed : AnnouncementResult
    /** Its duration ran out. */
    data object TimedOut : AnnouncementResult
}

/** What happens when something is announced while another announcement is showing. */
sealed interface AnnouncementPolicy {
    /** Wait for the current announcement to end, as snackbars do. */
    data object Queue : AnnouncementPolicy
    /** Replace the current announcement right away, as toasts do. */
    data object Replace : AnnouncementPolicy
}

/** How urgently assistive technologies announce it. */
sealed interface AnnouncementPoliteness {
    /** Announced once the screen reader finishes what it is reading. */
    data object Polite : AnnouncementPoliteness
    /** Announced right away, interrupting the screen reader. Use for errors that need attention. */
    data object Assertive : AnnouncementPoliteness
}

/**
 * A message shown by an [AnnouncementHost], such as a toast or a snackbar. Whoever announces it decides what
 * it says ([value]) and how it is presented: how long it stays, where it sits, and how it enters and leaves.
 */
class Announcement<T> internal constructor(
    val value: T,
    /** How long it stays before timing out, or null to stay until dismissed. */
    val duration: Duration?,
    /** Whether it contains controls, such as an action. Announcements with controls stay longer for accessibility. */
    val interactive: Boolean,
    val alignment: Alignment,
    val enter: EnterTransition,
    val exit: ExitTransition,
    val politeness: AnnouncementPoliteness,
    private val result: CancellableContinuation<AnnouncementResult>,
) {
    /** Ends the announcement because the person used its action. */
    fun performAction() = end(AnnouncementResult.ActionPerformed)

    /** Ends the announcement early. */
    fun dismiss() = end(AnnouncementResult.Dismissed)

    internal fun timeOut() = end(AnnouncementResult.TimedOut)

    private fun end(outcome: AnnouncementResult) {
        if (result.isActive) result.resume(outcome)
    }
}

/**
 * Holds the announcements an [AnnouncementHost] shows, one at a time.
 *
 * [announce] suspends until the announcement ends and returns how it ended, so callers can react to its action.
 */
class AnnouncementState<T>(val policy: AnnouncementPolicy = AnnouncementPolicy.Queue) {
    /** The announcement showing now, if any. */
    var current by mutableStateOf<Announcement<T>?>(null)
        private set
    private val queue = Mutex()

    /**
     * Shows [value] and suspends until it ends. Without [enter] or [exit] it appears and disappears without
     * animating. Cancelling the call dismisses the announcement.
     */
    suspend fun announce(
        value: T,
        duration: Duration? = 4.seconds,
        interactive: Boolean = false,
        alignment: Alignment = Alignment.BottomCenter,
        enter: EnterTransition = EnterTransition.None,
        exit: ExitTransition = ExitTransition.None,
        politeness: AnnouncementPoliteness = AnnouncementPoliteness.Polite,
    ): AnnouncementResult {
        require(duration == null || duration > Duration.ZERO) { "Announcement duration must be positive." }
        suspend fun show(): AnnouncementResult {
            var shown: Announcement<T>? = null
            try {
                return suspendCancellableCoroutine { continuation ->
                    val announcement = Announcement(value, duration, interactive, alignment, enter, exit, politeness, continuation)
                    current?.dismiss()
                    current = announcement
                    shown = announcement
                }
            } finally {
                if (current === shown) current = null
            }
        }
        return when (policy) {
            AnnouncementPolicy.Queue -> queue.withLock { show() }
            AnnouncementPolicy.Replace -> show()
        }
    }

    /** Dismisses the announcement showing now. Queued announcements still show afterwards. */
    fun dismiss() { current?.dismiss() }
}

/**
 * An unstyled layer that shows the announcements of [state] over [content], each the way it was announced: at its
 * alignment, with its enter and exit animations, for its duration. It decides none of these, and [announcement]
 * decides only how one looks.
 *
 * Durations follow the platform's recommended accessibility timeout, and pause while the announcement is hovered
 * or focused so it does not vanish while someone reads it or reaches for its action. Announcements are reported
 * to screen readers as live regions with their politeness.
 */
@Composable fun <T> AnnouncementHost(
    state: AnnouncementState<T>,
    modifier: Modifier = Modifier,
    announcement: @Composable (Announcement<T>) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val accessibility = LocalAccessibilityManager.current
    Box(modifier) {
        content()
        // An ending announcement keeps its own placement while it plays its exit animation.
        var displayed by remember { mutableStateOf(emptyList<Announcement<T>>()) }
        val current = state.current
        SideEffect { if (current != null && current !in displayed) displayed = displayed + current }
        displayed.forEach { shown ->
            key(shown) {
                val visibility = remember { MutableTransitionState(false) }
                visibility.targetState = state.current === shown
                val interactions = remember { MutableInteractionSource() }
                val hovered by interactions.collectIsHoveredAsState()
                var focused by remember { mutableStateOf(false) }
                val duration = shown.duration
                if (duration != null) {
                    LaunchedEffect(shown) {
                        val millis = accessibility?.calculateRecommendedTimeoutMillis(duration.inWholeMilliseconds,
                            containsIcons = false, containsText = true, containsControls = shown.interactive)
                            ?: duration.inWholeMilliseconds
                        var remaining = millis.milliseconds
                        while (remaining > Duration.ZERO) {
                            snapshotFlow { hovered || focused }.first { paused -> !paused }
                            val start = TimeSource.Monotonic.markNow()
                            withTimeoutOrNull(remaining) { snapshotFlow { hovered || focused }.first { paused -> paused } }
                            remaining -= start.elapsedNow()
                        }
                        shown.timeOut()
                    }
                }
                AnimatedVisibility(visibility,
                    Modifier.align(shown.alignment)
                        .hoverable(interactions)
                        .onFocusChanged { focused = it.hasFocus }
                        .semantics {
                            liveRegion = when (shown.politeness) {
                                AnnouncementPoliteness.Polite -> LiveRegionMode.Polite
                                AnnouncementPoliteness.Assertive -> LiveRegionMode.Assertive
                            }
                        },
                    enter = shown.enter, exit = shown.exit) {
                    announcement(shown)
                }
                if (visibility.isIdle && !visibility.currentState && !visibility.targetState) {
                    SideEffect { displayed = displayed - shown }
                }
            }
        }
    }
}
