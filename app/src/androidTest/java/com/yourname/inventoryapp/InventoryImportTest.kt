package com.yourname.inventoryapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yourname.inventory.data.InventoryDatabase
import com.yourname.inventory.data.InventoryItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InventoryImportTest {
    @Test fun importAndBindingPreserveIdentityAndRejectConflicts() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java).build()
        try {
            val dao = db.inventoryDao()
            val item = InventoryItem("0012", "Моноблок", qrData = "0012")
            dao.importInventory(listOf(item, InventoryItem("1234", "Стул")), false)
            dao.bindCode("0012", "2000000219448")
            assertEquals("0012", dao.getItemsByCode("2000000219448").single().inventoryNumber)
            assertEquals("0012", dao.getItemsByCode("0012").single().inventoryNumber)
            dao.importInventory(listOf(item.copy(name = "Новое название")), true)
            assertNull(dao.getItemByNumber("1234"))
            assertTrue(dao.getItemByNumber("0012")!!.scanned)
            assertEquals("2000000219448", dao.getItemByNumber("0012")!!.qrData)
            dao.importInventory(listOf(InventoryItem("4321", "МФУ")), false)
            try {
                dao.bindCode("4321", "2000000219448")
                fail("Duplicate binding must be rejected")
            } catch (_: IllegalArgumentException) { }
            try {
                dao.importInventory(listOf(item, InventoryItem("2000000219448", "Другой")), true)
                fail("Barcode/inventory number collision must be rejected")
            } catch (_: IllegalArgumentException) { }
            assertNotNull(dao.getItemByNumber("4321")) // transaction did not delete existing items
        } finally {
            db.close()
        }
    }
}
