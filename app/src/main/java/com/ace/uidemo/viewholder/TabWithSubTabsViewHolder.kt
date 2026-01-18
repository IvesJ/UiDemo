package com.ace.uidemo.viewholder

import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.SubTabAdapter
import com.ace.uidemo.adapter.SubTabSwitchAdapter
import com.ace.uidemo.databinding.TabContentWithSubtabsLayoutBinding
import com.ace.uidemo.model.MediaItem
import com.ace.uidemo.model.TabData
import com.ace.uidemo.widget.NestedViewPager2

/**
 * 带子Tab的ViewHolder，支持滑动切换子Tab
 * 新逻辑：
 * 1. 用户可以左右滑动在子Tab之间切换
 * 2. 在子Tab边界时，滑动事件传递给父ViewPager2
 * 3. 子Tab内的媒体自动轮播
 */
class TabWithSubTabsViewHolder(
    private val binding: TabContentWithSubtabsLayoutBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabCompleted: () -> Unit,
    private val onPauseStateChanged: (Boolean) -> Unit,
    private val onBoundaryReached: ((isLeft: Boolean) -> Unit)? = null
) : RecyclerView.ViewHolder(binding.root), ITabContentViewHolder {

    companion object {
        private const val TAG = "TabWithSubTabsViewHolder"
    }

    private var subTabAdapter: SubTabAdapter? = null
    private var subTabSwitchAdapter: SubTabSwitchAdapter? = null
    private var subTabs: List<TabData> = emptyList()
    private var currentSubTabIndex = 0
    private var isPaused = false
    private var lastBoundaryEventTime = 0L // 防抖时间戳

    init {
        Log.d(TAG, "TabWithSubTabsViewHolder created")
        setupSubTabSwitchPager()
    }

    private fun setupSubTabSwitchPager() {
        Log.d(TAG, "setupSubTabSwitchPager")
        binding.subTabSwitchPager.apply {
            offscreenPageLimit = 1

            // 设置边界滑动回调 - 只有在子tab切换的边界才传递给父级
            onBoundaryReached = { isLeft ->
                val currentTime = System.currentTimeMillis()
                Log.d(TAG, "SubTab boundary reached: isLeft=$isLeft, currentSubTab=$currentSubTabIndex, totalSubTabs=${subTabs.size}")

                // 只有在没有被防抖的情况下才处理
                if (currentTime - lastBoundaryEventTime >= 500) {
                    // 当在子Tab ViewPager边界滑动时，通知父级进行切换
                    if (isLeft && currentSubTabIndex == 0) {
                        // 在第一个子Tab向右滑动，切换到上一个父Tab
                        Log.d(TAG, "First subtab boundary reached, notify parent")
                        lastBoundaryEventTime = currentTime
                        this@TabWithSubTabsViewHolder.onBoundaryReached?.invoke(true)
                    } else if (!isLeft && currentSubTabIndex == subTabs.size - 1) {
                        // 在最后一个子Tab向左滑动，切换到下一个父Tab
                        Log.d(TAG, "Last subtab boundary reached, notify parent")
                        lastBoundaryEventTime = currentTime
                        this@TabWithSubTabsViewHolder.onBoundaryReached?.invoke(false)
                    } else {
                        // 在中间子Tab的边界，不传递给父级
                        Log.d(TAG, "Middle subtab boundary, not notifying parent")
                    }
                } else {
                    Log.d(TAG, "Debouncing: ignoring boundary event (too soon after last boundary event)")
                }
            }

            // 子tab边界不消费事件，允许传递给父级
            consumeBoundaryEvents = false

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    Log.d(TAG, "SubTab page selected: position=$position")
                    onSubTabChanged(position)
                }
            })
        }
    }

    fun bind(tabData: TabData) {
        Log.d(TAG, "bind: tabData=${tabData.title}, subTabCount=${tabData.subTabs.size}")
        subTabs = tabData.subTabs
        currentSubTabIndex = 0
        isPaused = false

        if (subTabs.isEmpty()) {
            Log.w(TAG, "No subtabs found")
            return
        }

        // 设置左侧子Tab列表
        setupSubTabList()

        // 设置子Tab切换适配器
        setupSubTabSwitchAdapter()
    }

    private fun setupSubTabList() {
        Log.d(TAG, "setupSubTabList: subTabCount=${subTabs.size}")
        subTabAdapter = SubTabAdapter(subTabs) { position ->
            Log.d(TAG, "SubTab clicked: position=$position")
            // 用户点击子Tab时的处理
            if (position != currentSubTabIndex) {
                binding.subTabSwitchPager.setCurrentItem(position, true)
            }
        }

        binding.subTabRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = subTabAdapter
        }
    }

    private fun setupSubTabSwitchAdapter() {
        Log.d(TAG, "setupSubTabSwitchAdapter")
        subTabSwitchAdapter = SubTabSwitchAdapter(
            subTabs = subTabs,
            lifecycleScope = lifecycleScope,
            onSubTabCompleted = { position ->
                Log.d(TAG, "onSubTabCompleted: position=$position, totalSubTabs=${subTabs.size}")
                // 单个子Tab的媒体轮播完成，切换到下一个子Tab
                val nextPosition = position + 1
                if (nextPosition < subTabs.size) {
                    // 还有下一个子tab，切换到下一个
                    Log.d(TAG, "Switching to next subtab: $nextPosition")
                    binding.subTabSwitchPager.setCurrentItem(nextPosition, true)
                } else {
                    // 这是最后一个子tab，应该已经由onAllSubTabsCompleted处理
                    Log.d(TAG, "Last subtab completed, should be handled by onAllSubTabsCompleted")
                }
                // 注意：不在这里处理最后一个子tab完成，由onAllSubTabsCompleted处理
            },
            onAllSubTabsCompleted = {
                Log.d(TAG, "onAllSubTabsCompleted - all subtabs finished")
                // 所有子Tab轮播完成，通知父级Tab完成
                onTabCompleted()
            },
            onPauseStateChanged = onPauseStateChanged,
            onSwitchToPrevious = { position ->
                Log.d(TAG, "onSwitchToPrevious: position=$position, currentSubTabIndex=$currentSubTabIndex")
                // 媒体左滑边界，切换到上一个子Tab
                val prevPosition = position - 1
                if (prevPosition >= 0) {
                    // 还有上一个子tab，切换到上一个
                    Log.d(TAG, "Switching to previous subtab: $prevPosition")
                    binding.subTabSwitchPager.setCurrentItem(prevPosition, true)
                } else {
                    // 这是第一个子tab，通知父级切换到上一个父Tab
                    Log.d(TAG, "First subtab reached, notify parent to switch to previous tab")
                    onBoundaryReached?.invoke(true) // true表示左滑，切换到上一个父tab
                }
            }
        )

        binding.subTabSwitchPager.adapter = subTabSwitchAdapter
        binding.subTabSwitchPager.setCurrentItem(0, false)

        Log.d(TAG, "SubTab adapter set with ${subTabs.size} subtabs")

        // 暂时注释掉自动开始
        // if (!isPaused) {
        //     subTabSwitchAdapter?.resumeCurrent()
        // }
    }

    /**
     * 子Tab切换时的处理
     */
    private fun onSubTabChanged(newPosition: Int) {
        Log.d(TAG, "onSubTabChanged: from=$currentSubTabIndex to=$newPosition")
        if (newPosition == currentSubTabIndex) {
            Log.d(TAG, "Same position, no change needed")
            return
        }

        if (newPosition < 0 || newPosition >= subTabs.size) {
            Log.w(TAG, "Invalid position: $newPosition, subTabsSize=${subTabs.size}")
            return
        }

        currentSubTabIndex = newPosition

        // 更新左侧选中状态
        subTabAdapter?.setSelectedPosition(newPosition)

        // 切换到新的子Tab
        subTabSwitchAdapter?.switchToSubTab(newPosition)

        // 同步adapter的currentPosition状态
        subTabSwitchAdapter?.setCurrentPosition(newPosition)

        // 更新暂停状态
        onPauseStateChanged(isPaused)

        Log.d(TAG, "SubTab changed to position $currentSubTabIndex successfully")
    }

    /**
     * 切换暂停/播放
     */
    override fun togglePause() {
        Log.d(TAG, "togglePause: isPaused=$isPaused")
        isPaused = !isPaused

        if (isPaused) {
            subTabSwitchAdapter?.pauseAll()
        } else {
            subTabSwitchAdapter?.resumeCurrent()
        }

        onPauseStateChanged(isPaused)
    }

    /**
     * 暂停轮播
     */
    override fun pause() {
        Log.d(TAG, "pause")
        if (!isPaused) {
            isPaused = true
            subTabSwitchAdapter?.pauseAll()
        }
    }

    /**
     * 恢复轮播
     */
    override fun resume() {
        Log.d(TAG, "resume")
        if (isPaused) {
            isPaused = false
            subTabSwitchAdapter?.resumeCurrent()
        }
    }

    /**
     * 释放资源
     */
    override fun release() {
        Log.d(TAG, "release")
        subTabSwitchAdapter?.releaseAll()
    }

    /**
     * 获取暂停状态
     */
    override fun isPaused(): Boolean = isPaused

    /**
     * 获取当前媒体项
     */
    override fun getCurrentMediaItem(): MediaItem? {
        return subTabSwitchAdapter?.getCurrentMediaItem()
    }

    /**
     * 是否在最后一项（最后一个子Tab且媒体轮播完成）
     */
    override fun isAtLastItem(): Boolean {
        return currentSubTabIndex == subTabs.size - 1
        // 注意：这里简化了逻辑，实际可以根据需要判断当前媒体是否也是最后一个
    }

    /**
     * 重置到第一个子Tab并开始播放
     */
    override fun resetToFirst() {
        Log.d(TAG, "resetToFirst")
        if (subTabs.isEmpty()) return

        currentSubTabIndex = 0
        isPaused = false

        // 更新左侧子Tab选中状态
        subTabAdapter?.setSelectedPosition(0)

        // 重置ViewPager到第一个子Tab
        binding.subTabSwitchPager.setCurrentItem(0, false)

        // 重置SubTabSwitchAdapter的currentPosition
        subTabSwitchAdapter?.resetCurrentPosition()

        // 暂时注释掉自动开始
        // subTabSwitchAdapter?.resumeCurrent()
    }
}
