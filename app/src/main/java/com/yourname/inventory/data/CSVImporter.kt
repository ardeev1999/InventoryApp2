package com.yourname.inventory.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class CSVImporter(private val context: Context) {
    
    suspend fun importFrom1C(uri: Uri): List<InventoryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<InventoryItem>()
        
        try {
            System.err.println("=== CSV IMPORT STARTED ===")
            Log.d("CSVImporter", "Importing from URI: $uri")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "Windows-1251")).use { reader ->
                    var lineNumber = 0
                    var itemsFound = 0
                    
                    reader.forEachLine { line ->
                        lineNumber++
                        
                        // Пропускаем пустые строки
                        if (line.trim().isEmpty()) {
                            System.err.println("Line $lineNumber: EMPTY - skipped")
                            return@forEachLine
                        }
                        
                        // Разделяем по точке с запятой
                        val columns = line.split(";")
                        
                        // В вашем формате: название в колонке 0, номер в колонке 7 (если считать с 0)
                        val name = columns.getOrNull(0)?.trim() ?: ""
                        var inventoryNumber = columns.getOrNull(7)?.trim() ?: ""
                        
                        System.err.println("Line $lineNumber: name='$name', number='$inventoryNumber'")
                        
                        // Пропускаем заголовки и строки без номера
                        if (name.isEmpty() || inventoryNumber.isEmpty()) {
                            System.err.println("Line $lineNumber: SKIP - no name or number")
                            return@forEachLine
                        }
                        
                        // Пропускаем строку заголовка
                        if (name == "Основное средство") {
                            System.err.println("Line $lineNumber: SKIP - header")
                            return@forEachLine
                        }
                        
                        // Конвертируем научную нотацию (1,11E+12 -> 1110000000000)
                        inventoryNumber = convertScientificNotation(inventoryNumber)
                        
                        // Пропускаем если после конвертации номер пустой
                        if (inventoryNumber.isEmpty()) {
                            System.err.println("Line $lineNumber: SKIP - empty after conversion")
                            return@forEachLine
                        }
                        
                        // Создаем объект
                        val item = InventoryItem(
                            inventoryNumber = inventoryNumber,
                            name = name,
                            qrData = inventoryNumber
                        )
                        
                        items.add(item)
                        itemsFound++
                        
                        System.err.println("Line $lineNumber: ADDED - $inventoryNumber - $name")
                        
                        if (itemsFound <= 10) { // Логируем первые 10 для проверки
                            Log.d("CSVImporter", "Item $itemsFound: $inventoryNumber - $name")
                        }
                    }
                    
                    System.err.println("=== IMPORT COMPLETE ===")
                    Log.d("CSVImporter", "Processed $lineNumber lines, found $itemsFound items")
                }
            } ?: run {
                System.err.println("=== ERROR: Cannot open input stream ===")
                Log.e("CSVImporter", "Cannot open input stream for URI: $uri")
            }
            
        } catch (e: Exception) {
            System.err.println("=== IMPORT ERROR: ${e.message} ===")
            e.printStackTrace()
            Log.e("CSVImporter", "Import error: ${e.message}", e)
        }
        
        return@withContext items
    }
    
    private fun convertScientificNotation(numberStr: String): String {
        return try {
            // Если строка пустая
            if (numberStr.isEmpty()) return ""
            
            // Если число в научной нотации (содержит E)
            if (numberStr.contains("E", ignoreCase = true)) {
                // Заменяем запятую на точку для парсинга (1,11E+12 -> 1.11E+12)
                val cleanStr = numberStr.replace(",", ".")
                
                // Парсим как Double
                val number = cleanStr.toDouble()
                
                // Конвертируем в целое число без десятичной части
                String.format("%.0f", number)
            } else {
                // Убираем все пробелы и нецифровые символы (кроме минуса)
                numberStr.replace(Regex("[^\\d-]"), "")
            }
        } catch (e: Exception) {
            System.err.println("Conversion error for '$numberStr': ${e.message}")
            numberStr // Если не удалось - возвращаем как есть
        }
    }
    
    // Альтернативная упрощенная функция импорта
    suspend fun importSimpleCSV(uri: Uri): List<InventoryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<InventoryItem>()
        
        try {
            System.err.println("=== SIMPLE CSV IMPORT ===")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "Windows-1251")).use { reader ->
                    reader.forEachLine { line ->
                        if (line.trim().isEmpty()) return@forEachLine
                        
                        // Ищем последнюю точку с запятой
                        val lastSemicolon = line.lastIndexOf(";")
                        if (lastSemicolon == -1) return@forEachLine
                        
                        val name = line.substring(0, lastSemicolon).trim()
                        val number = line.substring(lastSemicolon + 1).trim()
                        
                        if (name.isNotEmpty() && number.isNotEmpty()) {
                            val cleanNumber = convertScientificNotation(number)
                            if (cleanNumber.isNotEmpty() && name != "Основное средство") {
                                items.add(InventoryItem(
                                    inventoryNumber = cleanNumber,
                                    name = name,
                                    qrData = cleanNumber
                                ))
                            }
                        }
                    }
                }
            }
            
            System.err.println("SIMPLE IMPORT: Found ${items.size} items")
            
        } catch (e: Exception) {
            System.err.println("SIMPLE IMPORT ERROR: ${e.message}")
        }
        
        return@withContext items
    }
}