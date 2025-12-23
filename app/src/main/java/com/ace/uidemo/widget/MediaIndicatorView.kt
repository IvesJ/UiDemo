package com.ace.uidemo.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.ace.uidemo.R

class MediaIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val indicators = mutableListOf<IndicatorItem>()
    private var currentPosition = -1

    init {
        orientation = HORIZONTAL
    }

    fun setupWithMediaItems(count: Int) {
        removeAllViews()
        indicators.clear()
        currentPosition = -1

        for (i in 0 until count) {
            val indicatorView = LayoutInflater.from(context)
                .inflate(R.layout.indicator_item, this, false)

            val dotView = indicatorView.findViewById<View>(R.id.dotView)
            val progressBar = indicatorView.findViewById<ProgressBar>(R.id.progressBar)

            indicators.add(IndicatorItem(indicatorView, dotView, progressBar))

            // 添加间距
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            if (i > 0) {
                params.leftMargin = dpToPx(4) // 4dp间距
            }
            indicatorView.layoutParams = params

            addView(indicatorView)
        }
    }

    fun setCurrentPosition(position: Int) {
        if (position < 0 || position >= indicators.size) {
            android.util.Log.d("MediaIndicatorView", "setCurrentPosition: invalid position=$position, size=${indicators.size}")
            return
        }

        android.util.Log.d("MediaIndicatorView", "setCurrentPosition: from $currentPosition to $position")

        // Hide progress bar and show dot for previous position
        if (currentPosition >= 0 && currentPosition < indicators.size) {
            indicators[currentPosition].apply {
                progressBar.visibility = View.INVISIBLE
                progressBar.progress = 0
                dotView.visibility = View.VISIBLE
            }
        }

        // Show progress bar and hide dot for current position
        currentPosition = position
        indicators[currentPosition].apply {
            dotView.visibility = View.INVISIBLE
            progressBar.visibility = View.VISIBLE
            progressBar.progress = 0
        }
    }

    fun updateProgress(position: Int, progress: Int) {
        if (position >= 0 && position < indicators.size) {
            android.util.Log.d("MediaIndicatorView", "updateProgress: position=$position, progress=$progress")
            indicators[position].progressBar.progress = progress.coerceIn(0, 100)
        }
    }

    /**
     * 获取当前指示器的View（用于滚动定位）
     */
    fun getCurrentIndicatorView(): View? {
        return if (currentPosition >= 0 && currentPosition < indicators.size) {
            indicators[currentPosition].containerView
        } else {
            null
        }
    }

    /**
     * 获取指定位置的当前进度
     */
    fun getCurrentProgress(position: Int): Int {
        return if (position >= 0 && position < indicators.size) {
            indicators[position].progressBar.progress
        } else {
            0
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private data class IndicatorItem(
        val containerView: View,
        val dotView: View,
        val progressBar: ProgressBar
    )
}
