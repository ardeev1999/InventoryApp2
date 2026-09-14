package com.yourname.inventory.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory

class ExcelImporter(private val context: Context) {
    suspend fun importFromExcel(uri: Uri): StatementParser.Result = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть файл")
        stream.use { input ->
            WorkbookFactory.create(input).use { workbook ->
                StatementParser().parseReport(workbook)
            }
        }
    }

    fun isExcelFile(fileName: String): Boolean =
        listOf(".xls", ".xlsx", ".xlsm").any { fileName.endsWith(it, ignoreCase = true) }
}
