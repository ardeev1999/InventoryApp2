package com.yourname.inventory.data

import org.apache.poi.ss.usermodel.*
import java.math.BigDecimal
import java.util.Locale

/** Читает один реестр, а не складывает повторяющие его листы одной книги. */
class StatementParser {
    data class Result(val items: List<InventoryItem>, val warnings: List<String>, val sheetName: String)
    private val formatter = DataFormatter(Locale.US).apply {
        setUseCachedValuesForFormulaCells(true)
    }

    private data class Columns(val name: Int, val number: Int, val firstRow: Int = 0)

    fun parse(workbook: Workbook): List<InventoryItem> = parseReport(workbook).items

    fun parseReport(workbook: Workbook): Result {
        for (sheet in workbook) {
            val columns = detectColumns(sheet) ?: continue
            val items = linkedMapOf<String, InventoryItem>()
            val warnings = mutableListOf<String>()
            for (index in columns.firstRow..sheet.lastRowNum) {
                val row = sheet.getRow(index) ?: continue
                val name = text(row.getCell(columns.name))
                val numberCell = row.getCell(columns.number)
                if (!isItem(name, text(numberCell))) continue
                val number = try {
                    identifier(numberCell!!)
                } catch (e: IllegalArgumentException) {
                    warnings.add("$name: ${e.message}")
                    continue
                }
                val previous = items[number]
                require(previous == null || previous.name == name) {
                    "Лист '${sheet.sheetName}', строка ${index + 1}: номер $number повторяется у разных предметов"
                }
                items[number] = InventoryItem(inventoryNumber = number, name = name, qrData = number)
            }
            if (items.isNotEmpty() || warnings.isNotEmpty()) return Result(items.values.toList(), warnings, sheet.sheetName)
        }
        error("Не найдена таблица предметов. Поддерживаются ведомость 1С (C/J), список A/B или A/H и таблица с заголовками 'Основное средство' и 'Инвентарный номер'.")
    }

    private fun detectColumns(sheet: Sheet): Columns? {
        // Заголовки могут находиться после названия отчёта и строк группировки.
        for (row in sheet) {
            val values = row.associate { it.columnIndex to text(it).lowercase().replace(Regex("\\s+"), " ") }
            val name = values.entries.firstOrNull {
                it.value == "основное средство" || it.value == "наименование"
            }?.key
            val number = values.entries.firstOrNull { it.value == "инвентарный номер" }?.key
            if (name != null && number != null) return Columns(name, number, row.rowNum + 1)
        }
        // Только известные схемы. Колонка A с порядковым номером не участвует
        // в выборе номера ведомости: для C/J номер всегда берётся из J.
        return listOf(Columns(2, 9), Columns(0, 1), Columns(0, 7))
            .map { columns -> columns to sheet.count { row ->
                isItem(text(row.getCell(columns.name)), text(row.getCell(columns.number)))
            } }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }?.first
    }

    private fun isItem(name: String, number: String): Boolean =
        name.any { it.isLetter() } && number.isNotEmpty() &&
            !name.startsWith("Итого", ignoreCase = true) &&
            !name.startsWith("Всего", ignoreCase = true) &&
            !number.equals("Инвентарный номер", ignoreCase = true)

    private fun text(cell: Cell?): String = formatter.formatCellValue(cell).trim()

    private fun identifier(cell: Cell): String {
        val type = if (cell.cellType == CellType.FORMULA) cell.cachedFormulaResultType else cell.cellType
        if (type == CellType.STRING) return cell.stringCellValue.trim()
        require(type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            "Ячейка ${cell.address}: инвентарный номер должен быть текстом или целым числом"
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
