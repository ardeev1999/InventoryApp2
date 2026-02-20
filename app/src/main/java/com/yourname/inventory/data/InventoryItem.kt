package com.yourname.inventory.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "items")
data class InventoryItem(
    @PrimaryKey
    val inventoryNumber: String,
    val name: String,
    var department: String = "",
    var scanned: Boolean = false,
    var scanTimestamp: Date? = null,
    var qrData: String = "", // То же что inventoryNumber
    var comment: String = ""
)