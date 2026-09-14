package com.yourname.inventory.data

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.Date

@Dao
interface InventoryDao {
    @Query("SELECT * FROM items WHERE inventoryNumber = :code OR qrData = :code")
    suspend fun getItemsByCode(code: String): List<InventoryItem>

    @Query("UPDATE items SET qrData = :code WHERE inventoryNumber = :number")
    suspend fun updateCode(number: String, code: String)

    @Transaction
    suspend fun bindCode(number: String, code: String) {
        require(code.isNotEmpty()) { "Пустой код нельзя привязать" }
        require(getItemByNumber(number) != null) { "Предмет больше не существует" }
        require(getItemsByCode(code).all { it.inventoryNumber == number }) {
            "Этот код уже относится к другому предмету"
        }
        updateCode(number, code)
        updateScanStatus(number, true, Date())
    }

    @Transaction
    suspend fun importInventory(items: List<InventoryItem>, replaceExisting: Boolean) {
        require(items.isNotEmpty()) { "Файл не содержит предметов" }
        val previous = getAllItemsSync().associateBy { it.inventoryNumber }
        val incomingNumbers = items.map { it.inventoryNumber }.toSet()
        val merged = items.map { item ->
            val old = previous[item.inventoryNumber]
            // Привязка не должна перекрывать инвентарный номер нового предмета.
            require(old == null || old.qrData == item.inventoryNumber || old.qrData !in incomingNumbers) {
                "Код ${old?.qrData} уже привязан к ${item.inventoryNumber}, но в файле это номер другого предмета"
            }
            if (old == null) item else item.copy(
                qrData = old.qrData.ifEmpty { item.inventoryNumber },
                scanned = old.scanned, scanTimestamp = old.scanTimestamp,
                department = old.department, comment = old.comment
            )
        }
        if (!replaceExisting) {
            for (item in items) {
                require(previous.values.none {
                    it.inventoryNumber != item.inventoryNumber && it.qrData == item.inventoryNumber
                }) { "Номер ${item.inventoryNumber} уже используется как штрихкод другого предмета" }
            }
        }
        if (replaceExisting) deleteAll()
        insertAll(merged)
    }
    
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
