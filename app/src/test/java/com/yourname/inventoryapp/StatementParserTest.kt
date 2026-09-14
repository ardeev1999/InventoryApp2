package com.yourname.inventoryapp

import com.yourname.inventory.data.StatementParser
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class StatementParserTest {
    @Test fun fullStatementSkipsHeadingGroupsAndTotals() {
        HSSFWorkbook().use { book ->
            val sheet = book.createSheet("Ведомость")
            sheet.createRow(1).createCell(0).setCellValue("Ведомость остатков ОС")
            sheet.createRow(8).apply {
                createCell(2).setCellValue("Основное средство")
                createCell(9).setCellValue("Инвентарный номер")
            }
            sheet.createRow(9).apply {
                createCell(0).setCellValue("Сотрудник")
                createCell(11).setCellValue(100.0)
            }
            sheet.createRow(14).apply {
                createCell(0).setCellValue("1")
                createCell(2).setCellValue("Моноблок")
                createCell(9).setCellValue("4050920230000000010")
            }
            sheet.createRow(15).apply {
                createCell(0).setCellValue("Итого")
                createCell(11).setCellValue(100.0)
            }
            val items = StatementParser().parse(book)
            assertEquals(1, items.size)
            assertEquals("4050920230000000010", items.single().inventoryNumber)
            assertEquals("Моноблок", items.single().name)
        }
    }

    @Test fun headerlessStatementKeepsFirstRowAndUsesColumnJ() {
        HSSFWorkbook().use { book ->
            book.createSheet().createRow(0).apply {
                createCell(0).setCellValue("2")
                createCell(2).setCellValue("Лифт")
                createCell(9).setCellValue("04142915263618")
            }
            assertEquals("04142915263618", StatementParser().parse(book).single().inventoryNumber)
        }
    }

    @Test fun compactSheetPreservesShortIdentifiersAndFormatting() {
        HSSFWorkbook().use { book ->
            val sheet = book.createSheet()
            for ((index, number) in listOf("0", "1", "0012", "AB-12", "1234").withIndex()) {
                sheet.createRow(index).apply {
                    createCell(0).setCellValue("Предмет $index")
                    createCell(1).setCellValue(number)
                }
            }
            sheet.createRow(5).apply {
                createCell(0).setCellValue("Числовая ячейка")
                createCell(1).apply {
                    setCellValue(12.0)
                    setCellStyle(book.createCellStyle().apply { dataFormat = book.createDataFormat().getFormat("00000") })
                }
            }
            sheet.createRow(6).apply {
                createCell(0).setCellValue("Формула")
                createCell(1).cellFormula = "1000+235"
            }
            book.creationHelper.createFormulaEvaluator().evaluateAll()
            assertEquals(listOf("0", "1", "0012", "AB-12", "1234", "00012", "1235"),
                StatementParser().parse(book).map { it.inventoryNumber })
        }
    }

    @Test fun firstRecognizedSheetIsNotCombinedWithItsCopy() {
        HSSFWorkbook().use { book ->
            for (name in listOf("Исходная", "Копия")) {
                book.createSheet(name).createRow(0).apply {
                    createCell(0).setCellValue("Стул")
                    createCell(1).setCellValue("0012")
                }
            }
            assertEquals(1, StatementParser().parse(book).size)
        }
    }

    @Test fun numericLongIdentifierIsRejectedInsteadOfRounded() {
        HSSFWorkbook().use { book ->
            book.createSheet().createRow(0).apply {
                createCell(0).setCellValue("Предмет")
                createCell(1).setCellValue(4050920230000000010.0)
            }
            val report = StatementParser().parseReport(book)
            assertTrue(report.items.isEmpty())
            assertEquals(1, report.warnings.size)
        }
    }

    @Test fun actualStatementsWhenProvidedLocally() {
        val folder = System.getProperty("inventory.fixtures")
        assumeTrue(folder != null)
        val cases = listOf("1.xls" to 1200, "Ведомость остатков ОС на 10.09.2025г..xls" to 1201)
        for ((filename, count) in cases) {
            WorkbookFactory.create(File(folder!!, filename)).use { book ->
                val report = StatementParser().parseReport(book)
                assertEquals(3, report.warnings.size)
                val items = report.items
                assertEquals(filename, count, items.size)
                assertEquals("Компьютер персональный настольный (моноблок)",
                    items.single { it.inventoryNumber == "4050920230000000010" }.name)
                assertTrue(items.any { it.inventoryNumber == "4020920230000000014" })
                assertTrue(items.any { it.inventoryNumber == "04142915263618" })
                assertFalse(items.any { it.inventoryNumber.startsWith("TEMP_") })
                book.removeSheetAt(0)
                val compact = StatementParser().parseReport(book)
                assertEquals(1200, compact.items.size)
                assertEquals(2, compact.warnings.size)
                assertEquals("04142915263618", compact.items.first().inventoryNumber)
            }
        }
    }
}
