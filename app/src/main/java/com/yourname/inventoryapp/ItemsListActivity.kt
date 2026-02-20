package com.yourname.inventoryapp

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.inventory.data.InventoryViewModel
import kotlinx.coroutines.launch

class ItemsListActivity : AppCompatActivity() {

    private lateinit var viewModel: InventoryViewModel
    private lateinit var adapter: InventoryAdapter
    private lateinit var tvTotalCount: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_items_list)
        
        // Находим элементы
        tvTotalCount = findViewById(R.id.tvTotalCount)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        
        // Настройка RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = InventoryAdapter()
        recyclerView.adapter = adapter
        
        // Инициализация ViewModel
        viewModel = ViewModelProvider(this)[InventoryViewModel::class.java]
        
        // Показываем заголовок с количеством
        tvTotalCount.text = "Загрузка..."
        
        // Наблюдаем за списком предметов из базы
        viewModel.allItems.observe(this) { items ->
            if (items.isNotEmpty()) {
                tvTotalCount.text = "Всего предметов: ${items.size}"
                adapter.updateItems(items)
                
                // Подсчитываем отсканированные
                val scannedCount = items.count { it.scanned }
                Toast.makeText(this, 
                    "Отсканировано: $scannedCount из ${items.size}", 
                    Toast.LENGTH_SHORT).show()
            } else {
                tvTotalCount.text = "Нет предметов в базе"
                Toast.makeText(this, "База данных пуста", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Альтернативный способ загрузки (если LiveData не сработает)
        lifecycleScope.launch {
            try {
                val count = viewModel.getItemCount()
                tvTotalCount.text = "Всего предметов: $count"
            } catch (e: Exception) {
                tvTotalCount.text = "Ошибка загрузки"
                Toast.makeText(this@ItemsListActivity, 
                    "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}