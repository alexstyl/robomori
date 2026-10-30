package com.avdbuddy.theme

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.horizontalScroll as foundationHorizontalScroll
import androidx.compose.foundation.verticalScroll as foundationVerticalScroll
import androidx.compose.foundation.lazy.LazyColumn as FoundationLazyColumn
import androidx.compose.foundation.lazy.LazyRow as FoundationLazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp

internal val LocalMacOverscrollFactory = staticCompositionLocalOf<MacOverscrollFactory?> { null }

@Composable
private fun rememberScrollEffect(orientation: Orientation): OverscrollEffect? {
    val factory = LocalMacOverscrollFactory.current ?: return rememberOverscrollEffect()
    return remember(factory, orientation) { factory.createOverscrollEffect(orientation) }
}

// The scroll API already owns its axis; screens do not configure the effect separately.
internal fun Modifier.verticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier = composed {
    foundationVerticalScroll(
        state = state,
        overscrollEffect = if (enabled) rememberScrollEffect(Orientation.Vertical) else null,
        enabled = enabled,
        flingBehavior = flingBehavior,
        reverseScrolling = reverseScrolling,
    )
}

internal fun Modifier.horizontalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier = composed {
    foundationHorizontalScroll(
        state = state,
        overscrollEffect = if (enabled) rememberScrollEffect(Orientation.Horizontal) else null,
        enabled = enabled,
        flingBehavior = flingBehavior,
        reverseScrolling = reverseScrolling,
    )
}

@Composable
internal fun LazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (reverseLayout) Arrangement.Bottom else Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    FoundationLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = if (userScrollEnabled) rememberScrollEffect(Orientation.Vertical) else null,
        content = content,
    )
}

@Composable
internal fun LazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal = if (reverseLayout) Arrangement.End else Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    FoundationLazyRow(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        overscrollEffect = if (userScrollEnabled) rememberScrollEffect(Orientation.Horizontal) else null,
        content = content,
    )
}
