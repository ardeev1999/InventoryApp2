package com.yourname.inventory.data

import org.apache.poi.ss.usermodel.*
import java.math.BigDecimal
import java.util.Locale

class StatementParser {
    data class Result(val items: List<InventoryItem>, val warnings: List<String>, val sheetName: String,
        val skippedWithoutBarcode: Int = 0, val withoutInventoryNumber: Int = 0,
        val duplicateRows: Int = 0)
    private val formatter = DataFormatter(Locale.US).apply { setUseCachedValuesForFormulaCells(true) }
    private data class Columns(val name: Int, val number: Int, val barcode: Int, val firstRow: Int)
    fun parse(workbook: Workbook): List<InventoryItem> = parseReport(workbook).items

    fun parseReport(workbook: Workbook): Result {
        for (sheet in workbook) {
            val columns = detectColumns(sheet) ?: continue
            val items = linkedMapOf<String, InventoryItem>()
            val warnings = mutableListOf<String>()
            var skipped = 0
            var duplicates = 0
            for (index in columns.firstRow..sheet.lastRowNum) {
                val row = sheet.getRow(index) ?: continue
                val name = text(row.getCell(columns.name))
                val numberCell = row.getCell(columns.number)
                val barcodeCell = row.getCell(columns.barcode)
                if (name.isEmpty() && text(numberCell).isEmpty() && text(barcodeCell).isEmpty()) continue
                if (text(barcodeCell).isEmpty()) { skipped++; continue }
                if (name.isEmpty()) { warnings.add("Строка ${index + 1}: нет названия"); continue }
                try {
                    val barcode = identifier(barcodeCell!!)
                    val number = if (text(numberCell).isEmpty()) "" else identifier(numberCell!!)
                    val item = InventoryItem(barcode = barcode, name = name, inventoryNumber = number)
                    val previous = items[barcode]
                    // Нельзя молча выбрать предмет при конфликте ключа.
                    check(previous == null || previous == item) {
                        "Лист '${sheet.sheetName}', строка ${index + 1}: один штрихкод у разных предметов. Исправьте файл."
                    }
                    if (previous != null) duplicates++ else items[barcode] = item
                } catch (e: IllegalArgumentException) {
                    warnings.add("Строка ${index + 1}: ${e.message}")
                }
            }
            return Result(items.values.toList(), warnings, sheet.sheetName, skipped,
                items.values.count { it.inventoryNumber.isEmpty() }, duplicates)
        }
        error("Нужны заголовки: 'Основное средство' (или 'Наименование' / 'Название'), 'Инвентарный номер', 'Штрихкод'.")
    }

    private fun detectColumns(sheet: Sheet): Columns? {
        for (row in sheet) {
            val values = row.associate { it.columnIndex to text(it).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ") }
            val name = values.entries.firstOrNull { it.value in listOf("основное средство", "наименование", "название", "название позиции") }?.key
            val number = values.entries.firstOrNull { it.value == "инвентарный номер" }?.key
            val barcode = values.entries.firstOrNull { it.value == "штрихкод" }?.key
            if (name != null && number != null && barcode != null) return Columns(name, number, barcode, row.rowNum + 1)
        }
        return null
    }
    private fun text(cell: Cell?): String {
        // Выгрузка 1С обозначает отсутствие значения как <c t="e"/> без <v>.
        // Это не настоящая ошибка Excel (#N/A и др. содержат значение).
        if (cell is org.apache.poi.xssf.usermodel.XSSFCell &&
            cell.cellType == CellType.ERROR && cell.rawValue == null) return ""
        return formatter.formatCellValue(cell).trim()
    }

    private fun identifier(cell: Cell): String {
        val type = if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType
        if (type == CellType.STRING) return cell.stringCellValue.trim()
        require(type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            "Ячейка ${cell.address}: идентификатор должен быть текстом или целым числом"
        }
        val value = cell.numericCellValue
        require(value.isFinite() && value >= 0 && value % 1.0 == 0.0 && value < 1e15) {
            "Ячейка ${cell.address}: длинный или дробный номер сохранён числом. Выгрузите инвентарные номера из 1С как текст, чтобы не потерять цифры."
        }
        val formatted = text(cell)
        // Сохраняем формат 0000; General/E+ и разделители не превращаем в часть номера.
        return if (formatted.matches(Regex("[0-9]+"))) formatted
        else BigDecimal.valueOf(value).toBigIntegerExact().toString()
    }
}
