package com.heedar.tv.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.heedar.tv.databinding.ItemCategoryBinding

/** A category row entry. `id == null` is the synthetic "All" entry. */
data class CategoryChip(
    val id: String?,
    val name: String
)

class CategoryAdapter(
    private val onSelect: (String?) -> Unit
) : ListAdapter<CategoryChip, CategoryAdapter.ChipHolder>(DIFF) {

    private var selectedId: String? = null

    fun setSelected(id: String?) {
        if (selectedId == id) return
        val previous = selectedId
        selectedId = id
        notifyIdChanged(previous)
        notifyIdChanged(id)
    }

    private fun notifyIdChanged(id: String?) {
        val index = currentList.indexOfFirst { it.id == id }
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipHolder =
        ChipHolder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ChipHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChipHolder(
        private val binding: ItemCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chip: CategoryChip) {
            binding.tvCategory.text = chip.name
            binding.tvCategory.isSelected = chip.id == selectedId
            binding.tvCategory.setOnClickListener {
                setSelected(chip.id)
                onSelect(chip.id)
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CategoryChip>() {
            override fun areItemsTheSame(oldItem: CategoryChip, newItem: CategoryChip): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CategoryChip, newItem: CategoryChip): Boolean =
                oldItem == newItem
        }
    }
}
