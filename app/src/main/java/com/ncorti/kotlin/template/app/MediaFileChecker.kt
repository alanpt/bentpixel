package com.ncorti.kotlin.template.app

import android.content.Context
import android.util.Log
import java.io.File

object MediaFileChecker {

    private const val TAG = "MediaFileChecker"

    fun checkMediaFiles(context: Context, vjFolder: File) {
        Log.d(TAG, "Starting media file check...")

        if (!vjFolder.exists()) {
            Log.w(TAG, "VJ folder does not exist.")
            return
        }

        if (!vjFolder.isDirectory) {
            Log.e(TAG, "VJ folder is not a directory!")
            return
        }

        val files = vjFolder.listFiles()

        if (files.isNullOrEmpty()) {
            Log.w(TAG, "VJ folder is empty.")
            return
        }

        Log.d(TAG, "VJ folder contains ${files.size} files:")
        for (file in files) {
            Log.d(TAG, "  - ${file.name} (Type: ${getFileType(file)})")
        }
    }

    private fun getFileType(file: File): String {
        val lowerCaseName = file.name.lowercase()
        return when {
            lowerCaseName.endsWith(".png") -> "PNG Image"
            lowerCaseName.endsWith(".jpg") -> "JPG Image"
            lowerCaseName.endsWith(".jpeg") -> "JPEG Image"
            lowerCaseName.endsWith(".gif") -> "GIF Image"
            lowerCaseName.endsWith(".mp4") -> "MP4 Video"
            lowerCaseName.endsWith(".ogv") -> "OGV Video"
            else -> "Unknown"
        }
    }
}