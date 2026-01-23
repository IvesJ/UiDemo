package com.ace.uidemo.model

import android.util.Log

/**
 * 嵌套ViewPager2状态管理
 * 统一管理当前的tab、子tab、媒体位置等状态
 */
data class NestedViewPagerState(
    /** 当前父Tab索引 */
    var currentTabIndex: Int = 0,

    /** 当前子Tab索引 */
    var currentSubTabIndex: Int = 0,

    /** 当前媒体索引 */
    var currentMediaIndex: Int = 0,

    /** 当前是否处于暂停状态 */
    var isPaused: Boolean = false,

    /** 上次边界事件时间戳 */
    var lastBoundaryEventTime: Long = 0L,

    /** 上次完成事件时间戳 */
    var lastCompletionTime: Long = 0L
) {

    companion object {
        private const val TAG = "NestedViewPagerState"
    }

    /**
     * 状态变更监听器
     */
    private val stateChangeListeners = mutableListOf<(NestedViewPagerState) -> Unit>()

    /**
     * 添加状态变更监听器
     */
    fun addStateChangeListener(listener: (NestedViewPagerState) -> Unit) {
        stateChangeListeners.add(listener)
    }

    /**
     * 移除状态变更监听器
     */
    fun removeStateChangeListener(listener: (NestedViewPagerState) -> Unit) {
        stateChangeListeners.remove(listener)
    }

    /**
     * 通知状态变更
     */
    fun notifyStateChanged() {
        Log.d(TAG, "State changed: tab=$currentTabIndex, subTab=$currentSubTabIndex, media=$currentMediaIndex, paused=$isPaused")
        stateChangeListeners.forEach { it(this) }
    }

    /**
     * 切换到指定父Tab
     */
    fun switchToTab(tabIndex: Int) {
        if (tabIndex != currentTabIndex) {
            currentTabIndex = tabIndex
            currentSubTabIndex = 0 // 重置子Tab到第一个
            currentMediaIndex = 0   // 重置媒体到第一个
            notifyStateChanged()
        }
    }

    /**
     * 切换到指定子Tab
     */
    fun switchToSubTab(subTabIndex: Int) {
        if (subTabIndex != currentSubTabIndex) {
            currentSubTabIndex = subTabIndex
            currentMediaIndex = 0 // 重置媒体到第一个
            notifyStateChanged()
        }
    }

    /**
     * 切换到指定媒体
     */
    fun switchToMedia(mediaIndex: Int) {
        if (mediaIndex != currentMediaIndex) {
            currentMediaIndex = mediaIndex
            notifyStateChanged()
        }
    }

    /**
     * 设置暂停状态
     */
    fun setPauseState(paused: Boolean) {
        if (paused != isPaused) {
            isPaused = paused
            notifyStateChanged()
        }
    }

    /**
     * 重置到初始状态
     */
    fun reset() {
        currentTabIndex = 0
        currentSubTabIndex = 0
        currentMediaIndex = 0
        isPaused = false
        lastBoundaryEventTime = 0L
        lastCompletionTime = 0L
        notifyStateChanged()
    }

    /**
     * 检查是否可以进行边界事件处理（防抖检查）
     */
    fun canHandleBoundaryEvent(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastBoundaryEventTime >= NestedPagerConfig.DEBOUNCE_TIME_MS
    }

    /**
     * 更新边界事件时间戳
     */
    fun updateBoundaryEventTime() {
        lastBoundaryEventTime = System.currentTimeMillis()
    }

    /**
     * 检查是否可以进行完成事件处理（防抖检查）
     */
    fun canHandleCompletionEvent(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime - lastCompletionTime >= NestedPagerConfig.DEBOUNCE_TIME_MS
    }

    /**
     * 更新完成事件时间戳
     */
    fun updateCompletionEventTime() {
        lastCompletionTime = System.currentTimeMillis()
    }

    /**
     * 清理所有状态变更监听器
     */
    fun clearStateChangeListeners() {
        stateChangeListeners.clear()
        Log.d(TAG, "All state change listeners cleared")
    }

    /**
     * 获取当前状态的字符串表示
     */
    override fun toString(): String {
        return "NestedViewPagerState(tab=$currentTabIndex, subTab=$currentSubTabIndex, media=$currentMediaIndex, paused=$isPaused)"
    }
}