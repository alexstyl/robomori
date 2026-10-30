package com.avdbuddy.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avdbuddy.DeviceType

private val frame = Color(0xFF2B2C30)
private val band = Color(0xFF5A5C63)
private val idleScreen = Color(0xFFE4E6EB)
// A Pixel-like wallpaper, so a running emulator reads as switched on.
private val litScreen = Brush.linearGradient(listOf(Color(0xFF9CC2FF), Color(0xFF3574F0), Color(0xFF6B4CD6)))

/** A small two-tone drawing of the device, with its screen lit while the emulator runs. */
@Composable fun DeviceGlyph(type: DeviceType, running: Boolean, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Canvas(modifier.size(size)) {
        val screen: Brush = if (running) litScreen else Brush.linearGradient(listOf(idleScreen, idleScreen))
        when (type) {
            DeviceType.PHONE -> phone(screen)
            DeviceType.FOLDABLE -> foldable(screen)
            DeviceType.TABLET -> tablet(screen)
            DeviceType.WEAR -> watch(screen)
            DeviceType.TV -> television(screen)
        }
    }
}

/** A framed screen centred in the canvas; returns the screen bounds. */
private fun DrawScope.slab(width: Float, height: Float, radius: Float, bezel: Float, screen: Brush, top: Float = (size.height - height) / 2): Rect {
    val left = (size.width - width) / 2
    drawRoundRect(frame, Offset(left, top), Size(width, height), CornerRadius(radius))
    val inner = Rect(left + bezel, top + bezel, left + width - bezel, top + height - bezel)
    drawRoundRect(screen, inner.topLeft, inner.size, CornerRadius((radius - bezel).coerceAtLeast(1f)))
    return inner
}

private fun DrawScope.phone(screen: Brush) {
    val height = size.height * .94f
    val width = height * .48f
    val inner = slab(width, height, width * .24f, width * .07f, screen)
    // Side buttons on the right edge, like a Pixel.
    drawRect(frame, Offset(inner.right + width * .07f, inner.top + height * .2f), Size(size.width * .025f, height * .12f))
    drawCircle(frame, width * .055f, Offset(inner.center.x, inner.top + width * .12f))
}

private fun DrawScope.foldable(screen: Brush) {
    val height = size.height * .8f
    val width = height * .92f
    val inner = slab(width, height, width * .12f, width * .05f, screen)
    drawLine(frame.copy(alpha = .35f), Offset(inner.center.x, inner.top), Offset(inner.center.x, inner.bottom), size.width * .02f)
    drawCircle(frame, width * .035f, Offset(inner.left + inner.width * .75f, inner.top + width * .07f))
}

private fun DrawScope.tablet(screen: Brush) {
    val width = size.width * .96f
    val height = width * .66f
    val inner = slab(width, height, width * .09f, width * .055f, screen)
    drawCircle(Color(0xFF55575E), width * .014f, Offset(inner.center.x, inner.top - width * .028f))
}

private fun DrawScope.watch(screen: Brush) {
    val diameter = size.minDimension * .64f
    val center = Offset(size.width / 2, size.height / 2)
    val bandWidth = diameter * .6f
    drawRoundRect(band, Offset(center.x - bandWidth / 2, 0f), Size(bandWidth, size.height), CornerRadius(bandWidth * .25f))
    drawCircle(frame, diameter / 2, center)
    drawCircle(screen, diameter / 2 * .84f, center)
    drawRoundRect(frame, Offset(center.x + diameter / 2 - diameter * .02f, center.y - diameter * .1f),
        Size(diameter * .1f, diameter * .2f), CornerRadius(diameter * .03f))
}

private fun DrawScope.television(screen: Brush) {
    val width = size.width * .96f
    val height = width * .6f
    val top = size.height * .12f
    val inner = slab(width, height, width * .04f, width * .035f, screen, top)
    val base = inner.bottom + width * .035f
    drawRoundRect(frame, Offset(size.width / 2 - width * .25f, base + size.height * .06f), Size(width * .5f, size.height * .05f), CornerRadius(4f))
}
