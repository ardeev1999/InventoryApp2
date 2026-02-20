package com.yourname.inventory.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream

class ExcelImporter(private val context: Context) {

    suspend fun importFromExcel(uri: Uri): List<InventoryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<InventoryItem>()

        try {
            Log.d("ExcelImporter", "=== НАЧАЛО ИМПОРТА EXCEL ===")
            Log.d("ExcelImporter", "URI: $uri")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                Log.d("ExcelImporter", "Workbook создан успешно")
                
                val sheet = workbook.getSheetAt(0)
                Log.d("ExcelImporter", "Лист: '${sheet.sheetName}', строк: ${sheet.lastRowNum + 1}")
                
                // ★★★★ ДИАГНОСТИКА: показываем структуру файла ★★★★
                Log.d("ExcelImporter", "=== СТРУКТУРА ФАЙЛА ===")
                
                // Ищем заголовок и определяем структуру
                val columnMapping = detectColumnStructure(sheet)
                Log.d("ExcelImporter", "Найдены колонки: $columnMapping")
                
                // ★★★★ ОСНОВНОЙ ИМПОРТ ★★★★
                Log.d("ExcelImporter", "=== НАЧИНАЕМ ИМПОРТ ===")
                
                for (rowIndex in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex)
                    if (row == null) continue
                    
                    // Пропускаем заголовки
                    if (rowIndex == 0) {
                        Log.d("ExcelImporter", "Пропускаем заголовок")
                        continue
                    }
                    
                    val item = parseRow(row, columnMapping)
                    if (item != null) {
                        items.add(item)
                        
                        // Логируем первые 10 записей для отладки
                        if (items.size <= 10) {
                            Log.d("ExcelImporter", "Импортировано ${items.size}: ${item.inventoryNumber} - ${item.name}")
                        }
                    }
                }
                
                workbook.close()
                
                Log.d("ExcelImporter", "=== РЕЗУЛЬТАТ ИМПОРТА ===")
                Log.d("ExcelImporter", "Всего импортировано: ${items.size} предметов")
                
                // Логируем первую и последнюю запись
                if (items.isNotEmpty()) {
                    Log.d("ExcelImporter", "Первая запись: ${items.first().inventoryNumber} - ${items.first().name}")
                    Log.d("ExcelImporter", "Последняя запись: ${items.last().inventoryNumber} - ${items.last().name}")
                }
                
                // Валидация данных
                validateImportedData(items)
                
            } ?: run {
                Log.e("ExcelImporter", "Не удалось открыть InputStream")
            }

        } catch (e: Exception) {
            Log.e("ExcelImporter", "ОШИБКА импорта Excel: ${e.message}", e)
        }

        return@withContext items
    }
    
    // ★★★★ ОПРЕДЕЛЕНИЕ СТРУКТУРЫ КОЛОНОК ★★★★
    private fun detectColumnStructure(sheet: org.apache.poi.ss.usermodel.Sheet): Map<String, Int> {
        val columnMapping = mutableMapOf<String, Int>()
        
        // Ищем заголовки в первой строке
        val headerRow = sheet.getRow(0)
        if (headerRow != null) {
            Log.d("ExcelImporter", "Анализ заголовков:")
            for (cellIndex in 0..headerRow.lastCellNum.toInt()) {
                val cell = headerRow.getCell(cellIndex)
                val value = extractCellValue(cell)
                if (value.isNotEmpty()) {
                    Log.d("ExcelImporter", "Колонка $cellIndex: '$value'")
                    
                    when {
                        value.contains("Основное средство", ignoreCase = true) -> 
                            columnMapping["name"] = cellIndex
                        value.contains("Инвентарный номер", ignoreCase = true) -> 
                            columnMapping["inventoryNumber"] = cellIndex
                    }
                }
            }
        }
        
        // Если не нашли заголовки, используем эвристику
        if (columnMapping.isEmpty()) {
            Log.d("ExcelImporter", "Заголовки не найдены, использую эвристику")
            
            // Пробуем найти данные в типичных колонках
            val sampleRow = sheet.getRow(1) ?: sheet.getRow(2)
            if (sampleRow != null) {
                for (cellIndex in 0..minOf(7, sampleRow.lastCellNum.toInt())) {
                    val cellValue = extractCellValue(sampleRow.getCell(cellIndex))
                    if (cellValue.isNotEmpty()) {
                        // Эвристика: длинный текст - это название, числа - это номер
                        if (cellValue.length > 20 && !columnMapping.containsKey("name")) {
                            columnMapping["name"] = cellIndex
                            Log.d("ExcelImporter", "Предполагаю, что колонка $cellIndex - название")
                        } else if (cellValue.matches(Regex("\\d+")) && !columnMapping.containsKey("inventoryNumber")) {
                            columnMapping["inventoryNumber"] = cellIndex
                            Log.d("ExcelImporter", "Предполагаю, что колонка $cellIndex - инв. номер")
                        }
                    }
                }
            }
            
            // По умолчанию: A - название, H - номер (ваш случай)
            if (!columnMapping.containsKey("name")) {
                columnMapping["name"] = 0
            }
            if (!columnMapping.containsKey("inventoryNumber")) {
                columnMapping["inventoryNumber"] = 7
            }
        }
        
        return columnMapping
    }
    
    // ★★★★ ПАРСИНГ СТРОКИ ★★★★
    private fun parseRow(row: org.apache.poi.ss.usermodel.Row, columnMapping: Map<String, Int>): InventoryItem? {
        try {
            val nameColumn = columnMapping["name"] ?: 0
            val numberColumn = columnMapping["inventoryNumber"] ?: 7
            
            val nameCell = row.getCell(nameColumn)
            val numberCell = row.getCell(numberColumn)
            
            val name = extractCellValue(nameCell)
            var number = extractCellValue(numberCell)
            
            // Пропускаем пустые строки
            if (name.isEmpty() && number.isEmpty()) return null
            if (name == "1" && number.isEmpty()) return null
            
            // Очистка и валидация номера
            number = cleanInventoryNumber(number)
            
            // Если номер пустой, но есть название, генерируем временный номер
            if (number.isEmpty() && name.isNotEmpty()) {
                number = "TEMP_${System.currentTimeMillis()}_${row.rowNum}"
                Log.w("ExcelImporter", "Строка ${row.rowNum}: нет инвентарного номера, использую временный: $number")
            }
            
            // Если название пустое, но есть номер, создаем базовое название
            val itemName = if (name.isEmpty()) {
                "Предмет №$number"
            } else {
                // Исправляем проблему с "1" в названии
                if (name == "1" && number.isNotEmpty()) {
                    "Предмет №$number"
                } else {
                    name.trim()
                }
            }
            
            return InventoryItem(
                inventoryNumber = number,
                name = itemName,
                department = "",
                scanned = false,
                scanTimestamp = null,
                qrData = number,
                comment = "Импортировано из Excel"
            )
            
        } catch (e: Exception) {
            Log.e("ExcelImporter", "Ошибка парсинга строки ${row.rowNum}: ${e.message}")
            return null
        }
    }
    
    // ★★★★ ОЧИСТКА ИНВЕНТАРНОГО НОМЕРА ★★★★
    private fun cleanInventoryNumber(number: String): String {
        return number
            .replace(",", "")
            .replace(" ", "")
            .replace("\u00A0", "") // неразрывный пробел
            .replace("\"", "") // кавычки
            .replace("'", "") // апострофы
            .replace("\\s+".toRegex(), "") // все пробелы
            .trim()
            .takeIf { it.isNotEmpty() && it != "0" && it != "1" } ?: ""
    }
    
    // ★★★★ ВАЛИДАЦИЯ ДАННЫХ ★★★★
    private fun validateImportedData(items: List<InventoryItem>) {
        Log.d("ExcelImporter", "=== ВАЛИДАЦИЯ ДАННЫХ ===")
        
        val invalidItems = mutableListOf<InventoryItem>()
        val duplicateNumbers = mutableMapOf<String, Int>()
        
        items.forEach { item ->
            // Проверка на пустые номера
            if (item.inventoryNumber.isEmpty() || item.inventoryNumber.startsWith("TEMP_")) {
                invalidItems.add(item)
                Log.w("ExcelImporter", "Невалидный номер: ${item.name} - ${item.inventoryNumber}")
            }
            
            // Проверка на дубликаты
            duplicateNumbers[item.inventoryNumber] = duplicateNumbers.getOrDefault(item.inventoryNumber, 0) + 1
            
            // Проверка на минимальную длину названия
            if (item.name.length < 2) {
                Log.w("ExcelImporter", "Слишком короткое название: ${item.inventoryNumber} - '${item.name}'")
            }
        }
        
        // Находим дубликаты
        val duplicates = duplicateNumbers.filter { it.value > 1 }
        if (duplicates.isNotEmpty()) {
            Log.w("ExcelImporter", "Найдены дубликаты инвентарных номеров:")
            duplicates.forEach { (number, count) ->
                Log.w("ExcelImporter", "  $number: $count раз")
            }
        }
        
        Log.d("ExcelImporter", "Статистика:")
        Log.d("ExcelImporter", "  Всего записей: ${items.size}")
        Log.d("ExcelImporter", "  Невалидных: ${invalidItems.size}")
        Log.d("ExcelImporter", "  Дубликатов: ${duplicates.size}")
        
        // Логируем примеры невалидных данных
        if (invalidItems.isNotEmpty()) {
            Log.d("ExcelImporter", "Примеры невалидных записей:")
            invalidItems.take(5).forEachIndexed { index, item ->
                Log.d("ExcelImporter", "  ${index + 1}. ${item.inventoryNumber} - '${item.name}'")
            }
        }
    }
    
    // ★★★★ ИЗВЛЕЧЕНИЕ ЗНАЧЕНИЯ ИЗ ЯЧЕЙКИ ★★★★
    private fun extractCellValue(cell: org.apache.poi.ss.usermodel.Cell?): String {
        if (cell == null) return ""
        
        return try {
            when (cell.cellType) {
                CellType.STRING -> {
                    val value = cell.stringCellValue?.trim() ?: ""
                    // Исправляем проблему с кавычками
                    value.replace("\"\"", "\"").trim()
                }
                CellType.NUMERIC -> {
                    if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                        cell.dateCellValue?.toString() ?: ""
                    } else {
                        val num = cell.numericCellValue
                        if (num % 1 == 0.0) {
                            num.toLong().toString()
                        } else {
                            // Для очень больших чисел используем строковое представление
                            if (num > 1e12) {
                                java.text.NumberFormat.getInstance().apply {
                                    isGroupingUsed = false
                                }.format(num)
                            } else {
                                num.toString()
                            }
                        }
                    }
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> {
                    // Пытаемся получить результат формулы
                    try {
                        when (cell.cachedFormulaResultType) {
                            CellType.STRING -> cell.stringCellValue?.trim() ?: ""
                            CellType.NUMERIC -> cell.numericCellValue.toString()
                            else -> cell.toString().trim()
                        }
                    } catch (e: Exception) {
                        cell.toString().trim()
                    }
                }
                else -> cell.toString().trim()
            }
        } catch (e: Exception) {
            Log.w("ExcelImporter", "Ошибка чтения ячейки: ${e.message}")
            cell.toString().trim()
        }
    }
    
    // ★★★★ ПРОВЕРКА ТИПА ФАЙЛА ★★★★
    fun isExcelFile(fileName: String): Boolean {
        return fileName.endsWith(".xls", ignoreCase = true) ||
               fileName.endsWith(".xlsx", ignoreCase = true) ||
               fileName.endsWith(".xlsm", ignoreCase = true)
    }
    
    // ★★★★ ДЕБАГ ФУНКЦИЯ ★★★★
    suspend fun debugFile(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            Log.d("ExcelImporter", "=== ПОДРОБНЫЙ ДЕБАГ EXCEL ФАЙЛА ===")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                
                // Выводим больше информации
                Log.d("ExcelImporter", "Общая информация:")
                Log.d("ExcelImporter", "  Листов: ${workbook.numberOfSheets}")
                Log.d("ExcelImporter", "  Строк в листе 1: ${sheet.lastRowNum + 1}")
                
                // Анализируем первые 15 строк
                Log.d("ExcelImporter", "=== АНАЛИЗ ПЕРВЫХ 15 СТРОК ===")
                for (rowIndex in 0..minOf(14, sheet.lastRowNum)) {
                    val row = sheet.getRow(rowIndex)
                    if (row != null) {
                        Log.d("ExcelImporter", "--- Строка $rowIndex ---")
                        for (cellIndex in 0 until minOf(8, row.lastCellNum.toInt())) {
                            val cell = row.getCell(cellIndex)
                            val cellType = cell?.cellType?.toString() ?: "NULL"
                            val value = extractCellValue(cell)
                            Log.d("ExcelImporter", "  Колонка $cellIndex ($cellType): '$value'")
                        }
                    }
                }
                
                workbook.close()
            }
            
        } catch (e: Exception) {
            Log.e("ExcelImporter", "Ошибка при отладке: ${e.message}")
        }
    }
}