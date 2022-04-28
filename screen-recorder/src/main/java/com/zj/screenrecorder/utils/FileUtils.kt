package com.zj.screenrecorder.utils

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileUtils
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.net.URLConnection
import kotlin.coroutines.resume


object FileUtils {

    @Suppress("DEPRECATION")
    private fun getRealPathFromUri(context: Context?, contentUri: Uri?): String? {
        var cursor: Cursor? = null
        if (context == null || contentUri == null) return null
        return try {
            val pj = arrayOf(MediaStore.Video.Media.DATA)
            cursor = context.contentResolver.query(contentUri, pj, null, null, null)
            if (cursor == null || !cursor.moveToFirst()) return ""
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            cursor.moveToFirst()
            cursor.getString(columnIndex)
        } finally {
            cursor?.close()
        }
    }

    fun notifyVideosToAlbum(context: Context?, path: String, contentTypeBackWard: String? = null, onSaveResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = saveAndNotifyVideoToAlbum(context, path, contentTypeBackWard)
            CoroutineScope(Dispatchers.Main).launch {
                onSaveResult(result)
            }
        }
    }

    private suspend fun saveAndNotifyVideoToAlbum(context: Context?, path: String, contentTypeBackWard: String?) = suspendCancellableCoroutine<Boolean> {
        val file = File(path)
        val mimeType = URLConnection.getFileNameMap().getContentTypeFor(file.name) ?: contentTypeBackWard
        if (!file.exists() || file.isDirectory) {
            it.resume(false)
            return@suspendCancellableCoroutine
        }
        val resolver = context?.contentResolver
        val values = ContentValues()
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        values.put(MediaStore.Video.Media.DESCRIPTION, file.name)
        values.put(MediaStore.Video.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        val external = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        try {
            val uri = resolver?.insert(external, values)?.also { uri ->
                val fis = file.inputStream()
                val fos = resolver.openOutputStream(uri, "w") ?: throw NullPointerException()
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        FileUtils.copy(fis, fos)
                    } else {
                        val buffer = ByteArray(1024)
                        var len: Int
                        while (fis.read(buffer).also { l -> len = l } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                } finally {
                    fos.close()
                    fis.close()
                }
            }
            val uriPath = getRealPathFromUri(context, uri)
            notifyWithScanner(context, uriPath, mimeType) { b ->
                it.resume(b)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            it.resume(false)
        }
    }


    private fun notifyWithScanner(context: Context?, path: String?, mimeType: String?, onSaveResult: (Boolean) -> Unit) {
        var connection: MediaScannerConnection? = null
        val l = object : MediaScannerConnection.MediaScannerConnectionClient {
            override fun onScanCompleted(path: String?, uri: Uri?) {
                onSaveResult(true)
            }

            override fun onMediaScannerConnected() {
                if (connection?.isConnected == true) {
                    connection?.scanFile(path, mimeType)
                } else {
                    onSaveResult(false)
                }
            }
        }
        connection = MediaScannerConnection(context, l)
        connection.connect()
    }
}