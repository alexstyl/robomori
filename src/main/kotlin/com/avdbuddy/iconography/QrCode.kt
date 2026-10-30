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

val Icons.QrCode: ImageVector
    get() {
        if (_QrCode != null) return _QrCode!!

        _QrCode = ImageVector.Builder(
            name = "qr-code",
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
                moveTo(3.75f, 4.875f)
                curveToRelative(0f, -0.621f, 0.504f, -1.125f, 1.125f, -1.125f)
                horizontalLineToRelative(4.5f)
                curveToRelative(0.621f, 0f, 1.125f, 0.504f, 1.125f, 1.125f)
                verticalLineToRelative(4.5f)
                curveToRelative(0f, 0.621f, -0.504f, 1.125f, -1.125f, 1.125f)
                horizontalLineToRelative(-4.5f)
                arcTo(1.125f, 1.125f, 0f, false, true, 3.75f, 9.375f)
                verticalLineToRelative(-4.5f)
                close()
                moveTo(3.75f, 14.625f)
                curveToRelative(0f, -0.621f, 0.504f, -1.125f, 1.125f, -1.125f)
                horizontalLineToRelative(4.5f)
                curveToRelative(0.621f, 0f, 1.125f, 0.504f, 1.125f, 1.125f)
                verticalLineToRelative(4.5f)
                curveToRelative(0f, 0.621f, -0.504f, 1.125f, -1.125f, 1.125f)
                horizontalLineToRelative(-4.5f)
                arcToRelative(1.125f, 1.125f, 0f, false, true, -1.125f, -1.125f)
                verticalLineToRelative(-4.5f)
                close()
                moveTo(13.5f, 4.875f)
                curveToRelative(0f, -0.621f, 0.504f, -1.125f, 1.125f, -1.125f)
                horizontalLineToRelative(4.5f)
                curveToRelative(0.621f, 0f, 1.125f, 0.504f, 1.125f, 1.125f)
                verticalLineToRelative(4.5f)
                curveToRelative(0f, 0.621f, -0.504f, 1.125f, -1.125f, 1.125f)
                horizontalLineToRelative(-4.5f)
                arcTo(1.125f, 1.125f, 0f, false, true, 13.5f, 9.375f)
                verticalLineToRelative(-4.5f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(6.75f, 6.75f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
                moveTo(6.75f, 16.5f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
                moveTo(16.5f, 6.75f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
                moveTo(13.5f, 13.5f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
                moveTo(13.5f, 19.5f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
                moveTo(19.5f, 13.5f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
                moveTo(19.5f, 19.5f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
                moveTo(16.5f, 16.5f)
                horizontalLineToRelative(0.75f)
                verticalLineToRelative(0.75f)
                horizontalLineToRelative(-0.75f)
                verticalLineToRelative(-0.75f)
                close()
            }
        }.build()

        return _QrCode!!
    }

private var _QrCode: ImageVector? = null
