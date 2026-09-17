package com.yourname.inventory.data

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.OutputStream

/** Экспорт содержит только пользовательские поля; номера записываются текстом. */
object InventoryExporter {
    enum class Selection { FOUND, REMAINING, BOTH }
    fun write(items: List<InventoryItem>, selection: Selection, output: OutputStream) {
        XSSFWorkbook().use { workbook ->
            fun sheet(name: String, scanned: Boolean) {
                val sheet = workbook.createSheet(name)
                val header = sheet.createRow(0)
                header.createCell(0).setCellValue("Наименование")
                header.createCell(1).setCellValue("Инвентарный номер")
                val textStyle = workbook.createCellStyle().apply {
                    dataFormat = workbook.createDataFormat().getFormat("@")
                }
                items.filter { it.scanned == scanned }.forEachIndexed { index, item ->
                    sheet.createRow(index + 1).apply {
                        createCell(0).setCellValue(item.name)
                        createCell(1).apply {
                            cellStyle = textStyle
                            setCellValue(item.displayInventoryNumber)
                        }
                    }
                }
                sheet.setColumnWidth(0, 70 * 256)
                sheet.setColumnWidth(1, 32 * 256)
                sheet.createFreezePane(0, 1)
            }
            if (selection != Selection.REMAINING) sheet("Найденные", true)
            if (selection != Selection.FOUND) sheet("Оставшиеся", false)
            workbook.write(output)
        }
    }
}
