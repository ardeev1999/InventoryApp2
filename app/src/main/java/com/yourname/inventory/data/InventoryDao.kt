package com.yourname.inventory.data

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.Date

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items ORDER BY name, inventoryNumber, barcode")
    fun observeItems(): LiveData<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items ORDER BY name, inventoryNumber, barcode")
    suspend fun getAllItemsSync(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE barcode = :code")
    suspend fun getItemByBarcode(code: String): InventoryItem?

    @Query("SELECT * FROM items")
    suspend fun getLegacyItems(): List<LegacyInventoryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItem>)

    @Query("UPDATE inventory_items SET scanned = 1, scanTimestamp = :timestamp WHERE barcode = :barcode AND scanned = 0")
    suspend fun markScanned(barcode: String, timestamp: Date): Int

    @Transaction
    suspend fun scan(code: String): InventoryItem? {
        val item = getItemByBarcode(code) ?: return null
        markScanned(code, Date())
        return item
    }

    @Transaction
    suspend fun importInventory(items: List<InventoryItem>, replaceExisting: Boolean) {
        require(items.isNotEmpty()) { "Нет предметов для импорта" }
        require(items.all { it.barcode.isNotBlank() && it.name.isNotBlank() }) { "Не заполнены обязательные поля" }
        require(items.map { it.barcode }.toSet().size == items.size) { "Повторяющиеся штрихкоды" }
        val previous = getAllItemsSync().associateBy { it.barcode }
        val legacy = getLegacyItems()
        val legacyCodes = legacy.filter { it.qrData.isNotEmpty() && it.qrData != it.inventoryNumber }.groupBy { it.qrData }
        val legacyNumbers = legacy.groupBy { it.inventoryNumber }
        val incomingNumbers = items.filter { it.inventoryNumber.isNotEmpty() }.groupBy { it.inventoryNumber }
        val merged = items.map { item ->
            val old = previous[item.barcode]
            val archived = legacyCodes[item.barcode]?.singleOrNull()
                ?: if (incomingNumbers[item.inventoryNumber]?.size == 1)
                    legacyNumbers[item.inventoryNumber]?.singleOrNull()?.takeIf { it.name.trim() == item.name }
                else null
            item.copy(scanned = old?.scanned ?: archived?.scanned ?: false,
                scanTimestamp = old?.scanTimestamp ?: archived?.scanTimestamp)
        }
        if (replaceExisting) deleteItems()
        insertAll(merged)
    }

    @Query("DELETE FROM inventory_items")
    suspend fun deleteItems()
    @Query("DELETE FROM items")
    suspend fun deleteLegacyItems()
    @Transaction
    suspend fun clearAll() { deleteItems(); deleteLegacyItems() }
}
