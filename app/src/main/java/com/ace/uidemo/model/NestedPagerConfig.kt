package com.ace.uidemo.model

/**
 * 嵌套ViewPager2配置参数
 * 统一管理所有可配置的参数，避免硬编码
 */
object NestedPagerConfig {
    /** 防抖时间间隔（毫秒） */
    const val DEBOUNCE_TIME_MS = 500L

    /** 切换动画持续时间（毫秒） */
    const val ANIMATION_DURATION_MS = 300L

    /** ViewPager2离屏页面限制 */
    const val OFFSCREEN_PAGE_LIMIT = 1

    /** 触摸滑动阈值 */
    const val TOUCH_SLOP_MULTIPLIER = 1.5f

    /** 快速滑动速度阈值 */
    const val FAST_SWIPE_THRESHOLD = 3000f

    /** 普通滑动速度阈值 */
    const val NORMAL_SWIPE_THRESHOLD = 1000f

    /** ViewHolder复用池大小 */
    const val VIEW_HOLDER_POOL_SIZE = 5

    /** 自动轮播间隔（毫秒） */
    const val AUTO_SCROLL_INTERVAL_MS = 5000L

    /** 视频播放进度更新间隔（毫秒） */
    const val PROGRESS_UPDATE_INTERVAL_MS = 16L
}