package com.yourname.inventoryapp

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.yourname.inventory.data.InventoryViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {
    private lateinit var viewModel: InventoryViewModel
    private var pendingCode: String? = null
    private var resultTitle: String? = null
    private var resultMessage: String? = null
    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code == null) finish() else { pendingCode = code; process(code) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[InventoryViewModel::class.java]
        pendingCode = savedInstanceState?.getString("code")
        resultTitle = savedInstanceState?.getString("title")
        resultMessage = savedInstanceState?.getString("message")
        when {
            resultTitle != null -> showResult()
            pendingCode != null -> process(pendingCode!!)
            savedInstanceState == null -> startScanner()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("code", pendingCode)
        outState.putString("title", resultTitle)
        outState.putString("message", resultMessage)
        super.onSaveInstanceState(outState)
    }
    private fun startScanner() {
        pendingCode = null; resultTitle = null; resultMessage = null
        scanner.launch(ScanOptions().apply {
            setDesiredBarcodeFormats(BarcodeScanConfig.formats.map { it.name })
            BarcodeScanConfig.hints.forEach { (hint, value) -> addExtra(hint.name, value) }
            setPrompt("Наведите на QR-код или штрихкод")
            setBeepEnabled(true)
            setOrientationLocked(false)
        })
    }
    private fun process(code: String) {
        lifecycleScope.launch {
            try {
                // Точное сравнение только с barcode. Никаких дополнений, обрезки и поиска по инвентарному номеру.
                val item = viewModel.scan(code)
                resultTitle = if (item == null) "Предмет не найден" else if (item.scanned) "Уже найден" else "Предмет найден"
                resultMessage = if (item == null) "В загруженном списке нет предмета с этим кодом. Проверьте файл импорта."
                    else "${item.name}\n${item.displayInventoryNumber}"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { resultTitle = "Ошибка сканирования"; resultMessage = "Не удалось сохранить результат. Повторите попытку." }
            pendingCode = null
            showResult()
        }
    }
    private fun showResult() {
        AlertDialog.Builder(this).setTitle(resultTitle).setMessage(resultMessage)
            .setPositiveButton("Сканировать ещё") { _, _ -> startScanner() }
            .setNegativeButton("Закрыть") { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
}
