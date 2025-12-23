package com.ace.uidemo.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.databinding.TabContentLayoutBinding
import com.ace.uidemo.model.TabData
import com.ace.uidemo.viewholder.TabContentViewHolder

/**
 * 外层ViewPager2的Adapter，用于Tab切换（View方式，非Fragment）
 */
class TabPagerAdapter(
    private val tabs: List<TabData>,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabCompleted: () -> Unit, // Tab轮播完成回调
    private val onPauseStateChanged: (Boolean) -> Unit // 暂停状态改变回调
) : RecyclerView.Adapter<TabContentViewHolder>() {

    private val viewHolders = mutableMapOf<Int, TabContentViewHolder>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabContentViewHolder {
        val binding = TabContentLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        val holder = TabContentViewHolder(
            binding = binding,
            lifecycleScope = lifecycleScope,
            onTabCompleted = onTabCompleted,
            onPauseStateChanged = onPauseStateChanged
        )
        return holder
    }

    override fun onBindViewHolder(holder: TabContentViewHolder, position: Int) {
        viewHolders[position] = holder
        holder.bind(tabs[position])
    }

    override fun getItemCount(): Int = tabs.size

    override fun onViewRecycled(holder: TabContentViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.adapterPosition
        holder.release()
        if (position != RecyclerView.NO_POSITION) {
            viewHolders.remove(position)
        }
    }

    /**
     * 获取指定位置的ViewHolder
     */
    fun getViewHolder(position: Int): TabContentViewHolder? {
        return viewHolders[position]
    }

    /**
     * 释放所有ViewHolder资源
     */
    fun releaseAll() {
        viewHolders.values.forEach { it.release() }
        viewHolders.clear()
    }
}
