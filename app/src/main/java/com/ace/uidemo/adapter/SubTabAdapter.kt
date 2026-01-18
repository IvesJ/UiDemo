package com.ace.uidemo.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.databinding.ItemSubTabBinding
import com.ace.uidemo.model.TabData

/**
 * 左侧竖向子Tab列表的Adapter
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
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.adapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val oldPosition = selectedPosition
                selectedPosition = adapterPosition
                notifyItemChanged(oldPosition)
                notifyItemChanged(adapterPosition)
                onSubTabSelected(adapterPosition)
            }
        }
    }

    override fun getItemCount(): Int = subTabs.size

    /**
     * 更新选中的子Tab
     */
    fun setSelectedPosition(position: Int) {
        if (position != selectedPosition && position in subTabs.indices) {
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(position)
        }
    }

    /**
     * 获取当前选中位置
     */
    fun getSelectedPosition(): Int = selectedPosition

    class SubTabViewHolder(
        private val binding: ItemSubTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tabData: TabData, isSelected: Boolean) {
            binding.subTabTitle.text = tabData.title
            binding.subTabTitle.setTextColor(
                if (isSelected) Color.WHITE else Color.parseColor("#888888")
            )
            binding.root.setBackgroundColor(
                if (isSelected) Color.parseColor("#333333") else Color.TRANSPARENT
            )
        }
    }
}
