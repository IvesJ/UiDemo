package com.ace.uidemo.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ace.uidemo.download.core.DownloadManager
import com.ace.uidemo.download.model.DownloadProgress
import com.ace.uidemo.model.CloudConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ResourceDownloadService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var downloadManager: DownloadManager
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress

    private val _config = MutableStateFlow<List<CloudConfig>>(emptyList())
    val config: StateFlow<List<CloudConfig>> = _config

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "download_channel"
    }

    inner class LocalBinder : Binder() {
        fun getService(): ResourceDownloadService = this@ResourceDownloadService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== Service onCreate ==========")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("准备下载..."))
        Log.d(TAG, "前台Service已启动")

        downloadManager = DownloadManager(applicationContext)
        observeDownloadProgress()
        Log.d(TAG, "DownloadManager已初始化")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            try {
                downloadManager.startDownload()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun observeDownloadProgress() {
        // 监听配置
        serviceScope.launch {
            downloadManager.configFlow.collect { configs ->
                _config.value = configs
            }
        }

        // 监听下载进度
        serviceScope.launch {
            downloadManager.progressFlow.collect { progressMap ->
                _downloadProgress.value = progressMap
                updateNotification(progressMap)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "资源下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "媒体资源下载进度"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("资源下载")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(progressMap: Map<String, DownloadProgress>) {
        val totalFiles = progressMap.size
        val completedFiles = progressMap.values.count { it.isCompleted }
        val overallProgress = if (totalFiles > 0) {
            (completedFiles * 100) / totalFiles
        } else 0

        val notification = createNotification("下载进度: $completedFiles/$totalFiles ($overallProgress%)")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun retryFailedDownloads(tabId: String) {
        Log.d(TAG, "收到重试请求: Tab=$tabId")
        serviceScope.launch {
            try {
                downloadManager.retryTab(tabId)
                Log.d(TAG, "重试任务完成: Tab=$tabId")
            } catch (e: Exception) {
                Log.e(TAG, "重试任务异常: Tab=$tabId", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "========== Service onDestroy ==========")
        downloadManager.cancelAll()
        Log.d(TAG, "所有下载已取消")
    }
}
