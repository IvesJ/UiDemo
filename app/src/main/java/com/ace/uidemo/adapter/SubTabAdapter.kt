package com.ace.uidemo.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.databinding.ItemSubTabBinding
import com.ace.uidemo.model.TabData

/**
 * 子Tab列表适配器（竖向显示在内容页左侧）
 */
class SubTabAdapter(
    private val subTabs: List<TabData>,
    private val onSubTabSelected: (Int) -> Unit
) : RecyclerView.Adapter<SubTabAdapter.SubTabViewHolder>() {

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubTabViewHolder {
        val binding = ItemSubTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubTabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubTabViewHolder, position: Int) {
        holder.bind(subTabs[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = subTabs.size

    inner class SubTabViewHolder(
        private val binding: ItemSubTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION && position != selectedPosition) {
                    val previousPosition = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)
                    onSubTabSelected(position)
                }
            }
        }

        fun bind(subTab: TabData, isSelected: Boolean) {
            binding.subTabTextView.text = subTab.title
            binding.subTabTextView.isSelected = isSelected
        }
    }

    /**
     * 获取当前选中的子Tab位置
     */
    fun getSelectedPosition(): Int = selectedPosition

    /**
     * 重置选中状态
     */
    fun resetSelection() {
        val previousPosition = selectedPosition
        selectedPosition = 0
        notifyItemChanged(previousPosition)
        notifyItemChanged(selectedPosition)
    }
}
