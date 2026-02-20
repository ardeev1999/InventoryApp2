package com.yourname.inventoryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourname.inventory.data.InventoryItem

class ItemsAdapter(
    private val onItemClick: (InventoryItem) -> Unit
) : ListAdapter<InventoryItem, ItemsAdapter.ItemViewHolder>(DiffCallback) {

    class ItemViewHolder(view: View, val onItemClick: (InventoryItem) -> Unit) : 
        RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.itemName)
        private val numberTextView: TextView = view.findViewById(R.id.itemNumber)
        private val statusTextView: TextView = view.findViewById(R.id.itemStatus)
        private var currentItem: InventoryItem? = null
        
        init {
            view.setOnClickListener {
                currentItem?.let { onItemClick(it) }
            }
        }
        
        fun bind(item: InventoryItem) {
            currentItem = item
            nameTextView.text = item.name
            numberTextView.text = "Инв. №: ${item.inventoryNumber}"
            statusTextView.text = if (item.scanned) "✓ Найден" else "❌ Не найден"
            statusTextView.setTextColor(
                if (item.scanned) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return ItemViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<InventoryItem>() {
            override fun areItemsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
                return oldItem.inventoryNumber == newItem.inventoryNumber
            }

            override fun areContentsTheSame(oldItem: InventoryItem, newItem: InventoryItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}