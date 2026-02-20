package com.yourname.inventory.data

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Date


class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    
    // ========== ИНИЦИАЛИЗАЦИЯ БАЗЫ ДАННЫХ ==========
    private val database by lazy { InventoryDatabase.getDatabase(application) }
    private val inventoryDao by lazy { database.inventoryDao() }
    
    // ========== ИНИЦИАЛИЗАЦИЯ ИМПОРТЕРОВ ==========
    private val csvImporter by lazy { CSVImporter(application.applicationContext) }
    private val excelImporter by lazy { ExcelImporter(application.applicationContext) }
    
    // ========== LIVE DATA ДЛЯ UI ==========
    private val _stats = MutableLiveData<InventoryStats>()
    val stats: LiveData<InventoryStats> = _stats
    
    private val _importStatus = MutableLiveData<String>()
    val importStatus: LiveData<String> = _importStatus
    
    private val _importedItems = MutableLiveData<List<InventoryItem>>()
    val importedItems: LiveData<List<InventoryItem>> = _importedItems
    
    private val _currentItem = MutableLiveData<InventoryItem?>()
    val currentItem: LiveData<InventoryItem?> = _currentItem
    
    // ========== LIVE DATA ДЛЯ СПИСКОВ ==========
    val allItems by lazy { inventoryDao.getAllItems() }
    val unscannedItems by lazy { inventoryDao.getUnscannedItems() }
    val scannedItems by lazy { inventoryDao.getScannedItems() }
    
    // ========== ИНИЦИАЛИЗАЦИЯ ==========
    init {
        updateStats()
    }
    
    // ========== МЕТОДЫ ДЛЯ СТАТИСТИКИ ==========
    fun updateStats() {
        viewModelScope.launch {
            val total = inventoryDao.getTotalCount()
            val scanned = inventoryDao.getScannedCount()
            val remaining = inventoryDao.getRemainingCount()
            
            _stats.value = InventoryStats(
                total = total,
                found = scanned,
                remaining = remaining
            )
        }
    }
    
    // ========== МЕТОДЫ ИМПОРТА ФАЙЛОВ ==========
    
    /**
     * Основной метод импорта файлов
     */
    fun importFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                _importStatus.value = "Импорт файла..."
                
                // Сначала дебагим файл (выводим структуру в логи)
                excelImporter.debugFile(uri)
                
                // Затем импортируем данные
                val items = if (excelImporter.isExcelFile(fileName)) {
                    excelImporter.importFromExcel(uri)
                } else {
                    csvImporter.importFrom1C(uri)
                }
                
                if (items.isEmpty()) {
                    _importStatus.value = "Ошибка: Не найдено данных для импорта"
                    return@launch
                }
                
                // Сохраняем в базу
                saveImportedItems(items)
                
                _importStatus.value = "Успешно импортировано ${items.size} записей"
                
            } catch (e: Exception) {
                Log.e("Import", "Ошибка импорта: ${e.message}", e)
                _importStatus.value = "Ошибка импорта: ${e.message}"
            }
        }
    }
    
    /**
     * Метод для отладки импорта (показывает содержимое файла)
     */
    fun debugImportFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _importStatus.value = "Отладка импорта файла: $fileName..."
            Log.d("InventoryViewModel", "=== НАЧАЛО ОТЛАДКИ ИМПОРТА ===")
            
            // Сначала запускаем отладку
            if (excelImporter.isExcelFile(fileName)) {
                Log.d("InventoryViewModel", "Файл определен как Excel")
                excelImporter.debugFile(uri)
            } else {
                Log.d("InventoryViewModel", "Файл определен как CSV")
            }
            
            Log.d("InventoryViewModel", "=== ЗАВЕРШЕНИЕ ОТЛАДКИ ===")
            
            // Затем выполняем обычный импорт
            importFile(uri, fileName)
        }
    }
    
    /**
     * Сохранение импортированных данных в базу
     */
    private fun saveImportedItems(items: List<InventoryItem>) {
        viewModelScope.launch {
            try {
                // 1. Сохраняем в базу
                inventoryDao.insertAll(items)
                
                // 2. Правильно считаем статистику
                val total = items.size
                val found = items.count { it.scanned } // Будет 0 для новых импортов
                val remaining = total - found
                
                // 3. Обновляем статистику
                _stats.value = InventoryStats(
                    total = total,
                    found = found,      // 0 для новых импортов
                    remaining = remaining // равно total
                )
                
                // 4. Уведомляем
                _importStatus.value = "Успешно импортировано ${items.size} записей"
                
                Log.d("InventoryViewModel", "Импортировано $total записей")
                
            } catch (e: Exception) {
                _importStatus.value = "Ошибка сохранения: ${e.message}"
                Log.e("InventoryViewModel", "Ошибка сохранения: ${e.message}", e)
            }
        }
    }
    
    /**
     * Ручной импорт списка элементов (старый метод, оставлен для совместимости)
     */
    suspend fun importItems(items: List<InventoryItem>) {
        if (items.isNotEmpty()) {
            inventoryDao.insertAll(items)
            updateStats()
            Log.d("InventoryViewModel", "Ручной импорт ${items.size} записей")
        }
    }
    
    // ========== МЕТОДЫ ДЛЯ РАБОТЫ С ПРЕДМЕТАМИ ==========
    
    /**
     * Поиск предмета по инвентарному номеру
     */
    suspend fun getItemByNumber(inventoryNumber: String): InventoryItem? {
        return try {
            inventoryDao.getItemByNumber(inventoryNumber)
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка поиска предмета: ${e.message}")
            null
        }
    }
    
    /**
     * Установка текущего предмета для отображения
     */
    fun setCurrentItem(item: InventoryItem?) {
        _currentItem.value = item
    }
    
    /**
     * Пометка предмета как отсканированного
     */
    suspend fun markAsScanned(inventoryNumber: String, department: String? = null) {
        try {
            val item = inventoryDao.getItemByNumber(inventoryNumber)
            item?.let {
                // Если указан отдел - обновляем
                if (!department.isNullOrBlank()) {
                    val updatedItem = it.copy(
                        department = department,
                        scanned = true,
                        scanTimestamp = Date()
                    )
                    inventoryDao.insertItem(updatedItem)
                    Log.d("InventoryViewModel", "Предмет $inventoryNumber отмечен как отсканированный с отделом: $department")
                } else {
                    inventoryDao.updateScanStatus(inventoryNumber, true, Date())
                    Log.d("InventoryViewModel", "Предмет $inventoryNumber отмечен как отсканированный")
                }
                updateStats()
            } ?: run {
                Log.w("InventoryViewModel", "Предмет с номером $inventoryNumber не найден")
            }
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при отметке как отсканированного: ${e.message}")
        }
    }
    
    /**
     * Сброс статуса сканирования для предмета
     */
    suspend fun markAsUnscanned(inventoryNumber: String) {
        try {
            inventoryDao.updateScanStatus(inventoryNumber, false, null)
            updateStats()
            Log.d("InventoryViewModel", "Предмет $inventoryNumber сброшен в неотсканированный")
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при сбросе статуса: ${e.message}")
        }
    }
    
    /**
     * Обновление отдела для предмета
     */
    suspend fun updateDepartment(inventoryNumber: String, department: String) {
        try {
            inventoryDao.updateDepartment(inventoryNumber, department)
            Log.d("InventoryViewModel", "Обновлен отдел для $inventoryNumber: $department")
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при обновлении отдела: ${e.message}")
        }
    }
    
    // ========== МЕТОДЫ ДЛЯ УПРАВЛЕНИЯ БАЗОЙ ДАННЫХ ==========
    

    /**
    * Полная очистка базы данных с правильным сбросом статистики
    */
    suspend fun clearAll() {
        try {
            // 1. Очищаем базу данных
            inventoryDao.deleteAll()
            
            // 2. ★★★★ СБРАСЫВАЕМ СТАТИСТИКУ ★★★★
            _stats.value = InventoryStats(total = 0, found = 0, remaining = 0)
            
            // 3. Сбрасываем все LiveData
            _importedItems.value = emptyList()
            _currentItem.value = null
            
            // 4. Уведомляем пользователя
            _importStatus.value = "База данных очищена. Статистика сброшена."
            
            Log.i("InventoryViewModel", "База данных полностью очищена")
            
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при очистке базы данных: ${e.message}")
            _importStatus.value = "Ошибка при очистке: ${e.message}"
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            clearAll()  // Вызываем suspend версию
        }
    }


    /**
     * Удаление конкретного предмета
     */
    suspend fun deleteItem(inventoryNumber: String) {
        try {
            inventoryDao.deleteByNumber(inventoryNumber)
            updateStats()
            Log.d("InventoryViewModel", "Удален предмет: $inventoryNumber")
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при удалении предмета: ${e.message}")
        }
    }
    
    /**
     * Экспорт данных (заглушка для будущей реализации)
     */
    suspend fun exportData(): List<InventoryItem> {
        return try {
            inventoryDao.getAllItemsSync()
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при экспорте данных: ${e.message}")
            emptyList()
        }
    }
    
    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========
    
    /**
     * Проверка существования предмета в базе
     */
    suspend fun itemExists(inventoryNumber: String): Boolean {
        return try {
            inventoryDao.getItemByNumber(inventoryNumber) != null
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при проверке существования: ${e.message}")
            false
        }
    }
    
    /**
     * Получение количества предметов (синхронно)
     */
    suspend fun getItemCount(): Int {
        return try {
            inventoryDao.getTotalCount()
        } catch (e: Exception) {
            Log.e("InventoryViewModel", "Ошибка при подсчете предметов: ${e.message}")
            0
        }
    }
    
    // ========== DATA CLASSES ==========
    
    /**
     * Статистика инвентаризации
     */
    data class InventoryStats(
        val total: Int = 0,
        val found: Int = 0,
        val remaining: Int = 0
    ) {
        /**
         * Процент завершенности
         */
        val completionPercentage: Int
            get() = if (total > 0) (found * 100 / total) else 0
    }
    
    /**
     * Состояние импорта
     */
    enum class ImportState {
        IDLE,
        LOADING,
        SUCCESS,
        ERROR,
        EMPTY
    }
}