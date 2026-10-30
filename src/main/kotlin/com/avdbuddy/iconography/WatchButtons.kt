package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// AvdBuddy-drawn watch button glyphs: the button on the left with an arrow pressing into it.

private fun pressIcon(name: String, arrowTip: Float = 13f, button: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    button()
    stroke {
        // Arrow pointing left, at the button.
        moveTo(arrowTip + 6f, 12f)
        horizontalLineTo(arrowTip)
        moveTo(arrowTip + 3f, 9f)
        lineTo(arrowTip, 12f)
        lineTo(arrowTip + 3f, 15f)
    }
}.build()

private fun ImageVector.Builder.stroke(width: Float = 1.5f, pathBuilder: PathBuilder.() -> Unit) = path(
    fill = SolidColor(Color.Transparent),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = pathBuilder,
)

/** The crown seen side-on: an oval with knurl ticks around its edge. */
val Icons.WatchCrown: ImageVector
    get() = _WatchCrown ?: pressIcon("watch-crown") {
        stroke {
            moveTo(10.5f, 12f)
            arcToRelative(3.5f, 7f, 0f, true, true, -7f, 0f)
            arcToRelative(3.5f, 7f, 0f, false, true, 7f, 0f)
            close()
        }
        stroke(width = 1.2f) {
            moveTo(4.4f, 7.5f); lineTo(6.2f, 8.2f)
            moveTo(3.8f, 10f); lineTo(5.6f, 10.4f)
            moveTo(3.7f, 12.5f); lineTo(5.5f, 12.6f)
            moveTo(3.9f, 15f); lineTo(5.7f, 14.7f)
            moveTo(4.6f, 17.3f); lineTo(6.3f, 16.6f)
        }
    }.also { _WatchCrown = it }

private var _WatchCrown: ImageVector? = null

/** The side button: a line beside the top-right curve of the case. */
val Icons.WatchSideButton: ImageVector
    get() = _WatchSideButton ?: pressIcon("watch-side-button", arrowTip = 16.5f) {
        stroke {
            moveTo(2f, 7.3f)
            arcTo(14f, 14f, 0f, false, true, 13f, 20f)
        }
        stroke(width = 2f) {
            moveTo(9.1f, 7.6f)
            arcTo(16.8f, 16.8f, 0f, false, true, 14f, 13.4f)
        }
    }.also { _WatchSideButton = it }

private var _WatchSideButton: ImageVector? = null
