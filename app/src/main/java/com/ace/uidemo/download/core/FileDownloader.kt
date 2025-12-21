package com.ace.uidemo.download.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class FileDownloader(private val context: Context) {

    companion object {
        private const val TAG = "FileDownloader"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val activeDownloads = mutableSetOf<String>()

    suspend fun download(
        url: String,
        fileName: String,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "===== 开始文件下载 =====")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "文件名: $fileName")

        val mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) {
            val created = mediaDir.mkdirs()
            Log.d(TAG, "创建media目录: $created, 路径: ${mediaDir.absolutePath}")
        } else {
            Log.d(TAG, "media目录已存在: ${mediaDir.absolutePath}")
        }

        val file = File(mediaDir, fileName)
        Log.d(TAG, "目标文件路径: ${file.absolutePath}")

        activeDownloads.add(fileName)

        try {
            Log.d(TAG, "创建HTTP请求...")
            val request = Request.Builder().url(url).build()

            Log.d(TAG, "执行HTTP请求...")
            val response = client.newCall(request).execute()

            Log.d(TAG, "HTTP响应码: ${response.code}")
            if (!response.isSuccessful) {
                throw Exception("下载失败: HTTP ${response.code} - ${response.message}")
            }

            val body = response.body
            if (body == null) {
                throw Exception("响应体为空")
            }

            val totalBytes = body.contentLength()
            Log.d(TAG, "文件总大小: $totalBytes bytes (${totalBytes / 1024} KB)")

            Log.d(TAG, "开始写入文件...")
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    var bytes: Int
                    var lastLogProgress = 0

                    while (input.read(buffer).also { bytes = it } != -1) {
                        if (!activeDownloads.contains(fileName)) {
                            Log.w(TAG, "下载被取消: $fileName")
                            throw Exception("下载已取消")
                        }

                        output.write(buffer, 0, bytes)
                        downloadedBytes += bytes
                        onProgress(downloadedBytes, totalBytes)

                        // 每10%打印一次进度（避免日志过多）
                        val currentProgress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else 0

                        if (currentProgress >= lastLogProgress + 10) {
                            Log.d(TAG, "[$fileName] 写入进度: $currentProgress% (${downloadedBytes / 1024} KB / ${totalBytes / 1024} KB)")
                            lastLogProgress = currentProgress
                        }
                    }

                    Log.i(TAG, "[$fileName] ✓ 文件写入完成: ${downloadedBytes} bytes")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$fileName] ✗ 下载异常: ${e.message}", e)
            // 下载失败时删除不完整的文件
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "[$fileName] 删除不完整文件: $deleted")
            }
            throw e
        } finally {
            activeDownloads.remove(fileName)
            Log.d(TAG, "===== 文件下载结束 =====")
        }
    }

    fun cancelAll() {
        activeDownloads.clear()
    }
}
