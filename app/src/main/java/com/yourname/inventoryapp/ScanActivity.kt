package com.yourname.inventoryapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.yourname.inventory.data.InventoryViewModel
import kotlinx.coroutines.launch

class ScanActivity : AppCompatActivity() {
    
    private lateinit var viewModel: InventoryViewModel
    
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents.trim()
            processScannedCode(scannedCode)
        } else {
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ★★★★ НУЖЕН ХОТЯ БЫ ПУСТОЙ LAYOUT ★★★★
        // Если нет файла activity_scan.xml, создайте простой
        // setContentView(R.layout.activity_scan)
        
        viewModel = ViewModelProvider(this)[InventoryViewModel::class.java]
        
        startScanner()
    }
    
    private fun startScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE, ScanOptions.CODE_128)
            setPrompt("Наведите на QR-код или штрих-код")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(true)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }
    
    private fun processScannedCode(code: String) {
        lifecycleScope.launch {
            val item = viewModel.getItemByNumber(code)
            
            if (item != null) {
                viewModel.markAsScanned(code)
                
                Toast.makeText(
                    this@ScanActivity,
                    "Найден: ${item.name} (${item.inventoryNumber})",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@ScanActivity,
                    "Предмет с номером '$code' не найден в базе",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            // Закрываем через 1.5 секунды
            kotlinx.coroutines.delay(1500)
            finish()
        }
    }
}