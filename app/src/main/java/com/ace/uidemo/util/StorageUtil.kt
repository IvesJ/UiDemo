package com.ace.uidemo.util

import android.content.Context
import java.io.File

object StorageUtil {
    fun getMediaDirectory(context: Context): File {
        val mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }
        return mediaDir
    }

    fun getLocalFilePath(context: Context, fileName: String): String {
        return File(getMediaDirectory(context), fileName).absolutePath
    }

    fun clearCache(context: Context) {
        getMediaDirectory(context).deleteRecursively()
    }

    fun getCacheSize(context: Context): Long {
        return getMediaDirectory(context).walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}
