package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.EllipsisVertical: ImageVector by lazy {
    ImageVector.Builder("ellipsis-vertical", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            for (y in listOf(5f, 12f, 19f)) {
                moveTo(13.5f, y)
                arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, y)
                arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, y)
                close()
            }
        }
    }.build()
}
