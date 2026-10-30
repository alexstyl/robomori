package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// AvdBuddy-drawn foldable glyphs, showing the posture the button switches to.

private fun foldIcon(name: String, shape: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Transparent),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = shape,
    )
}.build()

private fun PathBuilder.panel(left: Float, right: Float, top: Float = 4f, bottom: Float = 20f) {
    moveTo(left + 1.5f, top)
    horizontalLineTo(right - 1.5f)
    arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
    verticalLineTo(bottom - 1.5f)
    arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
    horizontalLineTo(left + 1.5f)
    arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
    verticalLineTo(top + 1.5f)
    arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
    close()
}

/** Closed: one panel with the second showing as an edge behind it. */
val Icons.DeviceFolded: ImageVector
    get() = _DeviceFolded ?: foldIcon("device-folded") {
        panel(6.5f, 16f, top = 3f, bottom = 21f)
        moveTo(18f, 4.5f)
        verticalLineTo(19.5f)
    }.also { _DeviceFolded = it }

private var _DeviceFolded: ImageVector? = null

/** Open: two panels side by side across the hinge. */
val Icons.DeviceUnfolded: ImageVector
    get() = _DeviceUnfolded ?: foldIcon("device-unfolded") {
        panel(2.5f, 11.5f)
        panel(12.5f, 21.5f)
    }.also { _DeviceUnfolded = it }

private var _DeviceUnfolded: ImageVector? = null
