package com.yourname.inventory.data

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.Date

@Dao
interface InventoryDao {
    
    // === CRUD операции ===
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItem>)
    
    @Delete
    suspend fun deleteItem(item: InventoryItem)
    
    @Query("DELETE FROM items WHERE inventoryNumber = :number")
    suspend fun deleteByNumber(number: String)
    
    @Query("DELETE FROM items")
    suspend fun deleteAll()
    
    // === Запросы с LiveData (для ViewModel) ===
    @Query("SELECT * FROM items ORDER BY name")
    fun getAllItems(): LiveData<List<InventoryItem>>
    
    @Query("SELECT * FROM items WHERE scanned = 0 ORDER BY name")
    fun getUnscannedItems(): LiveData<List<InventoryItem>>
    
    @Query("SELECT * FROM items WHERE scanned = 1 ORDER BY scanTimestamp DESC")
    fun getScannedItems(): LiveData<List<InventoryItem>>
    
    // === Синхронные запросы (для корутин) ===
    @Query("SELECT * FROM items WHERE inventoryNumber = :inventoryNumber")
    suspend fun getItemByNumber(inventoryNumber: String): InventoryItem?
    
    @Query("SELECT * FROM items")
    suspend fun getAllItemsSync(): List<InventoryItem>
    
    // === Обновление отдельных полей ===
    @Query("UPDATE items SET scanned = :scanned, scanTimestamp = :timestamp WHERE inventoryNumber = :inventoryNumber")
    suspend fun updateScanStatus(inventoryNumber: String, scanned: Boolean, timestamp: Date?)
    
    @Query("UPDATE items SET department = :department WHERE inventoryNumber = :inventoryNumber")
    suspend fun updateDepartment(inventoryNumber: String, department: String)
    
    // === Статистика ===
    @Query("SELECT COUNT(*) FROM items")
    suspend fun getTotalCount(): Int
    
    @Query("SELECT COUNT(*) FROM items WHERE scanned = 1")
    suspend fun getScannedCount(): Int
    
    @Query("SELECT COUNT(*) FROM items WHERE scanned = 0")
    suspend fun getRemainingCount(): Int
    
    // === Поиск ===
    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' OR inventoryNumber LIKE '%' || :query || '%'")
    fun searchItems(query: String): LiveData<List<InventoryItem>>
}