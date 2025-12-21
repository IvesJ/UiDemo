package com.ace.uidemo.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.ace.uidemo.download.model.DownloadProgress
import com.ace.uidemo.download.service.ResourceDownloadService
import com.ace.uidemo.model.CloudConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 下载数据仓库
 * 负责封装与ResourceDownloadService的交互，提供统一的数据访问接口
 */
class DownloadRepository(private val context: Context) {

    companion object {
        private const val TAG = "DownloadRepository"
    }

    private var downloadService: ResourceDownloadService? = null
    private var serviceBound = false

    // 配置数据流
    private val _configFlow = MutableStateFlow<List<CloudConfig>>(emptyList())
    val configFlow: StateFlow<List<CloudConfig>> = _configFlow.asStateFlow()

    // 下载进度数据流
    private val _progressFlow = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progressFlow: StateFlow<Map<String, DownloadProgress>> = _progressFlow.asStateFlow()

    // Service连接状态
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service已连接")
            val binder = service as ResourceDownloadService.LocalBinder
            downloadService = binder.getService()
            serviceBound = true
            _serviceConnected.value = true
            Log.d(TAG, "开始监听配置和下载进度")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Service已断开连接")
            downloadService = null
            serviceBound = false
            _serviceConnected.value = false
        }
    }

    /**
     * 绑定下载Service
     */
    fun bindService() {
        Log.d(TAG, "启动并绑定下载Service...")
        val intent = Intent(context, ResourceDownloadService::class.java)

        // 1. 先启动Service（触发onStartCommand开始下载）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
            Log.d(TAG, "startForegroundService已调用")
        } else {
            context.startService(intent)
            Log.d(TAG, "startService已调用")
        }

        // 2. 再绑定Service（用于通信）
        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "bindService结果: $bound")
    }

    /**
     * 解绑Service
     */
    fun unbindService() {
        if (serviceBound) {
            Log.d(TAG, "解绑Service")
            context.unbindService(serviceConnection)
            serviceBound = false
            _serviceConnected.value = false
        }
    }

    /**
     * 获取配置Flow（需要在Service连接后调用）
     */
    fun observeConfig(): StateFlow<List<CloudConfig>>? {
        return downloadService?.config
    }

    /**
     * 获取下载进度Flow（需要在Service连接后调用）
     */
    fun observeProgress(): StateFlow<Map<String, DownloadProgress>>? {
        return downloadService?.downloadProgress
    }

    /**
     * 重试失败的下载
     */
    fun retryFailedDownloads(tabId: String) {
        downloadService?.retryFailedDownloads(tabId)
    }
}
