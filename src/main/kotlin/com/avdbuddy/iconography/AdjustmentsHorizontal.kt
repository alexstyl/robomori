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

val Icons.AdjustmentsHorizontal: ImageVector
    get() {
        if (_AdjustmentsHorizontal != null) return _AdjustmentsHorizontal!!
        
        _AdjustmentsHorizontal = ImageVector.Builder(
            name = "adjustments-horizontal",
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
                moveTo(10.5f, 6f)
                horizontalLineToRelative(9.75f)
                moveTo(10.5f, 6f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
                moveToRelative(3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, false, -3f, 0f)
                moveTo(3.75f, 6f)
                horizontalLineTo(7.5f)
                moveToRelative(3f, 12f)
                horizontalLineToRelative(9.75f)
                moveToRelative(-9.75f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -3f, 0f)
                moveToRelative(3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, false, false, -3f, 0f)
                moveToRelative(-3.75f, 0f)
                horizontalLineTo(7.5f)
                moveToRelative(9f, -6f)
                horizontalLineToRelative(3.75f)
                moveToRelative(-3.75f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -3f, 0f)
                moveToRelative(3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, false, false, -3f, 0f)
                moveToRelative(-9.75f, 0f)
                horizontalLineToRelative(9.75f)
            }
        }.build()
        
        return _AdjustmentsHorizontal!!
    }

private var _AdjustmentsHorizontal: ImageVector? = null

