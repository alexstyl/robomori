package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun navigationIcon(name: String, shape: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
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
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = shape,
    )
}.build()

val Icons.AndroidBack: ImageVector get() = androidBack
val Icons.AndroidHome: ImageVector get() = androidHome
val Icons.AndroidRecents: ImageVector get() = androidRecents

private val androidBack by lazy {
    navigationIcon("android-back") {
        moveTo(6f, 12f)
        lineTo(18f, 5f)
        verticalLineTo(19f)
        close()
    }
}

private val androidHome by lazy {
    navigationIcon("android-home") {
        moveTo(12f, 5f)
        arcTo(7f, 7f, 0f, true, true, 12f, 19f)
        arcTo(7f, 7f, 0f, true, true, 12f, 5f)
        close()
    }
}

private val androidRecents by lazy {
    navigationIcon("android-recents") {
        moveTo(5f, 5f)
        horizontalLineTo(19f)
        verticalLineTo(19f)
        horizontalLineTo(5f)
        close()
    }
}
