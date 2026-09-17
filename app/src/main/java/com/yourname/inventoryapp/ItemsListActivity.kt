package com.yourname.inventoryapp

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.inventory.data.InventoryItem
import com.yourname.inventory.data.InventoryViewModel

class ItemsListActivity : AppCompatActivity() {
    private val adapter = InventoryAdapter()
    private var items = emptyList<InventoryItem>()
    private var query = ""
    private lateinit var count: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_items_list)
        val filter = intent.getStringExtra("filter") ?: "all"
        title = when (filter) { "found" -> "Список найденных"; "remaining" -> "Список оставшихся"; else -> "Список предметов" }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        count = findViewById(R.id.tvTotalCount)
        findViewById<RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@ItemsListActivity)
            adapter = this@ItemsListActivity.adapter
        }
        findViewById<EditText>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty().trim(); render()
            }
        })
        ViewModelProvider(this)[InventoryViewModel::class.java].allItems.observe(this) { all ->
            items = when (filter) { "found" -> all.filter { it.scanned }; "remaining" -> all.filterNot { it.scanned }; else -> all }
            render()
        }
    }
    private fun render() {
        val visible = items.filter { it.name.contains(query, true) || it.displayInventoryNumber.contains(query, true) }
        adapter.updateItems(visible)
        count.text = if (visible.isEmpty()) "Нет предметов" else "Предметов: ${visible.size} из ${items.size}"
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
