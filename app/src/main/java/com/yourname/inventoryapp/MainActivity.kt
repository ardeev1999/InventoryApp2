package com.yourname.inventoryapp

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.yourname.inventory.data.InventoryItem  
import com.yourname.inventory.data.InventoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: InventoryViewModel
    private lateinit var statsTextView: TextView
    
    companion object {
        private const val REQUEST_CODE_PICK_CSV = 1003
        private const val REQUEST_CODE_PICK_EXCEL = 1004
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d(TAG, "=== MainActivity СТАРТУЕТ ===")

        // Инициализация ViewModel
        viewModel = ViewModelProvider(this)[InventoryViewModel::class.java]
        
        // Находим элементы интерфейса
        statsTextView = findViewById(R.id.statsTextView)
        val loadButton = findViewById<Button>(R.id.button)
        val listButton = findViewById<Button>(R.id.listButton)
        val scanButton = findViewById<Button>(R.id.scanButton)
        val importButton = findViewById<Button>(R.id.importButton)

        val scannedListButton = findViewById<Button>(R.id.scannedListButton)
        // ★★★★ ПРАВИЛЬНЫЙ ОБРАБОТЧИК ★★★★
        scannedListButton.setOnClickListener {
            // Всегда открываем ScannedItemsActivity
            // Проверка на пустоту будет внутри самой активности
            val intent = Intent(this, ScannedItemsActivity::class.java)
            startActivity(intent)
        }

        // ★★★★ ДОПОЛНИТЕЛЬНО: Наблюдение за количеством отсканированных ★★★★
        // Можно добавить для информативности
        viewModel.scannedItems.observe(this) { items ->
            val scannedCount = items?.size ?: 0
            Log.d("MainActivity", "Отсканировано предметов: $scannedCount")
            
            // Можно менять текст кнопки динамически
            scannedListButton.text = if (scannedCount > 0) {
                "📋 Список найденного ($scannedCount)"
            } else {
                "📋 Список найденного"
            }
        }
        
        // Наблюдаем за статистикой и обновляем TextView
        viewModel.stats.observe(this) { stats ->
            updateStats(stats)
        }
        
        viewModel.pendingImport.observe(this) { result ->
            if (result != null) {
                val warnings = if (result.warnings.isEmpty()) "" else
                    "\n\nНе будут импортированы ${result.warnings.size} строк:\n" +
                        result.warnings.take(10).joinToString("\n\n") +
                        if (result.warnings.size > 10) "\n…и ещё ${result.warnings.size - 10}" else ""
                AlertDialog.Builder(this)
                    .setTitle("Найдено предметов: ${result.items.size}")
                    .setMessage("Лист: ${result.sheetName}\n\nОбновить и добавить предметы или заменить весь список? При замене удаляются предметы, отсутствующие среди импортируемых записей, включая пропущенные проблемные строки. Для совпавших номеров сохраняются отметки и привязки.$warnings")
                    .setPositiveButton("Обновить / добавить") { _, _ -> viewModel.confirmPendingImport(false) }
                    .setNeutralButton("Заменить список") { _, _ -> viewModel.confirmPendingImport(true) }
                    .setNegativeButton("Отмена") { _, _ -> viewModel.cancelPendingImport() }
                    .setOnCancelListener { viewModel.cancelPendingImport() }
                    .show()
            }
        }

        // Наблюдение за статусом импорта
        viewModel.importStatus.observe(this) { status ->
            Log.d(TAG, "Статус импорта: $status")
            Toast.makeText(this, status, Toast.LENGTH_LONG).show()
        }
        
        // Обработчик кнопки "Загрузить тестовые данные"
        loadButton.setOnClickListener {
            loadTestData()
        }
        
        // Обработчик кнопки "Список предметов"
        listButton.setOnClickListener {
            // val intent = Intent(this, ItemsListActivity::class.java)
            // startActivity(intent)
            
            // ★★★★ ТЕСТОВЫЙ ПЕРЕХОД ★★★★
            try {
                val intent = Intent(this, ItemsListActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Ошибка при запуске ItemsListActivity", e)
            }
        }
        
        // Обработчик кнопки "Сканировать"
        scanButton.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            startActivity(intent)
        }

        // Обработчик кнопки "Импорт"
        importButton.setOnClickListener {
            showImportDialog()
        }
        
        Log.d(TAG, "MainActivity инициализирован")
    }
    
    // Показать диалог выбора типа импорта
    private fun showImportDialog() {
        val items = arrayOf("CSV файл", "Excel файл (XLS/XLSX)")
        
        AlertDialog.Builder(this)
            .setTitle("Выберите тип файла")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> importCSV()   // CSV файл
                    1 -> importExcel() // Excel файл
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun importCSV() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv",
                "text/comma-separated-values", 
                "text/plain"
            ))
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_CSV)
    }
    
    private fun importExcel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.oasis.opendocument.spreadsheet"
            ))
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_EXCEL)
    }
    
    // Обновление статистики на экране
    private fun updateStats(stats: InventoryViewModel.InventoryStats) {
        statsTextView.text = "Всего: ${stats.total}\nНайдено: ${stats.found}\nОсталось: ${stats.remaining}"
    }
    
    // ★★★★ ИСПРАВЛЕННАЯ ЗАГРУЗКА ТЕСТОВЫХ ДАННЫХ ★★★★
    private fun loadTestData() {
        Log.d(TAG, "Запуск очистки базы данных")
        
        // Используем новую функцию clearDatabase()
        viewModel.clearDatabase()
        
        // Показываем уведомление пользователю
        Toast.makeText(
            this,
            "✅ База данных очищена. Готово к импорту.",
            Toast.LENGTH_LONG
        ).show()
        
        // ★★★★ ОПЦИОНАЛЬНО: Можно сразу обновить текст статистики ★★★★
        statsTextView.text = """
            📋 ИНСТРУКЦИЯ:
            
            1. Нажмите "Импорт" для загрузки Excel файла
            2. Выберите ведомость 1С (.xls или .xlsx)
            3. Данные появятся в статистике
            4. Используйте "Сканировать" для инвентаризации
            
            Загружено предметов: 0
        """.trimIndent()
    }

    override fun onResume() {
        super.onResume()
        // Обновляем статистику при возвращении на экран
        viewModel.updateStats()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")
        
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // Получаем имя файла
                val fileName = getFileNameFromUri(uri)
                Log.d(TAG, "Выбран файл: $fileName, URI: $uri")
                
                when (requestCode) {
                    REQUEST_CODE_PICK_CSV -> {
                        Log.d(TAG, "=== ИМПОРТ CSV ===")
                        viewModel.importFile(uri, fileName)
                    }
                    REQUEST_CODE_PICK_EXCEL -> {
                        Log.d(TAG, "=== ИМПОРТ EXCEL ===")
                        viewModel.importFile(uri, fileName)
                    }
                    else -> {
                        Log.w(TAG, "Неизвестный requestCode: $requestCode")
                        Toast.makeText(this, "❌ Неизвестный тип файла", Toast.LENGTH_LONG).show()
                    }
                }
            } ?: run {
                Log.w(TAG, "URI пустой")
                Toast.makeText(this, "❌ Не выбран файл", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.d(TAG, "Отмена выбора файла")
        }
    }
    
    // Метод для получения имени файла
    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            when (uri.scheme) {
                "content" -> {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            cursor.getString(nameIndex) ?: "unknown_file"
                        } else {
                            "unknown_file"
                        }
                    } ?: "unknown_file"
                }
                "file" -> uri.lastPathSegment ?: "unknown_file"
                else -> "unknown_file"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения имени файла: ${e.message}")
            "unknown_file"
        }
    }
}