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
            addView(indicatorView)
        }
    }

    fun setCurrentPosition(position: Int) {
        if (position < 0 || position >= indicators.size) return

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
            indicators[position].progressBar.progress = progress.coerceIn(0, 100)
        }
    }

    fun getCurrentIndicatorView(): View? {
        return if (currentPosition >= 0 && currentPosition < indicators.size) {
            indicators[currentPosition].containerView
        } else {
            null
        }
    }

    private data class IndicatorItem(
        val containerView: View,
        val dotView: View,
        val progressBar: ProgressBar
    )
}
