package com.avdbuddy.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.avdbuddy.QrPairing
import kotlin.math.floor

@Composable
internal fun PairingQrCode(pairing: QrPairing, modifier: Modifier = Modifier) {
    val matrix = remember(pairing) { pairing.matrix() }
    Canvas(modifier.semantics { contentDescription = "Scan this QR code to pair your device" }) {
        drawRect(Color.White)
        val module = floor(size.minDimension / matrix.width)
        val left = (size.width - module * matrix.width) / 2
        val top = (size.height - module * matrix.height) / 2
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(left + x * module, top + y * module), Size(module, module))
        }
    }
}
