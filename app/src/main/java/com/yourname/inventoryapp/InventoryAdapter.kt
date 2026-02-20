package com.yourname.inventoryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourname.inventory.data.InventoryItem  // ← ЭТОТ ИМПОРТ!

class InventoryAdapter : RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder>() {
    
    private var items: List<InventoryItem> = emptyList()
    
    class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val itemName: TextView = itemView.findViewById(R.id.itemName)
        val itemNumber: TextView = itemView.findViewById(R.id.itemNumber)
        val itemStatus: TextView = itemView.findViewById(R.id.itemStatus)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return InventoryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = items[position]
        
        holder.itemName.text = item.name
        holder.itemNumber.text = "Инв. №: ${item.inventoryNumber}"
        
        // Определяем статус
        val statusText = if (item.scanned) {
            "✓ Отсканирован"
        } else {
            "⏳ Ожидает сканирования"
        }
        holder.itemStatus.text = statusText
        
        // Цвет статуса
        val color = if (item.scanned) {
            android.graphics.Color.parseColor("#4CAF50") // зеленый
        } else {
            android.graphics.Color.parseColor("#FF9800") // оранжевый
        }
        holder.itemStatus.setTextColor(color)
    }
    
    override fun getItemCount(): Int = items.size
    
    fun updateItems(newItems: List<InventoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}