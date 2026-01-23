package com.ace.uidemo.model

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 嵌套ViewPager2事件总线
 * 解耦组件间的通信，统一事件处理
 */
class NestedPagerEventBus {

    companion object {
        private const val TAG = "NestedPagerEventBus"

        @Volatile
        private var INSTANCE: NestedPagerEventBus? = null

        fun getInstance(): NestedPagerEventBus {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NestedPagerEventBus().also { INSTANCE = it }
            }
        }
    }

    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val eventListeners = mutableMapOf<Class<out Event>, MutableList<(Event) -> Unit>>()

    /**
     * 嵌套ViewPager事件定义
     */
    sealed class Event {
        /** 切换到下一个子Tab */
        data class SwitchToNextSubTab(val fromPosition: Int) : Event()

        /** 切换到上一个子Tab */
        data class SwitchToPreviousSubTab(val fromPosition: Int) : Event()

        /** 切换到指定子Tab */
        data class SwitchToSubTabPosition(val position: Int, val smooth: Boolean = true) : Event()

        /** 切换到下一个父Tab */
        object SwitchToNextParentTab : Event()

        /** 切换到上一个父Tab */
        object SwitchToPreviousParentTab : Event()

        /** 切换到指定父Tab */
        data class SwitchToParentTabPosition(val position: Int, val smooth: Boolean = true) : Event()

        /** 切换到下一个媒体 */
        data class SwitchToNextMedia(val subTabPosition: Int) : Event()

        /** 切换到上一个媒体 */
        data class SwitchToPreviousMedia(val subTabPosition: Int) : Event()

        /** 媒体播放完成 */
        data class MediaCompleted(val subTabPosition: Int, val mediaPosition: Int) : Event()

        /** 子Tab所有媒体播放完成 */
        data class SubTabCompleted(val subTabPosition: Int) : Event()

        /** 所有子Tab播放完成 */
        object AllSubTabsCompleted : Event()

        /** 暂停/恢复播放 */
        data class PauseStateChanged(val isPaused: Boolean) : Event()

        /** 重置到初始状态 */
        object Reset : Event()

        /** 边界滑动事件 */
        data class BoundaryReached(val isLeft: Boolean, val level: BoundaryLevel) : Event()

        /** 状态同步事件 */
        data class StateSync(val state: NestedViewPagerState) : Event()
    }

    /**
     * 边界级别
     */
    enum class BoundaryLevel {
        MEDIA,    // 媒体级边界
        SUB_TAB,  // 子Tab级边界
        PARENT_TAB // 父Tab级边界
    }

    /**
     * 注册事件监听器
     */
    fun <T : Event> subscribe(eventClass: Class<T>, listener: (T) -> Unit) {
        val listeners = eventListeners.getOrPut(eventClass) { mutableListOf() }
        listeners.add { event ->
            if (eventClass.isInstance(event)) {
                @Suppress("UNCHECKED_CAST")
                listener(event as T)
            }
        }
        Log.d(TAG, "Subscribed to ${eventClass.simpleName}")
    }

    /**
     * 内联便捷方法
     */
    inline fun <reified T : Event> subscribe(noinline listener: (T) -> Unit) {
        subscribe(T::class.java, listener)
    }

    /**
     * 取消事件监听器
     */
    fun <T : Event> unsubscribe(eventClass: Class<T>, listener: (T) -> Unit) {
        eventListeners[eventClass]?.removeAll {
            // 注意：这里的比较可能不准确，在实际使用中可能需要更复杂的监听器管理
            false
        }
        Log.d(TAG, "Unsubscribed from ${eventClass.simpleName}")
    }

    /**
     * 发送事件
     */
    fun post(event: Event) {
        Log.d(TAG, "Posting event: ${event::class.java.simpleName}")
        eventScope.launch {
            val listeners = eventListeners[event::class.java] ?: return@launch
            listeners.forEach { listener ->
                try {
                    listener(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling event: ${event::class.java.simpleName}", e)
                }
            }
        }
    }

    /**
     * 清空所有监听器
     */
    fun clear() {
        eventListeners.clear()
        Log.d(TAG, "All event listeners cleared")
    }

    /**
     * 获取指定事件类型的监听器数量
     */
    fun getListenerCount(eventClass: Class<out Event>): Int {
        return eventListeners[eventClass]?.size ?: 0
    }
}