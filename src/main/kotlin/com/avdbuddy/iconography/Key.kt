/*
 * Icon from Heroicons (heroicons).
 * License: MIT
 *
 * MIT License
 *
 * Copyright (c) Tailwind Labs, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.avdbuddy.iconography

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.Key: ImageVector
    get() {
        if (_Key != null) return _Key!!

        _Key = ImageVector.Builder(
            name = "key",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(15.75f, 5.25f)
                arcToRelative(3f, 3f, 0f, false, true, 3f, 3f)
                moveToRelative(3f, 0f)
                arcToRelative(6f, 6f, 0f, false, true, -7.029f, 5.912f)
                curveToRelative(-0.563f, -0.097f, -1.159f, 0.026f, -1.563f, 0.43f)
                lineTo(10.5f, 17.25f)
                horizontalLineTo(8.25f)
                verticalLineToRelative(2.25f)
                horizontalLineTo(6f)
                verticalLineToRelative(2.25f)
                horizontalLineTo(2.25f)
                verticalLineToRelative(-2.818f)
                curveToRelative(0f, -0.597f, 0.237f, -1.17f, 0.659f, -1.591f)
                lineToRelative(6.499f, -6.499f)
                curveToRelative(0.404f, -0.404f, 0.527f, -1f, 0.43f, -1.563f)
                arcTo(6f, 6f, 0f, true, true, 21.75f, 8.25f)
                close()
            }
        }.build()

        return _Key!!
    }

private var _Key: ImageVector? = null
