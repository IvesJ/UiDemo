package com.ace.uidemo

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ace.uidemo.databinding.ActivityMainBinding
import com.ace.uidemo.fragment.UnifiedMediaFragment
import com.ace.uidemo.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * MainActivity - 使用单个Fragment管理所有内容
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var unifiedFragment: UnifiedMediaFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "========== MainActivity onCreate ==========")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 显示loading状态
        showLoading()

        // 启动并绑定Service
        viewModel.bindDownloadService()

        // 观察ViewModel状态
        observeViewModel()
    }

    /**
     * 观察ViewModel的状态变化
     */
    private fun observeViewModel() {
        // 观察是否显示内容
        lifecycleScope.launch {
            viewModel.showContent.collect { showContent ->
                if (showContent) {
                    showContent()
                } else {
                    showLoading()
                }
            }
        }

        // 观察已完成的Tab列表
        lifecycleScope.launch {
            viewModel.completedTabs.collect { completedTabs ->
                if (completedTabs.isNotEmpty()) {
                    Log.d(TAG, "收到已完成Tab列表更新: ${completedTabs.size} 个Tab")
                    setupUnifiedFragment(completedTabs)
                }
            }
        }

        // 观察重试对话框
        lifecycleScope.launch {
            viewModel.showRetryDialog.collect { tabTitle ->
                tabTitle?.let {
                    showRetryDialog(it)
                }
            }
        }
    }

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.fragmentContainer.visibility = View.GONE
        Log.d(TAG, "显示Loading状态")
    }

    private fun showContent() {
        binding.loadingLayout.visibility = View.GONE
        binding.fragmentContainer.visibility = View.VISIBLE
        Log.d(TAG, "显示内容视图")
    }

    /**
     * 设置统一的Fragment
     */
    private fun setupUnifiedFragment(completedTabs: List<com.ace.uidemo.model.TabData>) {
        if (completedTabs.isEmpty()) {
            Log.w(TAG, "completedTabs为空，跳过setupUnifiedFragment")
            return
        }

        Log.d(TAG, "初始化UnifiedMediaFragment，${completedTabs.size} 个已完成Tab")

        // 如果Fragment已存在，先移除
        unifiedFragment?.let {
            supportFragmentManager.beginTransaction()
                .remove(it)
                .commitNow()
        }

        // 创建新的UnifiedMediaFragment
        unifiedFragment = UnifiedMediaFragment.newInstance(ArrayList(completedTabs))

        // 添加到容器
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, unifiedFragment!!)
            .commit()
    }

    private fun showRetryDialog(tabTitle: String) {
        AlertDialog.Builder(this)
            .setTitle("下载失败")
            .setMessage("Tab \"$tabTitle\" 的部分文件下载失败，是否重试？")
            .setPositiveButton("重试") { _, _ ->
                viewModel.retryFailedDownloads(tabTitle)
            }
            .setNegativeButton("取消") { _, _ ->
                viewModel.clearRetryDialog()
            }
            .setOnCancelListener {
                viewModel.clearRetryDialog()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "========== MainActivity onDestroy ==========")
    }
}
