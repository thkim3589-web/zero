package com.thkim.jijang.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thkim.jijang.data.model.SiteCase
import com.thkim.jijang.databinding.ItemSiteCaseBinding
import com.thkim.jijang.ui.common.StatusChip

class SiteCaseAdapter(
    private val onClick: (SiteCase) -> Unit
) : ListAdapter<SiteCase, SiteCaseAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSiteCaseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSiteCaseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SiteCase) {
            binding.textCaseName.text = item.caseName
            binding.textSiteAddress.text = item.siteAddress.ifBlank { "주소 미입력" }
            StatusChip.apply(binding.textStatus, item.status)
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SiteCase>() {
            override fun areItemsTheSame(oldItem: SiteCase, newItem: SiteCase) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SiteCase, newItem: SiteCase) = oldItem == newItem
        }
    }
}
