package com.yourname.inventoryapp

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType

/** Форматы, для которых ZXing предоставляет самостоятельные декодеры. */
internal object BarcodeScanConfig {
    val formats: List<BarcodeFormat> = listOf(
        BarcodeFormat.QR_CODE,
        BarcodeFormat.DATA_MATRIX,
        BarcodeFormat.AZTEC,
        BarcodeFormat.PDF_417,
        BarcodeFormat.MAXICODE,
        BarcodeFormat.CODE_128,
        BarcodeFormat.CODE_39,
        BarcodeFormat.CODE_93,
        BarcodeFormat.CODABAR,
        BarcodeFormat.ITF,
        BarcodeFormat.EAN_8,
        BarcodeFormat.EAN_13,
        BarcodeFormat.UPC_E,
        BarcodeFormat.RSS_14,
        BarcodeFormat.RSS_EXPANDED
    )

    // По умолчанию ITFReader отвергает 2 и 4 цифры. ITF кодирует пары цифр;
    // длины больше максимальной из списка также разрешаются самим ZXing.
    // Это настройка проверки длины, а не дополнение/обрезка содержимого.
    val hints: Map<DecodeHintType, Any>
        get() = mapOf(
            DecodeHintType.ALLOWED_LENGTHS to intArrayOf(2, 4, 6, 8, 10, 12, 14)
        )
}
