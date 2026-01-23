package com.ace.uidemo.controller

import com.ace.uidemo.model.NestedViewPagerState
import com.ace.uidemo.model.MediaItem

/**
 * 嵌套ViewPager2控制器接口
 * 提供统一的控制抽象，便于测试和扩展
 */
interface NestedViewPagerController {

    /**
     * 获取当前状态
     */
    fun getCurrentState(): NestedViewPagerState

    /**
     * 切换到下一个子Tab
     * @return 是否切换成功
     */
    fun switchToNextSubTab(): Boolean

    /**
     * 切换到上一个子Tab
     * @return 是否切换成功
     */
    fun switchToPreviousSubTab(): Boolean

    /**
     * 切换到指定子Tab位置
     * @param position 目标位置
     * @param smooth 是否平滑滚动
     * @return 是否切换成功
     */
    fun switchToSubTabPosition(position: Int, smooth: Boolean = true): Boolean

    /**
     * 切换到下一个父Tab
     * @return 是否切换成功
     */
    fun switchToNextParentTab(): Boolean

    /**
     * 切换到上一个父Tab
     * @return 是否切换成功
     */
    fun switchToPreviousParentTab(): Boolean

    /**
     * 切换到指定父Tab位置
     * @param position 目标位置
     * @param smooth 是否平滑滚动
     * @return 是否切换成功
     */
    fun switchToParentTabPosition(position: Int, smooth: Boolean = true): Boolean

    /**
     * 切换到下一个媒体
     * @return 是否切换成功
     */
    fun switchToNextMedia(): Boolean

    /**
     * 切换到上一个媒体
     * @return 是否切换成功
     */
    fun switchToPreviousMedia(): Boolean

    /**
     * 暂停播放
     */
    fun pause()

    /**
     * 恢复播放
     */
    fun resume()

    /**
     * 切换暂停/播放状态
     */
    fun togglePause()

    /**
     * 重置到初始状态
     */
    fun reset()

    /**
     * 释放资源
     */
    fun release()

    /**
     * 获取当前媒体项
     */
    fun getCurrentMediaItem(): MediaItem?

    /**
     * 检查是否在最后一项
     */
    fun isAtLastItem(): Boolean

    /**
     * 检查是否暂停
     */
    fun isPaused(): Boolean
}

/**
 * 媒体ViewPager控制器接口
 */
interface MediaViewPagerController {

    /**
     * 切换到指定媒体位置
     * @param position 目标位置
     * @param smooth 是否平滑滚动
     */
    fun switchToPosition(position: Int, smooth: Boolean = true)

    /**
     * 获取当前媒体位置
     */
    fun getCurrentPosition(): Int

    /**
     * 获取媒体总数
     */
    fun getItemCount(): Int

    /**
     * 暂停当前媒体
     */
    fun pauseCurrentMedia()

    /**
     * 恢复当前媒体
     */
    fun resumeCurrentMedia()

    /**
     * 重置所有媒体
     */
    fun resetAllMedia()

    /**
     * 释放所有媒体资源
     */
    fun releaseAllMedia()
}

/**
 * 子Tab ViewPager控制器接口
 */
interface SubTabViewPagerController {

    /**
     * 切换到指定子Tab位置
     * @param position 目标位置
     * @param smooth 是否平滑滚动
     */
    fun switchToPosition(position: Int, smooth: Boolean = true)

    /**
     * 获取当前子Tab位置
     */
    fun getCurrentPosition(): Int

    /**
     * 获取子Tab总数
     */
    fun getItemCount(): Int

    /**
     * 暂停所有子Tab
     */
    fun pauseAll()

    /**
     * 恢复当前子Tab
     */
    fun resumeCurrent()

    /**
     * 重置到第一个子Tab
     */
    fun resetToFirst()

    /**
     * 释放所有资源
     */
    fun releaseAll()
}

/**
 * 滑动手势类型
 */
enum class SwipeDirection {
    LEFT, RIGHT, UP, DOWN
}

/**
 * 滑动类型
 */
enum class SwipeType {
    FAST, NORMAL, SLOW
}

/**
 * 手势分析器
 */
interface GestureAnalyzer {
    /**
     * 分析滑动速度类型
     */
    fun analyzeSwipeVelocity(velocityX: Float): SwipeType

    /**
     * 分析滑动方向
     */
    fun analyzeSwipeDirection(deltaX: Float, deltaY: Float): SwipeDirection

    /**
     * 检查是否为有效滑动
     */
    fun isValidSwipe(deltaX: Float, deltaY: Float, touchSlop: Float): Boolean
}