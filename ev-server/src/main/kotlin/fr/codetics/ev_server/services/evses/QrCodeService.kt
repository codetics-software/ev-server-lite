package fr.codetics.ev_server.services.evses

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.springframework.stereotype.Service

@Service
class QrCodeService {
    fun svg(url: String): String {
        val matrix =
            QRCodeWriter()
                .encode(url, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 4))
        val path = buildString {
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                if (matrix[x, y]) append("M$x ${y}h1v1h-1z")
            }
        }
        return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${matrix.width} ${matrix.height}" role="img" aria-label="Accéder au portail de recharge"><rect width="100%" height="100%" fill="white"/><path d="$path" fill="black"/></svg>"""
    }
}
