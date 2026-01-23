package com.ace.uidemo.viewholder

import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.SubTabAdapter
import com.ace.uidemo.adapter.SubTabSwitchAdapter
import com.ace.uidemo.controller.NestedViewPagerController
import com.ace.uidemo.controller.SubTabViewPagerController
import com.ace.uidemo.databinding.TabContentWithSubtabsLayoutBinding
import com.ace.uidemo.model.*

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
) : RecyclerView.ViewHolder(binding.root), ITabContentViewHolder, NestedViewPagerController, SubTabViewPagerController {

    companion object {
        private const val TAG = "TabWithSubTabsViewHolder"
    }

    // 使用新的架构组件
    private val state = NestedViewPagerState()
    private val eventBus = NestedPagerEventBus.getInstance()

    private var subTabAdapter: SubTabAdapter? = null
    private var subTabSwitchAdapter: SubTabSwitchAdapter? = null
    private var subTabs: List<TabData> = emptyList()

    init {
        Log.d(TAG, "TabWithSubTabsViewHolder created")
        setupEventListeners()
        setupSubTabSwitchPager()
    }

    /**
     * 设置事件监听器
     */
    private fun setupEventListeners() {
        // 订阅状态变更事件
        state.addStateChangeListener { newState ->
            Log.d(TAG, "State changed: $newState")
        }

        // 注意：移除了 SubTabCompleted, SwitchToNextSubTab, SwitchToPreviousSubTab 的事件订阅
        // 因为这些事件已经在回调中直接处理了，避免重复处理导致逻辑错误

        eventBus.subscribe<NestedPagerEventBus.Event.SwitchToSubTabPosition> { event ->
            Log.d(TAG, "Received SwitchToSubTabPosition event: position=${event.position}, smooth=${event.smooth}")
            binding.subTabSwitchPager.setCurrentItem(event.position, event.smooth)
        }

        eventBus.subscribe<NestedPagerEventBus.Event.PauseStateChanged> { event ->
            Log.d(TAG, "Received PauseStateChanged event: isPaused=${event.isPaused}")
            onPauseStateChanged(event.isPaused)
        }
    }

    private fun setupSubTabSwitchPager() {
        Log.d(TAG, "setupSubTabSwitchPager")
        binding.subTabSwitchPager.apply {
            offscreenPageLimit = NestedPagerConfig.OFFSCREEN_PAGE_LIMIT

            // 设置边界滑动回调 - 只有在子tab切换的边界才传递给父级
            onBoundaryReached = { isLeft ->
                Log.d(TAG, "SubTab boundary reached: isLeft=$isLeft, currentSubTab=${state.currentSubTabIndex}, totalSubTabs=${subTabs.size}")

                // 使用新的防抖检查
                if (state.canHandleBoundaryEvent()) {
                    // 当在子Tab ViewPager边界滑动时，通知父级进行切换
                    if (isLeft && state.currentSubTabIndex == 0) {
                        // 在第一个子Tab向右滑动，切换到上一个父Tab
                        Log.d(TAG, "First subtab boundary reached, notify parent")
                        state.updateBoundaryEventTime()
                        eventBus.post(NestedPagerEventBus.Event.BoundaryReached(true, NestedPagerEventBus.BoundaryLevel.PARENT_TAB))
                        this@TabWithSubTabsViewHolder.onBoundaryReached?.invoke(true)
                    } else if (!isLeft && state.currentSubTabIndex == subTabs.size - 1) {
                        // 在最后一个子Tab向左滑动，切换到下一个父Tab
                        Log.d(TAG, "Last subtab boundary reached, notify parent")
                        state.updateBoundaryEventTime()
                        eventBus.post(NestedPagerEventBus.Event.BoundaryReached(false, NestedPagerEventBus.BoundaryLevel.PARENT_TAB))
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
        state.reset() // 使用状态管理重置

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
            // 用户点击子Tab时，使用事件总线发送切换事件
            if (position != state.currentSubTabIndex) {
                eventBus.post(NestedPagerEventBus.Event.SwitchToSubTabPosition(position, true))
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
                // 直接处理子Tab完成，确保切换逻辑正常工作
                val nextPosition = position + 1
                if (nextPosition < subTabs.size) {
                    // 还有下一个子tab，切换到下一个
                    Log.d(TAG, "Switching to next subtab: $nextPosition")
                    binding.subTabSwitchPager.setCurrentItem(nextPosition, true)
                } else {
                    // 这是最后一个子tab，应该已经由onAllSubTabsCompleted处理
                    Log.d(TAG, "Last subtab completed, should be handled by onAllSubTabsCompleted")
                }
            },
            onAllSubTabsCompleted = {
                Log.d(TAG, "onAllSubTabsCompleted - all subtabs finished")
                // 使用事件总线发送所有子Tab完成事件
                eventBus.post(NestedPagerEventBus.Event.AllSubTabsCompleted)
                onTabCompleted()
            },
            onPauseStateChanged = { isPaused ->
                // 使用事件总线发送暂停状态变更事件
                eventBus.post(NestedPagerEventBus.Event.PauseStateChanged(isPaused))
            },
            onSwitchToPrevious = { position ->
                Log.d(TAG, "onSwitchToPrevious: position=$position, currentSubTabIndex=${state.currentSubTabIndex}")
                // 直接处理上一个子Tab切换
                val prevPosition = position - 1
                if (prevPosition >= 0) {
                    // 还有上一个子tab，切换到上一个
                    Log.d(TAG, "Switching to previous subtab: $prevPosition")
                    binding.subTabSwitchPager.setCurrentItem(prevPosition, true)
                } else {
                    // 这是第一个子tab，通知父级切换到上一个父Tab
                    Log.d(TAG, "First subtab reached, notify parent to switch to previous tab")
                    onBoundaryReached?.invoke(true)
                }
            }
        )

        binding.subTabSwitchPager.adapter = subTabSwitchAdapter
        binding.subTabSwitchPager.setCurrentItem(0, false)

        Log.d(TAG, "SubTab adapter set with ${subTabs.size} subtabs")
    }

    /**
     * 子Tab切换时的处理
     */
    private fun onSubTabChanged(newPosition: Int) {
        Log.d(TAG, "onSubTabChanged: from=${state.currentSubTabIndex} to=$newPosition")
        if (newPosition == state.currentSubTabIndex) {
            Log.d(TAG, "Same position, no change needed")
            return
        }

        if (newPosition < 0 || newPosition >= subTabs.size) {
            Log.w(TAG, "Invalid position: $newPosition, subTabsSize=${subTabs.size}")
            return
        }

        // 使用状态管理更新当前子Tab索引
        state.switchToSubTab(newPosition)

        // 更新左侧选中状态
        subTabAdapter?.setSelectedPosition(newPosition)

        // 切换到新的子Tab
        subTabSwitchAdapter?.switchToSubTab(newPosition)

        // 同步adapter的currentPosition状态
        subTabSwitchAdapter?.setCurrentPosition(newPosition)

        // 通过事件总线发送暂停状态变更
        eventBus.post(NestedPagerEventBus.Event.PauseStateChanged(state.isPaused))

        Log.d(TAG, "SubTab changed to position $newPosition successfully")
    }

    // ========== ITabContentViewHolder接口实现 ==========

    /**
     * 切换暂停/播放
     */
    override fun togglePause() {
        Log.d(TAG, "togglePause: isPaused=${state.isPaused}")
        val newPauseState = !state.isPaused
        state.setPauseState(newPauseState)

        if (newPauseState) {
            subTabSwitchAdapter?.pauseAll()
        } else {
            subTabSwitchAdapter?.resumeCurrent()
        }

        eventBus.post(NestedPagerEventBus.Event.PauseStateChanged(newPauseState))
    }

    /**
     * 暂停轮播
     */
    override fun pause() {
        Log.d(TAG, "pause")
        if (!state.isPaused) {
            state.setPauseState(true)
            subTabSwitchAdapter?.pauseAll()
            eventBus.post(NestedPagerEventBus.Event.PauseStateChanged(true))
        }
    }

    /**
     * 恢复轮播
     */
    override fun resume() {
        Log.d(TAG, "resume")
        if (state.isPaused) {
            state.setPauseState(false)
            subTabSwitchAdapter?.resumeCurrent()
            eventBus.post(NestedPagerEventBus.Event.PauseStateChanged(false))
        }
    }

    /**
     * 释放资源
     */
    override fun release() {
        Log.d(TAG, "release")
        releaseAll()
    }

    /**
     * 获取暂停状态
     */
    override fun isPaused(): Boolean = state.isPaused

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
        return state.currentSubTabIndex == subTabs.size - 1
        // 注意：这里简化了逻辑，实际可以根据需要判断当前媒体是否也是最后一个
    }

    /**
     * 处理切换到下一个子Tab事件
     */
    private fun handleSwitchToNextSubTab(fromPosition: Int) {
        val nextPosition = fromPosition + 1
        if (nextPosition < subTabs.size) {
            Log.d(TAG, "Switching to next subtab: $nextPosition")
            binding.subTabSwitchPager.setCurrentItem(nextPosition, true)
        } else {
            Log.d(TAG, "Last subtab completed, switching to next parent tab")
            eventBus.post(NestedPagerEventBus.Event.SwitchToNextParentTab)
        }
    }

    /**
     * 处理切换到上一个子Tab事件
     */
    private fun handleSwitchToPreviousSubTab(fromPosition: Int) {
        val prevPosition = fromPosition - 1
        if (prevPosition >= 0) {
            Log.d(TAG, "Switching to previous subtab: $prevPosition")
            binding.subTabSwitchPager.setCurrentItem(prevPosition, true)
        } else {
            Log.d(TAG, "First subtab reached, switching to previous parent tab")
            eventBus.post(NestedPagerEventBus.Event.SwitchToPreviousParentTab)
            onBoundaryReached?.invoke(true)
        }
    }

    // ========== NestedViewPagerController接口实现 ==========

    override fun getCurrentState(): NestedViewPagerState = state

    override fun switchToNextSubTab(): Boolean {
        return if (state.currentSubTabIndex < subTabs.size - 1) {
            eventBus.post(NestedPagerEventBus.Event.SwitchToSubTabPosition(state.currentSubTabIndex + 1))
            true
        } else false
    }

    override fun switchToPreviousSubTab(): Boolean {
        return if (state.currentSubTabIndex > 0) {
            eventBus.post(NestedPagerEventBus.Event.SwitchToSubTabPosition(state.currentSubTabIndex - 1))
            true
        } else false
    }

    override fun switchToSubTabPosition(position: Int, smooth: Boolean): Boolean {
        return if (position in 0 until subTabs.size) {
            eventBus.post(NestedPagerEventBus.Event.SwitchToSubTabPosition(position, smooth))
            true
        } else false
    }

    override fun switchToNextParentTab(): Boolean {
        eventBus.post(NestedPagerEventBus.Event.SwitchToNextParentTab)
        return true
    }

    override fun switchToPreviousParentTab(): Boolean {
        eventBus.post(NestedPagerEventBus.Event.SwitchToPreviousParentTab)
        return true
    }

    override fun switchToParentTabPosition(position: Int, smooth: Boolean): Boolean {
        eventBus.post(NestedPagerEventBus.Event.SwitchToParentTabPosition(position, smooth))
        return true
    }

    override fun switchToNextMedia(): Boolean {
        eventBus.post(NestedPagerEventBus.Event.SwitchToNextMedia(state.currentSubTabIndex))
        return true
    }

    override fun switchToPreviousMedia(): Boolean {
        eventBus.post(NestedPagerEventBus.Event.SwitchToPreviousMedia(state.currentSubTabIndex))
        return true
    }

    // ========== SubTabViewPagerController接口实现 ==========

    override fun switchToPosition(position: Int, smooth: Boolean) {
        if (position in 0 until subTabs.size) {
            binding.subTabSwitchPager.setCurrentItem(position, smooth)
        }
    }

    override fun getCurrentPosition(): Int = state.currentSubTabIndex

    override fun getItemCount(): Int = subTabs.size

    override fun pauseAll() {
        state.setPauseState(true)
        subTabSwitchAdapter?.pauseAll()
    }

    override fun resumeCurrent() {
        state.setPauseState(false)
        subTabSwitchAdapter?.resumeCurrent()
    }

    override fun resetToFirst() {
        Log.d(TAG, "resetToFirst")
        if (subTabs.isEmpty()) return

        state.reset()

        // 更新左侧子Tab选中状态
        subTabAdapter?.setSelectedPosition(0)

        // 重置ViewPager到第一个子Tab
        binding.subTabSwitchPager.setCurrentItem(0, false)

        // 重置SubTabSwitchAdapter的currentPosition
        subTabSwitchAdapter?.resetCurrentPosition()
    }

    override fun reset() {
        Log.d(TAG, "reset")
        resetToFirst()
    }

    override fun releaseAll() {
        Log.d(TAG, "releaseAll")
        subTabSwitchAdapter?.releaseAll()
        // 清理状态管理中的监听器
        state.clearStateChangeListeners()
    }
}
