package com.yourname.inventoryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourname.inventory.data.InventoryItem

class InventoryAdapter : ListAdapter<InventoryItem, InventoryAdapter.Holder>(object : DiffUtil.ItemCallback<InventoryItem>() {
    override fun areItemsTheSame(a: InventoryItem, b: InventoryItem) = a.barcode == b.barcode
    override fun areContentsTheSame(a: InventoryItem, b: InventoryItem) = a == b
}) {
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.itemName)
        val number: TextView = view.findViewById(R.id.itemNumber)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_inventory, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.number.text = if (item.inventoryNumber.isBlank()) item.displayInventoryNumber else "Инв. №: ${item.inventoryNumber}"
    }
    fun updateItems(items: List<InventoryItem>) = submitList(items)
}
