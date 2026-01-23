package com.ace.uidemo.widget

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.model.NestedPagerConfig
import kotlin.math.abs

/**
 * 自定义ViewPager2包装器，解决嵌套滑动冲突
 * 支持在边界时将滑动事件传递给父ViewPager2
 * 使用配置化参数提高可维护性
 */
class NestedViewPager2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "NestedViewPager2"
    }

    private val viewPager: ViewPager2 = ViewPager2(context)
    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = (ViewConfiguration.get(context).scaledTouchSlop * NestedPagerConfig.TOUCH_SLOP_MULTIPLIER).toInt()

    // 回调接口，用于通知父级进行切换
    var onBoundaryReached: ((isLeft: Boolean) -> Unit)? = null

    // 是否消费边界滑动事件（不传递给父级）
    var consumeBoundaryEvents = false

    init {
        addView(viewPager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        Log.d(TAG, "NestedViewPager2 created, touchSlop=$touchSlop")
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                parent.requestDisallowInterceptTouchEvent(true)
                Log.d(TAG, "ACTION_DOWN: x=${ev.x}, y=${ev.y}")
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - initialX
                val dy = ev.y - initialY

                Log.d(TAG, "ACTION_MOVE: dx=$dx, dy=$dy, touchSlop=$touchSlop")

                // 判断是否是横向滑动
                if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    val isScrollingLeft = dx > 0
                    val isScrollingRight = dx < 0

                    // 检查是否在边界
                    val isAtLeftBoundary = viewPager.currentItem == 0
                    val isAtRightBoundary = viewPager.currentItem == (viewPager.adapter?.itemCount ?: 0) - 1

                    Log.d(TAG, "Horizontal scroll detected: " +
                            "scrollLeft=$isScrollingLeft, scrollRight=$isScrollingRight, " +
                            "currentItem=${viewPager.currentItem}, " +
                            "itemCount=${viewPager.adapter?.itemCount}, " +
                            "leftBoundary=$isAtLeftBoundary, rightBoundary=$isAtRightBoundary, " +
                            "consumeBoundaryEvents=$consumeBoundaryEvents")

                    when {
                        isScrollingLeft && isAtLeftBoundary -> {
                            // 在左边界且向右滑动（想看前一个内容）
                            Log.d(TAG, "Left boundary + scrolling right: notify parent for previous")
                            onBoundaryReached?.invoke(true)

                            if (consumeBoundaryEvents) {
                                Log.d(TAG, "Consuming boundary event")
                                parent.requestDisallowInterceptTouchEvent(true)
                            } else {
                                Log.d(TAG, "Passing to parent")
                                parent.requestDisallowInterceptTouchEvent(false)
                                return false
                            }
                        }
                        isScrollingRight && isAtRightBoundary -> {
                            // 在右边界且向左滑动（想看下一个内容）
                            Log.d(TAG, "Right boundary + scrolling left: notify parent for next")
                            onBoundaryReached?.invoke(false)

                            if (consumeBoundaryEvents) {
                                Log.d(TAG, "Consuming boundary event")
                                parent.requestDisallowInterceptTouchEvent(true)
                            } else {
                                Log.d(TAG, "Passing to parent")
                                parent.requestDisallowInterceptTouchEvent(false)
                                return false
                            }
                        }
                        else -> {
                            // 非边界情况，拦截事件自己处理
                            Log.d(TAG, "Non-boundary, handling internally")
                            parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                Log.d(TAG, "ACTION_UP/CANCEL")
            }
        }
        return viewPager.onTouchEvent(ev)
    }

    // 代理ViewPager2的方法
    var adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>?
        get() = viewPager.adapter
        set(value) {
            Log.d(TAG, "Setting adapter: itemCount=${value?.itemCount}")
            viewPager.adapter = value
        }

    var currentItem: Int
        get() = viewPager.currentItem
        set(value) {
            Log.d(TAG, "Setting currentItem: $value")
            viewPager.currentItem = value
        }

    var offscreenPageLimit: Int
        get() = viewPager.offscreenPageLimit
        set(value) {
            Log.d(TAG, "Setting offscreenPageLimit: $value")
            viewPager.offscreenPageLimit = value
        }

    fun setCurrentItem(item: Int, smoothScroll: Boolean = true) {
        Log.d(TAG, "setCurrentItem: item=$item, smooth=$smoothScroll")
        viewPager.setCurrentItem(item, smoothScroll)
    }

    fun registerOnPageChangeCallback(callback: ViewPager2.OnPageChangeCallback) {
        Log.d(TAG, "registerOnPageChangeCallback")
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)
                callback.onPageScrolled(position, positionOffset, positionOffsetPixels)
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                Log.d(TAG, "onPageSelected: position=$position")
                callback.onPageSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                Log.d(TAG, "onPageScrollStateChanged: state=$state")
                callback.onPageScrollStateChanged(state)
            }
        })
    }

    fun unregisterOnPageChangeCallback(callback: ViewPager2.OnPageChangeCallback) {
        Log.d(TAG, "unregisterOnPageChangeCallback")
        viewPager.unregisterOnPageChangeCallback(callback)
    }

    /**
     * 检查是否在左边界
     */
    fun isAtLeftBoundary(): Boolean = viewPager.currentItem == 0

    /**
     * 检查是否在右边界
     */
    fun isAtRightBoundary(): Boolean = viewPager.currentItem == (viewPager.adapter?.itemCount ?: 0) - 1
}