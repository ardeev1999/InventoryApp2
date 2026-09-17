package com.yourname.inventoryapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yourname.inventory.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventoryImportTest {
    @Test fun barcodeLookupReimportResetAndDuplicateScans() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        try {
            val dao = db.inventoryDao()
            val first = InventoryItem("2000000219448", "Моноблок", "4050920230000000010")
            val second = InventoryItem("2000000244198", "Стул")
            val third = InventoryItem("2000000244204", "Стол")
            dao.importInventory(listOf(first, second, third), false)
            assertNull(dao.scan(first.inventoryNumber))
            assertFalse(dao.scan(first.barcode)!!.scanned)
            val timestamp = dao.getItemByBarcode(first.barcode)!!.scanTimestamp
            assertTrue(dao.scan(first.barcode)!!.scanned)
            assertEquals(timestamp, dao.getItemByBarcode(first.barcode)!!.scanTimestamp)
            assertEquals(1, dao.getAllItemsSync().count { it.scanned })
            dao.scan(second.barcode)
            assertFalse(dao.getItemByBarcode(third.barcode)!!.scanned)
            dao.importInventory(listOf(first.copy(name = "Новое название")), false)
            assertTrue(dao.getItemByBarcode(first.barcode)!!.scanned)
            assertEquals(3, dao.getAllItemsSync().size)
            try {
                dao.importInventory(listOf(first, first.copy(name = "Конфликт")), true)
                fail("Duplicate must fail before replacement")
            } catch (_: IllegalArgumentException) { }
            assertEquals(3, dao.getAllItemsSync().size)
            dao.importInventory(listOf(first), true)
            assertEquals(1, dao.getAllItemsSync().size)
            dao.clearAll()
            assertTrue(dao.getAllItemsSync().isEmpty())
            assertTrue(dao.getLegacyItems().isEmpty())
        } finally { db.close() }
    }
    @Test fun migratesVersionTwoWithoutDeletingOldRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE items (inventoryNumber TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, department TEXT NOT NULL, scanned INTEGER NOT NULL, scanTimestamp INTEGER, qrData TEXT NOT NULL, comment TEXT NOT NULL)")
            old.execSQL("INSERT INTO items VALUES ('00012', 'Стул', '', 1, 123, '00012', '')")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, InventoryDatabase::class.java, name)
            .addMigrations(InventoryDatabase.MIGRATION_2_3).build()
        try {
            val dao = db.inventoryDao()
            assertEquals(1, dao.getLegacyItems().size)
            assertTrue(dao.getAllItemsSync().isEmpty())
            dao.importInventory(listOf(InventoryItem("2000000244198", "Стул", "00012")), false)
            assertTrue(dao.getItemByBarcode("2000000244198")!!.scanned)
            assertNull(dao.scan("00012"))
            dao.clearAll()
            assertTrue(dao.getLegacyItems().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
