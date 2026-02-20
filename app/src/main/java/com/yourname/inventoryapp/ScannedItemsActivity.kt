package com.yourname.inventoryapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.yourname.inventory.data.InventoryItem      // ← ЭТОТ ИМПОРТ
import com.yourname.inventory.data.InventoryViewModel // ← ЭТОТ ИМПОРТ
import com.yourname.inventoryapp.databinding.ActivityScannedItemsBinding

class ScannedItemsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScannedItemsBinding
    private lateinit var viewModel: InventoryViewModel
    private lateinit var adapter: InventoryAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityScannedItemsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        Log.d("ScannedItems", "Активити создана")
        
        // Настройка ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Найденные предметы"
        
        // Инициализация ViewModel
        viewModel = ViewModelProvider(this).get(InventoryViewModel::class.java)
        
        // Инициализация адаптера
        adapter = InventoryAdapter()
        
        // Настройка RecyclerView
        binding.scannedRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.scannedRecyclerView.adapter = adapter
        
        // Наблюдение за данными
        observeScannedItems()
    }
    
    private fun observeScannedItems() {
        viewModel.scannedItems.observe(this) { items ->
            Log.d("ScannedItems", "Получены данные: ${items?.size ?: 0} предметов")
            
            if (items != null) {
                adapter.updateItems(items)
                supportActionBar?.subtitle = "Найдено: ${items.size} предметов"
                
                // Логи для отладки
                if (items.isNotEmpty()) {
                    items.take(3).forEach { item ->
                        Log.d("ScannedItems", "Элемент: ${item.inventoryNumber} - ${item.name} - scanned: ${item.scanned}")
                    }
                } else {
                    Log.d("ScannedItems", "Список отсканированных предметов пуст")
                }
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}