package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// AvdBuddy-drawn recording glyphs, matching the outline weight of the other controls.
private fun captureIcon(name: String, center: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(12f, 3.75f)
        arcTo(8.25f, 8.25f, 0f, true, true, 12f, 20.25f)
        arcTo(8.25f, 8.25f, 0f, true, true, 12f, 3.75f)
        close()
        center()
    }
}.build()

val Icons.RecordCircle: ImageVector
    get() = _RecordCircle ?: captureIcon("record-circle") {
        moveTo(12f, 8.5f)
        arcTo(3.5f, 3.5f, 0f, true, true, 12f, 15.5f)
        arcTo(3.5f, 3.5f, 0f, true, true, 12f, 8.5f)
        close()
    }.also { _RecordCircle = it }

private var _RecordCircle: ImageVector? = null

val Icons.StopCircle: ImageVector
    get() = _StopCircle ?: captureIcon("stop-circle") {
        moveTo(8.5f, 8.5f)
        horizontalLineTo(15.5f)
        verticalLineTo(15.5f)
        horizontalLineTo(8.5f)
        close()
    }.also { _StopCircle = it }

private var _StopCircle: ImageVector? = null
