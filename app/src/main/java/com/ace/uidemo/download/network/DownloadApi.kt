package com.ace.uidemo.download.network

import com.ace.uidemo.model.CloudConfig
import retrofit2.http.GET

interface DownloadApi {
    @GET("config/media-config.json")  // 替换为实际接口路径
    suspend fun getConfig(): List<CloudConfig>
}
