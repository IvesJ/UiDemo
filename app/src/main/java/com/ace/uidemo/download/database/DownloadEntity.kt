package com.ace.uidemo.download.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val tabId: String,
    val fileName: String,
    val url: String,
    val md5: String,
    val state: String,
    val timestamp: Long
)
