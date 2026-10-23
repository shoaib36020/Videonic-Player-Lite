package com.videonic.lite.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.net.Uri

data class LocalVideo(val id: Long, val uri: Uri, val title: String, val duration: Long, val size: Long, val folder: String, val modified: Long)

class VideoRepository(private val context: Context) {
    fun load(): List<LocalVideo> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val columns = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DATE_MODIFIED)
        val videos = mutableListOf<LocalVideo>()
        context.contentResolver.query(collection, columns, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                videos.add(LocalVideo(id, ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "Video", if(cursor.isNull(durationCol)) 0 else cursor.getLong(durationCol), if(cursor.isNull(sizeCol)) 0 else cursor.getLong(sizeCol), cursor.getString(folderCol) ?: "Other", if(cursor.isNull(dateCol)) 0 else cursor.getLong(dateCol)))
            }
        }
        return videos
    }
}
