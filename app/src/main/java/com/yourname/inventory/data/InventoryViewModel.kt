package com.yourname.inventory.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = InventoryDatabase.getDatabase(application).inventoryDao()
    val allItems = dao.observeItems()
    val pendingImport = MutableLiveData<StatementParser.Result?>(null)
    val message = MutableLiveData<String?>(null)
    val busy = MutableLiveData(false)
    val hasLegacyItems = MutableLiveData(false)
    init { viewModelScope.launch { hasLegacyItems.value = dao.getLegacyItems().isNotEmpty() } }

    private fun operation(block: suspend () -> Unit) {
        if (busy.value == true) return
        busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message.value = "Ошибка: ${e.message}" }
            finally { busy.value = false }
        }
    }
    fun importFile(uri: Uri) {
        if (pendingImport.value != null) return
        operation {
            val result = ExcelImporter(getApplication()).importFromExcel(uri)
            if (result.items.isEmpty()) message.value = "Нет предметов для импорта. Без штрихкода: ${result.skippedWithoutBarcode}. Ошибок: ${result.warnings.size}. " + result.warnings.take(3).joinToString("\n")
            else pendingImport.value = result
        }
    }
    fun cancelPendingImport() { pendingImport.value = null }
    fun confirmPendingImport(replace: Boolean) {
        val result = pendingImport.value ?: return
        if (busy.value == true) return
        pendingImport.value = null
        operation {
            dao.importInventory(result.items, replace)
            message.value = "Импортировано: ${result.items.size}. Без штрихкода пропущено: ${result.skippedWithoutBarcode}. Без инвентарного номера: ${result.withoutInventoryNumber}. Ошибок: ${result.warnings.size}."
        }
    }
    fun clearDatabase() {
        if (pendingImport.value != null) return
        operation { dao.clearAll(); hasLegacyItems.value = false; message.value = "База данных очищена" }
    }
    suspend fun scan(code: String): InventoryItem? = dao.scan(code)

    fun export(uri: Uri, selection: InventoryExporter.Selection) = operation {
        val items = dao.getAllItemsSync()
        withContext(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            val output = resolver.openOutputStream(uri, "wt") ?: error("Не удалось открыть файл для записи")
            output.use { InventoryExporter.write(items, selection, it) }
        }
        message.value = "Экспорт завершён"
    }
}
