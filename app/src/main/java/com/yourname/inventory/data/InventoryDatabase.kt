package com.yourname.inventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [InventoryItem::class],
    version = 2,  // ← Здесь 2 вместо 1
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: InventoryDatabase? = null
        
        fun getDatabase(context: Context): InventoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    "inventory_database"
                )
                .fallbackToDestructiveMigration()  // ← ДОБАВЬТЕ ЭТУ СТРОЧКУ
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}