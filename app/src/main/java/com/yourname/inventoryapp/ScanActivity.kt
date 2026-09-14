package com.yourname.inventoryapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.yourname.inventory.data.InventoryItem
import com.yourname.inventory.data.InventoryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {
    private lateinit var viewModel: InventoryViewModel
    private var pendingCode: String? = null
    private var pendingFormat: String? = null

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code == null) finish() else {
            pendingCode = code
            pendingFormat = result.formatName
            processScannedCode(code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[InventoryViewModel::class.java]
        pendingCode = savedInstanceState?.getString("code")
        pendingFormat = savedInstanceState?.getString("format")
        val code = pendingCode
        if (code != null) processScannedCode(code)
        else if (savedInstanceState == null) startScanner()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("code", pendingCode)
        outState.putString("format", pendingFormat)
        super.onSaveInstanceState(outState)
    }

    private fun startScanner() {
        pendingCode = null
        pendingFormat = null
        barcodeLauncher.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(BarcodeScanConfig.formats.map { it.name })
            BarcodeScanConfig.hints.forEach { (hint, value) -> addExtra(hint.name, value) }
            setPrompt("Наведите на QR-код или штрихкод")
            setCameraId(0)
            setBeepEnabled(true)
            setOrientationLocked(false)
        })
    }

    private fun processScannedCode(code: String) {
        lifecycleScope.launch {
            try {
                val matches = viewModel.getItemsByCode(code)
                when (matches.size) {
                    0 -> showUnknownCode(code)
                    1 -> {
                        val item = matches.single()
                        viewModel.markAsScanned(item.inventoryNumber)
                        showFound(item)
                    }
                    else -> showError("Код относится к нескольким предметам. Проверьте инвентарные номера и привязки.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e.message ?: "Не удалось прочитать базу")
            }
        }
    }

    private fun showUnknownCode(code: String) {
        AlertDialog.Builder(this)
            .setTitle("Код не найден")
            .setMessage("Считано: $code\nФормат: ${pendingFormat ?: "неизвестен"}\n\nШтрихкод 1С может отличаться от инвентарного номера. Выберите предмет, чтобы сохранить связь и отметить его найденным.")
            .setPositiveButton("Выбрать предмет") { _, _ -> chooseItem(code) }
            .setNeutralButton("Сканировать снова") { _, _ -> startScanner() }
            .setNegativeButton("Закрыть") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun chooseItem(code: String) {
        lifecycleScope.launch {
            try {
                val items = viewModel.bindingCandidates().sortedBy { it.name }
                if (items.isEmpty()) {
                    showError("Сначала импортируйте ведомость: список предметов пуст.")
                    return@launch
                }
                val search = EditText(this@ScanActivity).apply {
                    hint = "Инвентарный номер или название"
                    isSingleLine = true
                }
                var visibleItems = items
                val adapter = ArrayAdapter<String>(this@ScanActivity, android.R.layout.simple_list_item_1,
                    items.map { "${it.inventoryNumber}\n${it.name}" }.toMutableList())
                val list = ListView(this@ScanActivity).apply { this.adapter = adapter }
                val content = LinearLayout(this@ScanActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = (16 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                    addView(search)
                    addView(list, LinearLayout.LayoutParams(-1, (300 * resources.displayMetrics.density).toInt()))
                }
                val dialog = AlertDialog.Builder(this@ScanActivity)
                    .setTitle("Привязать код $code")
                    .setView(content)
                    .setNegativeButton("Отмена") { _, _ -> showUnknownCode(code) }
                    .setOnCancelListener { showUnknownCode(code) }
                    .create()
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s?.toString().orEmpty().trim()
                        visibleItems = items.filter {
                            it.inventoryNumber.contains(query, true) || it.name.contains(query, true)
                        }
                        adapter.clear()
                        adapter.addAll(visibleItems.map { "${it.inventoryNumber}\n${it.name}" })
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
                list.setOnItemClickListener { _, _, position, _ ->
                    val item = visibleItems[position]
                    dialog.dismiss()
                    confirmBinding(code, item)
                }
                dialog.show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e.message ?: "Не удалось загрузить предметы")
            }
        }
    }

    private fun confirmBinding(code: String, item: InventoryItem) {
        val replacement = if (item.qrData.isNotEmpty() && item.qrData != item.inventoryNumber && item.qrData != code)
            "\nПрежняя привязка ${item.qrData} будет заменена." else ""
        AlertDialog.Builder(this)
            .setTitle("Подтвердите предмет")
            .setMessage("${item.name}\nИнвентарный номер: ${item.inventoryNumber}\nКод: $code$replacement")
            .setPositiveButton("Привязать и отметить") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.bindCode(item.inventoryNumber, code)
                        showFound(item)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        showError(e.message ?: "Не удалось сохранить привязку")
                    }
                }
            }
            .setNegativeButton("Назад") { _, _ -> chooseItem(code) }
            .setOnCancelListener { chooseItem(code) }
            .show()
    }

    private fun showFound(item: InventoryItem) {
        Toast.makeText(this, "Найден: ${item.name} (${item.inventoryNumber})", Toast.LENGTH_LONG).show()
        pendingCode = null
        finish()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this).setTitle("Не удалось выполнить действие")
            .setMessage(message).setPositiveButton("Закрыть") { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
}
