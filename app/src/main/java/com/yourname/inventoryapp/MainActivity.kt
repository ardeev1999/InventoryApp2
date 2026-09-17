package com.yourname.inventoryapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.yourname.inventory.data.InventoryViewModel
import com.yourname.inventory.data.InventoryExporter

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: InventoryViewModel
    private var importDialog: AlertDialog? = null
    private var exportSelection = InventoryExporter.Selection.BOTH
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) viewModel.export(uri, exportSelection)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel = ViewModelProvider(this)[InventoryViewModel::class.java]
        exportSelection = savedInstanceState?.getString("exportSelection")?.let {
            InventoryExporter.Selection.valueOf(it)
        } ?: InventoryExporter.Selection.BOTH
        viewModel.allItems.observe(this) { items ->
            val found = items.count { it.scanned }
            findViewById<TextView>(R.id.statsTextView).text =
                "Всего: ${items.size}\nНайдено: $found\nОсталось: ${items.size - found}"
        }
        viewModel.hasLegacyItems.observe(this) { legacy ->
            findViewById<TextView>(R.id.migrationNotice).apply {
                visibility = if (legacy) android.view.View.VISIBLE else android.view.View.GONE
                text = "Обновлён формат учёта. Загрузите Excel со столбцом «Штрихкод». Отметки старой версии переносятся для однозначно совпавших предметов."
            }
        }
        viewModel.message.observe(this) { message ->
            if (message != null) {
                findViewById<TextView>(R.id.operationStatus).text = message
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.message.value = null
            }
        }
        val actions = listOf(R.id.importButton, R.id.resetButton, R.id.exportButton, R.id.scanButton)
        viewModel.busy.observe(this) { busy ->
            actions.forEach { findViewById<Button>(it).isEnabled = !busy }
            findViewById<android.widget.ProgressBar>(R.id.progress).visibility =
                if (busy) android.view.View.VISIBLE else android.view.View.GONE
        }
        viewModel.pendingImport.observe(this) { result ->
            importDialog?.dismiss()
            importDialog = null
            if (result != null) {
                val warnings = result.warnings.take(5).joinToString("\n")
                importDialog = AlertDialog.Builder(this)
                    .setTitle("Импорт: ${result.items.size} предметов")
                    .setMessage("Лист: ${result.sheetName}\nБез штрихкода пропущено: ${result.skippedWithoutBarcode}\nБез инвентарного номера: ${result.withoutInventoryNumber}\nПовторных строк: ${result.duplicateRows}\nОшибок: ${result.warnings.size}\n$warnings\n\nОбновление сохраняет остальные предметы. Замена удаляет отсутствующие в импортируемом списке. Отметки найденного сохраняются по штрихкоду.")
                    .setPositiveButton("Обновить / добавить") { _, _ -> viewModel.confirmPendingImport(false) }
                    .setNeutralButton("Заменить список") { _, _ -> viewModel.confirmPendingImport(true) }
                    .setNegativeButton("Отмена") { _, _ -> viewModel.cancelPendingImport() }
                    .setOnCancelListener { viewModel.cancelPendingImport() }.show()
            }
        }
        findViewById<Button>(R.id.importButton).setOnClickListener {
            importFile.launch(arrayOf("application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        }
        fun list(button: Int, filter: String) {
            findViewById<Button>(button).setOnClickListener {
                startActivity(Intent(this, ItemsListActivity::class.java).putExtra("filter", filter))
            }
        }
        list(R.id.listButton, "all")
        list(R.id.scannedListButton, "found")
        list(R.id.remainingListButton, "remaining")
        findViewById<Button>(R.id.scanButton).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Очистить базу данных?")
                .setMessage("Будут удалены все предметы, отметки найденного и архив старой версии. Экспортированные файлы сохранятся.")
                .setPositiveButton("Очистить") { _, _ -> viewModel.clearDatabase() }
                .setNegativeButton("Отмена", null).show()
        }
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            AlertDialog.Builder(this).setTitle("Экспорт данных")
                .setItems(arrayOf("Найденные предметы", "Оставшиеся предметы", "Оба списка — отдельные листы")) { _, index ->
                    exportSelection = InventoryExporter.Selection.values()[index]
                    val name = when (exportSelection) {
                        InventoryExporter.Selection.FOUND -> "Найденные"
                        InventoryExporter.Selection.REMAINING -> "Оставшиеся"
                        InventoryExporter.Selection.BOTH -> "Инвентаризация"
                    }
                    exportFile.launch("$name.xlsx")
                }.setNegativeButton("Отмена", null).show()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("exportSelection", exportSelection.name)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { importDialog?.dismiss(); super.onDestroy() }
}
