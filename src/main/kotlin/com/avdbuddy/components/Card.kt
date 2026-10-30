package com.avdbuddy.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avdbuddy.theme.AvdBuddyTheme

/** A bordered surface that groups related rows; separate rows with [CardDivider]. */
@Composable fun Card(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.fillMaxWidth()
            .background(AvdBuddyTheme.colors.panelBackground, shape)
            .border(1.dp, AvdBuddyTheme.colors.shelfBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable fun CardDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(AvdBuddyTheme.colors.shelfBorder))
}

/**
 * Card rows that expand in when added and collapse out when removed, with a [CardDivider] above every
 * row but the first. Rows present on first composition appear without animating.
 */
@Composable fun <T> ColumnScope.AnimatedCardRows(items: List<T>, key: (T) -> Any, row: @Composable (T) -> Unit) {
    // Plain holders: they only change during composition, and finished exits recompose through `removals`.
    val rows = remember { LinkedHashMap<Any, AnimatedRow<T>>() }
    var removals by remember { mutableIntStateOf(0) }
    val firstComposition = rows.isEmpty() && removals == 0
    val keys = items.map(key)
    // Keep a removed row where it was, after the closest row that is still listed before it.
    val order = keys.toMutableList()
    var anchor = -1
    rows.keys.forEach { existing ->
        val index = order.indexOf(existing)
        if (index >= 0) anchor = index else order.add(++anchor, existing)
    }
    items.forEach { item ->
        val k = key(item)
        val existing = rows[k]
        if (existing == null) rows[k] = AnimatedRow(item, MutableTransitionState(firstComposition).apply { targetState = true })
        else { existing.item = item; existing.visibility.targetState = true }
    }
    rows.keys.filter { it !in keys }.forEach { rows.getValue(it).visibility.targetState = false }
    order.forEachIndexed { index, k ->
        val entry = rows.getValue(k)
        key(k) {
            AnimatedVisibility(entry.visibility, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    if (index > 0) CardDivider()
                    row(entry.item)
                }
            }
        }
        if (!entry.visibility.targetState && !entry.visibility.currentState && entry.visibility.isIdle) {
            SideEffect { if (rows.remove(k) != null) removals++ }
        }
    }
}

private class AnimatedRow<T>(var item: T, val visibility: MutableTransitionState<Boolean>)
