package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// AvdBuddy-drawn TV remote glyphs.

private fun remoteIcon(name: String, shape: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
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

val Icons.RemoteHome: ImageVector
    get() = _RemoteHome ?: remoteIcon("remote-home") {
        moveTo(3.5f, 11f)
        lineTo(12f, 4f)
        lineTo(20.5f, 11f)
        moveTo(5.75f, 9.25f)
        verticalLineTo(20f)
        horizontalLineTo(18.25f)
        verticalLineTo(9.25f)
        moveTo(10f, 20f)
        verticalLineTo(14.5f)
        horizontalLineTo(14f)
        verticalLineTo(20f)
    }.also { _RemoteHome = it }

private var _RemoteHome: ImageVector? = null

val Icons.RemotePlayPause: ImageVector
    get() = _RemotePlayPause ?: remoteIcon("remote-play-pause") {
        moveTo(3.5f, 6f)
        lineTo(11f, 12f)
        lineTo(3.5f, 18f)
        close()
        moveTo(15f, 6f)
        verticalLineTo(18f)
        moveTo(19.5f, 6f)
        verticalLineTo(18f)
    }.also { _RemotePlayPause = it }

private var _RemotePlayPause: ImageVector? = null
