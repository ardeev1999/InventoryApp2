package com.yourname.inventory.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import java.util.Date

@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey val barcode: String,
    val name: String,
    val inventoryNumber: String = "",
    val scanned: Boolean = false,
    val scanTimestamp: Date? = null
) {
    @get:Ignore
    val displayInventoryNumber: String
        get() = inventoryNumber.ifBlank { "Без инвентарного номера" }
}

// Архив версии 2: сохраняется при обновлении, удаляется только кнопкой сброса.
@Entity(tableName = "items")
data class LegacyInventoryItem(
    @PrimaryKey val inventoryNumber: String,
    val name: String,
    val department: String,
    val scanned: Boolean,
    val scanTimestamp: Date?,
    val qrData: String,
    val comment: String
)
