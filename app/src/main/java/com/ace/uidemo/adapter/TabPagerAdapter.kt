package com.ace.uidemo.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.databinding.TabContentLayoutBinding
import com.ace.uidemo.databinding.TabContentWithSubtabsLayoutBinding
import com.ace.uidemo.model.TabData
import com.ace.uidemo.viewholder.ITabContentViewHolder
import com.ace.uidemo.viewholder.TabContentViewHolder
import com.ace.uidemo.viewholder.TabWithSubTabsViewHolder

/**
 * 外层ViewPager2的Adapter，用于Tab切换
 * 支持两种类型的ViewHolder：
 * - TabContentViewHolder：无子Tab的情况
 * - TabWithSubTabsViewHolder：有子Tab的情况
 */
class TabPagerAdapter(
    private val tabs: List<TabData>,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabCompleted: () -> Unit,
    private val onPauseStateChanged: (Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SIMPLE = 0
        private const val VIEW_TYPE_WITH_SUBTABS = 1
    }

    private val viewHolders = mutableMapOf<Int, ITabContentViewHolder>()

    override fun getItemViewType(position: Int): Int {
        return if (tabs[position].subTabs.isNotEmpty()) {
            VIEW_TYPE_WITH_SUBTABS
        } else {
            VIEW_TYPE_SIMPLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_WITH_SUBTABS -> {
                val binding = TabContentWithSubtabsLayoutBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                TabWithSubTabsViewHolder(
                    binding = binding,
                    lifecycleScope = lifecycleScope,
                    onTabCompleted = onTabCompleted,
                    onPauseStateChanged = onPauseStateChanged
                )
            }
            else -> {
                val binding = TabContentLayoutBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                TabContentViewHolder(
                    binding = binding,
                    lifecycleScope = lifecycleScope,
                    onTabCompleted = onTabCompleted,
                    onPauseStateChanged = onPauseStateChanged
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val tabData = tabs[position]
        when (holder) {
            is TabWithSubTabsViewHolder -> {
                viewHolders[position] = holder
                holder.bind(tabData)
            }
            is TabContentViewHolder -> {
                viewHolders[position] = holder
                holder.bind(tabData)
            }
        }
    }

    override fun getItemCount(): Int = tabs.size

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.adapterPosition
        when (holder) {
            is TabWithSubTabsViewHolder -> holder.release()
            is TabContentViewHolder -> holder.release()
        }
        if (position != RecyclerView.NO_POSITION) {
            viewHolders.remove(position)
        }
    }

    /**
     * 获取指定位置的ViewHolder（通过接口访问）
     */
    fun getViewHolder(position: Int): ITabContentViewHolder? {
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
