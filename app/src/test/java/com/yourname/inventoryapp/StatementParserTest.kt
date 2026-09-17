package com.yourname.inventoryapp

import com.yourname.inventory.data.StatementParser
import com.yourname.inventory.data.InventoryItem
import com.yourname.inventory.data.InventoryExporter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class StatementParserTest {
    private fun fixture(workbook: Workbook): Workbook {
        val sheet = workbook.createSheet("Выгрузка")
        sheet.createRow(0).createCell(0).setCellValue("Отчёт 1С")
        sheet.createRow(8).apply {
            createCell(1).setCellValue("Основное средство")
            createCell(2).setCellValue("Инвентарный номер")
            createCell(3).setCellValue("Штрихкод")
        }
        fun row(n: Int, name: String, inventory: String?, barcode: String?) {
            sheet.createRow(n).apply {
                createCell(1).setCellValue(name)
                inventory?.let { createCell(2).setCellValue(it) }
                barcode?.let { createCell(3).setCellValue(it) }
            }
        }
        row(9, "Моноблок", "4050920230000000010", "2000000219448")
        row(10, "Без кода", "123", null)
        row(11, "Стул", null, "2000000244198")
        row(12, "Стол", "", "2000000244204")
        row(13, "С нулями", "00012", "0012345678905")
        row(14, "Ещё один", "00012", "2000000244211")
        return workbook
    }
    @Test fun importsByBarcodeWithMissingAndDuplicateInventoryNumbers() {
        for (workbook in listOf(HSSFWorkbook(), XSSFWorkbook())) workbook.use {
            val result = StatementParser().parseReport(fixture(it))
            assertEquals(5, result.items.size)
            assertEquals(1, result.skippedWithoutBarcode)
            assertEquals(2, result.withoutInventoryNumber)
            assertEquals("4050920230000000010", result.items[0].inventoryNumber)
            assertEquals("2000000219448", result.items[0].barcode)
            assertEquals("Без инвентарного номера", result.items[1].displayInventoryNumber)
            assertEquals("0012345678905", result.items[3].barcode)
            assertEquals(2, result.items.count { it.inventoryNumber == "00012" })
        }
    }
    @Test fun inlineStringsAreNotTreatedAsEmptyCells() {
        XSSFWorkbook().use { workbook ->
            fixture(workbook)
            val cell = workbook.getSheetAt(0).getRow(9).getCell(3)
            cell.ctCell.unsetV()
            cell.ctCell.t = org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType.INLINE_STR
            cell.ctCell.addNewIs().t = "2000000219448"
            val result = StatementParser().parseReport(workbook)
            assertEquals(5, result.items.size)
            assertEquals("2000000219448", result.items.first().barcode)
        }
    }
    @Test fun oneCEmptyErrorCellsAreMissingValues() {
        XSSFWorkbook().use { workbook ->
            fixture(workbook)
            val sheet = workbook.getSheetAt(0)
            for ((row, column) in listOf(11 to 2, 10 to 3)) {
                val cell = sheet.getRow(row).createCell(column)
                cell.setCellErrorValue(org.apache.poi.ss.usermodel.FormulaError.NA.code)
                cell.ctCell.unsetV()
            }
            val result = StatementParser().parseReport(workbook)
            assertEquals(5, result.items.size)
            assertEquals(1, result.skippedWithoutBarcode)
            assertEquals(2, result.withoutInventoryNumber)
        }
    }
    @Test fun numericBarcodeIsPlainTextAndLongNumericInventoryIsReported() {
        HSSFWorkbook().use { workbook ->
            fixture(workbook)
            val sheet = workbook.getSheetAt(0)
            sheet.getRow(9).getCell(3).setCellValue(2000000219448.0)
            sheet.getRow(13).getCell(2).setCellValue(4.05092023e18)
            val result = StatementParser().parseReport(workbook)
            assertEquals("2000000219448", result.items.first().barcode)
            assertEquals(1, result.warnings.size)
            assertEquals(4, result.items.size)
        }
    }
    @Test fun rejectsConflictingBarcodes() {
        HSSFWorkbook().use { workbook ->
            fixture(workbook)
            workbook.getSheetAt(0).getRow(11).getCell(3).setCellValue("2000000219448")
            assertThrows(IllegalStateException::class.java) { StatementParser().parse(workbook) }
        }
    }
    @Test fun identicalDuplicateIsCountedOnce() {
        HSSFWorkbook().use { workbook ->
            fixture(workbook)
            workbook.getSheetAt(0).createRow(15).apply {
                createCell(1).setCellValue("Моноблок")
                createCell(2).setCellValue("4050920230000000010")
                createCell(3).setCellValue("2000000219448")
            }
            val result = StatementParser().parseReport(workbook)
            assertEquals(5, result.items.size)
            assertEquals(1, result.duplicateRows)
        }
    }
    @Test fun missingBarcodeColumnFailsInsteadOfGuessing() {
        HSSFWorkbook().use { workbook ->
            fixture(workbook)
            workbook.getSheetAt(0).getRow(8).removeCell(workbook.getSheetAt(0).getRow(8).getCell(3))
            assertThrows(IllegalStateException::class.java) { StatementParser().parse(workbook) }
        }
    }
    @Test fun exportSeparatesFoundAndRemainingAndKeepsIdentifiersAsStrings() {
        val items = listOf(InventoryItem("2000000219448", "Моноблок", "4050920230000000010", true),
            InventoryItem("2000000244198", "Стул"), InventoryItem("2000000244204", "Стол", "00012"))
        for (selection in InventoryExporter.Selection.values()) {
            val output = ByteArrayOutputStream()
            InventoryExporter.write(items, selection, output)
            WorkbookFactory.create(output.toByteArray().inputStream()).use { workbook ->
                assertEquals(if (selection == InventoryExporter.Selection.BOTH) 2 else 1, workbook.numberOfSheets)
                if (selection != InventoryExporter.Selection.REMAINING) {
                    val sheet = workbook.getSheet("Найденные")
                    assertEquals(1, sheet.lastRowNum)
                    assertEquals(CellType.STRING, sheet.getRow(1).getCell(1).cellType)
                    assertEquals("4050920230000000010", sheet.getRow(1).getCell(1).stringCellValue)
                }
                if (selection != InventoryExporter.Selection.FOUND) {
                    val sheet = workbook.getSheet("Оставшиеся")
                    assertEquals(2, sheet.lastRowNum)
                    assertEquals("Без инвентарного номера", sheet.getRow(1).getCell(1).stringCellValue)
                    assertEquals("00012", sheet.getRow(2).getCell(1).stringCellValue)
                }
                for (sheet in workbook) for (row in sheet) assertEquals(2, row.lastCellNum.toInt())
            }
        }
    }
    @Test fun emptyExportHasBothHeaders() {
        val output = ByteArrayOutputStream()
        InventoryExporter.write(emptyList(), InventoryExporter.Selection.BOTH, output)
        WorkbookFactory.create(output.toByteArray().inputStream()).use {
            assertEquals(2, it.numberOfSheets)
            assertEquals(0, it.getSheetAt(0).lastRowNum)
        }
    }
    @Test fun suppliedWorkbookMatchesExpectedCounts() {
        val path = System.getProperty("inventory.fixture")
        assumeTrue("Pass -Dinventory.fixture=/path/file.xlsx to check the private fixture", path != null)
        WorkbookFactory.create(File(path!!)).use {
            val result = StatementParser().parseReport(it)
            assertEquals(result.warnings.take(3).joinToString(";"), 3322, result.items.size)
            assertEquals(49, result.skippedWithoutBarcode)
            assertEquals(195, result.withoutInventoryNumber)
            assertTrue(result.warnings.isEmpty())
            assertEquals(3322, result.items.map { item -> item.barcode }.toSet().size)
        }
    }
}
