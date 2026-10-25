package com.videonic.lite.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.net.Uri
import android.os.Build

data class LocalVideo(val id: Long, val uri: Uri, val title: String, val duration: Long,
    val size: Long, val folder: String, val modified: Long, val width:Int=0, val height:Int=0,
    val relativePath:String="")

class VideoRepository(private val context: Context) {
    fun load(): List<LocalVideo> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val prefs=context.getSharedPreferences("videonic",Context.MODE_PRIVATE)
        val dateOverrides=prefs.all.filterKeys { it.startsWith("restore_modified_") }
            .mapNotNull { (key,value) -> key.removePrefix("restore_modified_").toLongOrNull()
                ?.let { it to (value as? Long ?: return@mapNotNull null) } }.toMap()
        val consumedOverrides=mutableSetOf<Long>()
        val columns=mutableListOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,MediaStore.Video.Media.HEIGHT)
        if(Build.VERSION.SDK_INT>=29) columns+=MediaStore.MediaColumns.RELATIVE_PATH
        val videos = mutableListOf<LocalVideo>()
        context.contentResolver.query(collection, columns.toTypedArray(), null, null,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val relativePathCol=if(Build.VERSION.SDK_INT>=29)
                cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val modified=dateOverrides[id] ?: if(cursor.isNull(dateCol)) 0 else cursor.getLong(dateCol)
                if(id in dateOverrides) consumedOverrides+=id
                videos.add(LocalVideo(id, ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "Video", if(cursor.isNull(durationCol)) 0 else cursor.getLong(durationCol), if(cursor.isNull(sizeCol)) 0 else cursor.getLong(sizeCol), cursor.getString(folderCol) ?: "Other", modified,
                    if(widthCol<0 || cursor.isNull(widthCol))0 else cursor.getInt(widthCol),
                    if(heightCol<0 || cursor.isNull(heightCol))0 else cursor.getInt(heightCol),
                    if(relativePathCol<0 || cursor.isNull(relativePathCol))""
                        else cursor.getString(relativePathCol).orEmpty()))
            }
        }
        if(consumedOverrides.isNotEmpty()) prefs.edit().apply {
            consumedOverrides.forEach { remove("restore_modified_$it") }
        }.apply()
        return videos
    }
}
