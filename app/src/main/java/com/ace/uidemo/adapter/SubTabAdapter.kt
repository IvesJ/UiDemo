package com.ace.uidemo.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.databinding.ItemSubTabBinding
import com.ace.uidemo.model.TabData

/**
 * 子Tab适配器 - 竖向显示
 */
class SubTabAdapter(
    private val subTabs: List<TabData>,
    private val onSubTabSelected: (position: Int) -> Unit
) : RecyclerView.Adapter<SubTabAdapter.SubTabViewHolder>() {

    private var selectedPosition = 0

    inner class SubTabViewHolder(
        private val binding: ItemSubTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentPosition: Int = 0

        fun bind(tabData: TabData, isSelected: Boolean, position: Int) {
            currentPosition = position
            binding.subTabText.text = tabData.title
            binding.subTabText.isSelected = isSelected

            binding.root.setOnClickListener {
                if (currentPosition != selectedPosition) {
                    notifyItemChanged(selectedPosition)
                    selectedPosition = currentPosition
                    notifyItemChanged(selectedPosition)
                    onSubTabSelected(selectedPosition)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubTabViewHolder {
        val binding = ItemSubTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubTabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubTabViewHolder, position: Int) {
        holder.bind(subTabs[position], position == selectedPosition, position)
    }

    override fun getItemCount(): Int = subTabs.size

    /**
     * 设置选中的子Tab
     */
    fun setSelectedPosition(position: Int) {
        if (position in subTabs.indices && position != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
        }
    }

    fun getSelectedPosition(): Int = selectedPosition
}
