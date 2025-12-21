package com.ace.uidemo.download.core

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object MD5Util {
    fun calculateMD5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytes: Int
            while (fis.read(buffer).also { bytes = it } != -1) {
                digest.update(buffer, 0, bytes)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
