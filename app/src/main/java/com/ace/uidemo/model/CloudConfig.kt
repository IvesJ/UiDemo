package com.ace.uidemo.model

import com.google.gson.annotations.SerializedName

data class CloudConfig(
    @SerializedName("TabTitle")
    val tabTitle: String,

    @SerializedName("filesInfo")
    val filesInfo: List<FileInfo> = emptyList(),

    @SerializedName("subTabs")
    val subTabs: List<CloudConfig> = emptyList()
)

data class FileInfo(
    @SerializedName("pageType")
    val pageType: String,  // "image" or "video"

    @SerializedName("pageRes")
    val pageRes: String,   // 本地文件名

    @SerializedName("pageUrl")
    val pageUrl: String,   // 下载URL

    @SerializedName("md5")
    val md5: String
)
