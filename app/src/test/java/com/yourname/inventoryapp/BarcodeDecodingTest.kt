package com.yourname.inventoryapp

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeDecodingTest {
    @Test
    fun shortCodesAndLeadingZerosSurviveDecoding() {
        val formats = listOf(
            BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
            BarcodeFormat.ITF, BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.AZTEC, BarcodeFormat.PDF_417
        )
        for (format in formats) {
            for (value in listOf("1234", "0012", "0000", "001234", "1234567890123456")) {
                assertDecodes(format, value)
            }
        }
        assertDecodes(BarcodeFormat.ITF, "12")
    }

    @Test
    fun textAndWhitespaceArePreserved() {
        for (format in listOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128)) {
            assertDecodes(format, "AB12")
            assertDecodes(format, " 0012 ")
        }
    }

    @Test
    fun retailCodesRetainTheirEncodedDigits() {
        assertDecodes(BarcodeFormat.EAN_8, "12345670")
        assertDecodes(BarcodeFormat.EAN_13, "5901234123457")
        assertDecodes(BarcodeFormat.UPC_A, "012345678905")
        assertDecodes(BarcodeFormat.UPC_E, "01234565")
        // A/B — служебные start/stop-символы Codabar, не часть номера.
        assertDecodes(BarcodeFormat.CODABAR, "A0012B", "0012")
    }

    private fun assertDecodes(format: BarcodeFormat, value: String, expected: String = value) {
        val matrix = MultiFormatWriter().encode(value, format, 600, 300)
        // Белое поле нужно в том числе форматам, чей Writer не добавляет quiet zone.
        val border = 40
        val width = matrix.width + 2 * border
        val height = matrix.height + 2 * border
        val pixels = IntArray(width * height) { -1 }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) pixels[(y + border) * width + x + border] = 0xFF000000.toInt()
            }
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        val hints = BarcodeScanConfig.hints +
            (DecodeHintType.POSSIBLE_FORMATS to BarcodeScanConfig.formats)
        val result = MultiFormatReader().decode(bitmap, hints)
        assertEquals("$format: $value", expected, result.text)
        assertEquals(format, result.barcodeFormat)
    }
}
