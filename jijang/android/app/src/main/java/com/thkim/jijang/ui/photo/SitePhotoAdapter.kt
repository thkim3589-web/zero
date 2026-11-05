package com.thkim.jijang.ui.photo

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thkim.jijang.data.model.SitePhoto
import com.thkim.jijang.databinding.ItemSitePhotoBinding

class SitePhotoAdapter(
    private val onFieldChanged: (SitePhoto) -> Unit,
    private val onDelete: (SitePhoto) -> Unit
) : ListAdapter<SitePhoto, SitePhotoAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSitePhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSitePhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SitePhoto) {
            binding.textSlotLabel.text = "블록${item.blockIndex + 1} · 슬롯${item.slotIndex + 1}"
            binding.imageThumbnail.setImageBitmap(
                BitmapFactory.decodeFile(item.localFilePath, BitmapFactory.Options().apply { inSampleSize = 4 })
            )

            binding.editPhotoType.setText(item.photoType)
            binding.editDescription.setText(item.description)
            binding.editRelatedFacility.setText(item.relatedFacilityNo)

            binding.editPhotoType.doAfterTextChangedTo { text ->
                onFieldChanged(currentItem().copy(photoType = text.ifBlank { null }))
            }
            binding.editDescription.doAfterTextChangedTo { text ->
                onFieldChanged(currentItem().copy(description = text.ifBlank { null }))
            }
            binding.editRelatedFacility.doAfterTextChangedTo { text ->
                onFieldChanged(currentItem().copy(relatedFacilityNo = text.ifBlank { null }))
            }
            binding.buttonDelete.setOnClickListener { onDelete(currentItem()) }
        }

        private fun currentItem(): SitePhoto = getItem(bindingAdapterPosition)

        private fun android.widget.EditText.doAfterTextChangedTo(action: (String) -> Unit) {
            var lastFocus = false
            onFocusChangeListener = android.view.View.OnFocusChangeListener { _, hasFocus ->
                if (lastFocus && !hasFocus && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    action(text.toString())
                }
                lastFocus = hasFocus
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SitePhoto>() {
            override fun areItemsTheSame(oldItem: SitePhoto, newItem: SitePhoto) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SitePhoto, newItem: SitePhoto) = oldItem == newItem
        }
    }
}
