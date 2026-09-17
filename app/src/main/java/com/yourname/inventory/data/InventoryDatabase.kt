package com.yourname.inventory.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [InventoryItem::class, LegacyInventoryItem::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventoryDao(): InventoryDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS inventory_items (barcode TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, inventoryNumber TEXT NOT NULL, scanned INTEGER NOT NULL, scanTimestamp INTEGER)")
            }
        }
        @Volatile private var instance: InventoryDatabase? = null
        fun getDatabase(context: Context): InventoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext,
                InventoryDatabase::class.java, "inventory_database")
                .addMigrations(MIGRATION_2_3).build().also { instance = it }
        }
    }
}
