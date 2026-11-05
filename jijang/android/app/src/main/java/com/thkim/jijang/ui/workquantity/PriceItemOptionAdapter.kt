package com.thkim.jijang.ui.workquantity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thkim.jijang.databinding.ItemPriceItemOptionBinding

class PriceItemOptionAdapter(
    private val onClick: (PriceItemOption) -> Unit
) : ListAdapter<PriceItemOption, PriceItemOptionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPriceItemOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPriceItemOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PriceItemOption) {
            binding.textOption.text = item.label
            binding.textOptionUnit.text = item.unit
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PriceItemOption>() {
            override fun areItemsTheSame(oldItem: PriceItemOption, newItem: PriceItemOption) = oldItem.code == newItem.code
            override fun areContentsTheSame(oldItem: PriceItemOption, newItem: PriceItemOption) = oldItem == newItem
        }
    }
}
