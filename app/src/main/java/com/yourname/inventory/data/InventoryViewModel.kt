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
    private val _pendingImport = MutableLiveData<StatementParser.Result?>()
    val pendingImport: LiveData<StatementParser.Result?> = _pendingImport
    private var importBusy = false

    fun importFile(uri: Uri, fileName: String) {
        if (importBusy || _pendingImport.value != null) return
        importBusy = true
        viewModelScope.launch {
            try {
                _importStatus.value = "Чтение ведомости..."
                val result = if (excelImporter.isExcelFile(fileName)) {
                    excelImporter.importFromExcel(uri)
                } else {
                    StatementParser.Result(csvImporter.importFrom1C(uri), emptyList(), "CSV")
                }
                if (result.items.isEmpty()) {
                    _importStatus.value = "Не найдено данных для импорта. " + result.warnings.take(3).joinToString("\n")
                } else {
                    _pendingImport.value = result
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _importStatus.value = "Ошибка импорта: ${e.message}"
            } finally {
                importBusy = false
            }
        }
    }

    fun cancelPendingImport() { _pendingImport.value = null }

    fun confirmPendingImport(replaceExisting: Boolean) {
        val result = _pendingImport.value ?: return
        if (importBusy) return
        _pendingImport.value = null
        importBusy = true
        viewModelScope.launch {
            try {
                saveImportedItems(result.items, replaceExisting)
                _importStatus.value = "Импортировано ${result.items.size} предметов. Пропущено проблемных строк: ${result.warnings.size}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _importStatus.value = "Ошибка сохранения: ${e.message}"
            } finally {
                importBusy = false
            }
        }
    }

    private suspend fun saveImportedItems(items: List<InventoryItem>, replaceExisting: Boolean) {
        inventoryDao.importInventory(items, replaceExisting)
        _stats.value = InventoryStats(
            total = inventoryDao.getTotalCount(),
            found = inventoryDao.getScannedCount(),
            remaining = inventoryDao.getRemainingCount()
        )
        _importedItems.value = items
    }

    suspend fun getItemsByCode(code: String): List<InventoryItem> = inventoryDao.getItemsByCode(code)

    suspend fun bindingCandidates(): List<InventoryItem> = inventoryDao.getAllItemsSync()

    suspend fun bindCode(number: String, code: String) {
        inventoryDao.bindCode(number, code)
        updateStats()
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
        val item = inventoryDao.getItemByNumber(inventoryNumber)
            ?: error("Предмет больше не существует")
        if (!department.isNullOrBlank()) {
            inventoryDao.insertItem(item.copy(department = department, scanned = true, scanTimestamp = Date()))
        } else {
            inventoryDao.updateScanStatus(inventoryNumber, true, Date())
        }
        updateStats()
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