package com.avdbuddy.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledSlider
import com.avdbuddy.theme.appFocusRing
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SliderState
import org.jetbrains.jewel.ui.theme.sliderStyle

@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val style = JewelTheme.sliderStyle
    val metrics = style.metrics
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val state = SliderState.of(enabled = enabled, hovered = hovered, pressed = pressed || dragged)
    val thumbWidth = metrics.thumbSize.width + metrics.thumbBorderWidth * 2
    val thumbHeight = metrics.thumbSize.height + metrics.thumbBorderWidth * 2

    Box(modifier.semantics(mergeDescendants = true) {}) {
        UnstyledSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            track = { slider ->
                Canvas(Modifier.fillMaxWidth().height(thumbHeight)) {
                    val inset = thumbWidth.toPx() / 2
                    val left = Offset(inset, size.height / 2)
                    val right = Offset((size.width - inset).coerceAtLeast(inset), size.height / 2)
                    val start = if (layoutDirection == LayoutDirection.Rtl) right else left
                    val end = if (layoutDirection == LayoutDirection.Rtl) left else right
                    drawLine(if (enabled) style.colors.track else style.colors.trackDisabled,
                        start, end, metrics.trackHeight.toPx(), StrokeCap.Round)
                    if (slider.fraction > 0f) {
                        drawLine(if (enabled) style.colors.trackFilled else style.colors.trackFilledDisabled,
                            start, Offset(start.x + (end.x - start.x) * slider.fraction, start.y),
                            metrics.trackHeight.toPx(), StrokeCap.Round)
                    }
                    if (steps > 0) {
                        val radius = 1.dp.toPx()
                        val y = size.height / 2 + metrics.trackHeight.toPx() / 2 + 3.dp.toPx() + radius
                        repeat(steps + 2) { index ->
                            val x = left.x + (right.x - left.x) * index / (steps + 1)
                            drawCircle(style.colors.stepMarker, radius, Offset(x, y))
                        }
                    }
                }
            },
            thumb = {
                Box(Modifier.padding(metrics.thumbBorderWidth)) {
                    Spacer(Modifier.size(metrics.thumbSize).hoverable(interaction, enabled)
                        .background(style.colors.thumbFillFor(state).value, style.thumbShape)
                        .border(Stroke.Alignment.Outside, metrics.thumbBorderWidth,
                            style.colors.thumbBorderFor(state).value, style.thumbShape)
                        .appFocusRing(interaction, style.thumbShape,
                            offset = metrics.thumbBorderWidth))
                }
            },
        )
    }
}
