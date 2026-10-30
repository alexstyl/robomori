package com.avdbuddy

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom
import java.util.Base64

internal class QrPairing private constructor(val serviceName: String, val password: String) {
    val payload get() = "WIFI:T:ADB;S:$serviceName;P:$password;;"
    fun matrix(): BitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0)

    companion object {
        fun create(): QrPairing {
            val random = SecureRandom()
            fun token(bytes: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))
            return QrPairing("studio-avdbuddy-${token(12)}", token(20))
        }
    }
}
