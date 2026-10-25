package com.videonic.lite.security

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.util.Base64
import android.provider.MediaStore
import android.content.ContentUris
import android.os.Build
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** User-PIN-derived AES-256-GCM vault. No PIN/key is persisted. */
class PrivateVault(private val context: Context) {
    data class Entry(val id:String, val name:String, val size:Long,val trashed:Boolean=false,
        val modified:Long=0L,val relativePath:String="")
    private val prefs=context.getSharedPreferences("videonic_vault",Context.MODE_PRIVATE)
    private val root=File(context.filesDir,"private_videos").apply{mkdirs()}
    private val random=SecureRandom()
    private fun bytes(size:Int)=ByteArray(size).also{random.nextBytes(it)}
    private fun encode(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
    private fun decode(s:String)=Base64.decode(s,Base64.NO_WRAP)
    fun configured()=prefs.contains("salt")
    // Existing folders created before 4-digit setup retain their old PIN lengths.
    fun pinLengthHint():Int=prefs.getInt("pin_digits",6)
    private fun derive(pin:String, salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(pin.toCharArray(),salt,180_000,256)
        return try {SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded} finally {spec.clearPassword()}
    }
    fun setup(pin:String):Boolean {
        if(configured() || pin.length != 4 || !pin.all(Char::isDigit)) return false
        val salt=bytes(16)
        val key=derive(pin,salt)
        val verifier=MessageDigest.getInstance("SHA-256").digest(key+"videonic-vault-check-v1".toByteArray())
        prefs.edit().putString("salt",encode(salt)).putString("verifier",encode(verifier)).putInt("pin_digits",4).apply()
        key.fill(0)
        return true
    }
    fun unlock(pin:String):ByteArray? {
        if(!configured()) return null
        val key=derive(pin,decode(prefs.getString("salt","")!!))
        val verifier=MessageDigest.getInstance("SHA-256").digest(key+"videonic-vault-check-v1".toByteArray())
        return if(MessageDigest.isEqual(verifier,decode(prefs.getString("verifier","")!!))) key else {key.fill(0);null}
    }
    private fun storedEntries():List<Entry> {
        val a=JSONArray(prefs.getString("entries","[]"))
        return (0 until a.length()).mapNotNull { i->runCatching {
            val o=a.getJSONObject(i);Entry(o.getString("id"),o.getString("name"),o.getLong("size"),
                o.optBoolean("trashed",false),o.optLong("modified",0L),o.optString("relativePath",""))
        }.getOrNull() }.filter{File(root,"${it.id}.enc").isFile}
    }
    fun entries():List<Entry> = storedEntries()
    fun trashedEntries():List<Entry> = storedEntries().filter { it.trashed }
    @Synchronized private fun put(entries:List<Entry>) {
        val a=JSONArray();entries.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name)
            .put("size",it.size).put("trashed",it.trashed).put("modified",it.modified)
            .put("relativePath",it.relativePath))}
        prefs.edit().putString("entries",a.toString()).commit()
    }
    @Synchronized fun moveToTrash(entry:Entry) {
        val all=storedEntries()
        if(all.none { it.id==entry.id }) error("Private video no longer exists")
        put(all.map { if(it.id==entry.id)it.copy(trashed=true) else it })
    }
    @Synchronized fun restore(entry:Entry) {
        val all=storedEntries()
        if(all.none { it.id==entry.id }) error("Private video no longer exists")
        put(all.map { if(it.id==entry.id)it.copy(trashed=false) else it })
    }
    fun importVideo(uri:Uri,name:String,key:ByteArray,modified:Long=0L,
        relativePath:String=""):Entry {
        val id=java.util.UUID.randomUUID().toString()
        val file=File(root,"$id.enc")
        val iv=bytes(12)
        var total=0L
        try {
            context.contentResolver.openInputStream(uri)?.use { source->
                file.outputStream().buffered().use{raw->
                    raw.write(iv)
                    val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))}
                    CipherOutputStream(raw,cipher).use { encrypted ->
                        val buffer=ByteArray(65536)
                        while(true){val n=source.read(buffer);if(n<0)break;encrypted.write(buffer,0,n);total+=n}
                    }
                }
            } ?: error("Unable to read selected video")
            check(total>0L) { "Selected video is empty or unavailable" }
            val entry=Entry(id,name.take(180).ifBlank{"Private video"},total,
                modified=modified.takeIf { it>0L } ?: System.currentTimeMillis()/1000L,
                relativePath=relativePath)
            put(storedEntries()+entry)
            runCatching { storeThumbnail(uri,id,key) }
                .onFailure { android.util.Log.w("PrivateVault","Encrypted thumbnail generation failed for $name",it) }
            return entry
        } catch(e:Exception){file.delete();throw e}
    }
    private fun thumbnailFile(id:String)=File(root,"$id.thumb.enc")
    private fun storeThumbnail(uri:Uri,id:String,key:ByteArray) {
        val bitmap=if(Build.VERSION.SDK_INT>=29) {
            context.contentResolver.loadThumbnail(uri,android.util.Size(480,320),null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver,
                ContentUris.parseId(uri),MediaStore.Video.Thumbnails.MINI_KIND,null)
        } ?: return
        val plain=ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG,75,output)
            output.toByteArray()
        }
        bitmap.recycle()
        val iv=bytes(12)
        val encrypted=Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))
            doFinal(plain)
        }
        plain.fill(0)
        thumbnailFile(id).outputStream().buffered().use { output ->
            output.write(iv)
            output.write(encrypted)
        }
        encrypted.fill(0)
    }
    fun loadThumbnail(entry:Entry,key:ByteArray):Bitmap?=runCatching {
        val file=thumbnailFile(entry.id)
        if(!file.isFile || file.length()<=28L) return@runCatching null
        val encrypted=file.readBytes()
        val iv=encrypted.copyOfRange(0,12)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))
        }
        val plain=cipher.doFinal(encrypted,12,encrypted.size-12)
        encrypted.fill(0)
        BitmapFactory.decodeByteArray(plain,0,plain.size).also { plain.fill(0) }
    }.getOrNull()
    /** Decrypt and authenticate the complete encrypted file before considering removal of the source. */
    fun verifyEncryptedEntry(entry:Entry,key:ByteArray):Boolean {
        val file=File(root,"${entry.id}.enc")
        if(!file.isFile || file.length()<=28L) return false
        val input=file.inputStream().buffered()
        input.use { raw ->
            val iv=ByteArray(12)
            if(raw.read(iv)!=12) return false
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))
            var count=0L
            CipherInputStream(raw,cipher).use { plain ->
                val buffer=ByteArray(65536)
                while(true) {
                    val n=plain.read(buffer)
                    if(n<0) break
                    count+=n
                }
            }
            return count==entry.size && count>0L
        }
    }

    /** Playback uses a temporary plaintext cache file: erased by the screen's close action.
     * Android can retain plaintext cache after a process crash. Don't promise absolute secrecy. */
    fun preparePlayback(entry:Entry,key:ByteArray):File {
        val sourceExtension=entry.name.substringAfterLast('.',"mp4").lowercase()
            .takeIf { it in setOf("mp4","mkv","webm","mov","m4v","avi","3gp","ts") } ?: "mp4"
        val output=File(context.cacheDir,"videonic_private_${entry.id}.$sourceExtension")
        if(output.isFile && output.length()==entry.size) return output
        val staging=File(context.cacheDir,"videonic_private_${entry.id}.$sourceExtension.part")
        try {
            val input=File(root,"${entry.id}.enc")
            input.inputStream().buffered().use { raw ->
                val iv=ByteArray(12)
                check(raw.read(iv)==12){"Invalid encrypted video"}
                val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))}
                CipherInputStream(raw,cipher).use{decrypted->
                    staging.outputStream().buffered(1024*1024).use{decrypted.copyTo(it,1024*1024)}
                }
            }
            check(staging.length()==entry.size){"Decrypted video size does not match the vault entry"}
            check(staging.renameTo(output)){"Unable to finalize private playback cache"}
            return output
        } catch(e:Exception){staging.delete();throw e}
    }
    @Synchronized fun remove(entry:Entry) {
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("videonic_private_${entry.id}.") }
            ?.forEach { cached ->
                check(cached.delete() || !cached.exists()) { "Unable to remove private playback cache" }
            }
        val file=File(root,"${entry.id}.enc")
        check(file.delete()) { "Unable to remove encrypted file" }
        val thumbnail=thumbnailFile(entry.id)
        check(thumbnail.delete() || !thumbnail.exists()) { "Unable to remove encrypted thumbnail" }
        put(storedEntries().filterNot { it.id==entry.id })
    }
    fun exportVideo(entry:Entry,key:ByteArray,uri:Uri){
        fun preserveMediaStoreDate() {
            if(entry.modified>0L) {
                val mediaUri=when {
                    uri.authority=="media" -> uri
                    android.os.Build.VERSION.SDK_INT>=29 -> runCatching {
                        MediaStore.getMediaUri(context,uri)
                    }.getOrNull()
                    else -> null
                } ?: return
                runCatching {
                    context.contentResolver.update(mediaUri,ContentValues().apply {
                        put(MediaStore.MediaColumns.DATE_MODIFIED,entry.modified)
                    },null,null)
                    val mediaId=ContentUris.parseId(mediaUri)
                    context.getSharedPreferences("videonic",Context.MODE_PRIVATE).edit()
                        .putLong("restore_modified_$mediaId",entry.modified).apply()
                }
            }
        }
        val extension=entry.name.substringAfterLast('.',"mp4").lowercase()
            .takeIf { it in setOf("mp4","mkv","webm","mov","m4v","avi","3gp","ts") } ?: "mp4"
        val cached=File(context.cacheDir,"videonic_private_${entry.id}.$extension")
        if(cached.isFile && cached.length()==entry.size) {
            cached.inputStream().buffered(1024*1024).use { plain ->
                context.contentResolver.openOutputStream(uri)?.buffered(1024*1024)?.use {
                    plain.copyTo(it,1024*1024)
                } ?: error("Cannot open export location")
            }
            preserveMediaStoreDate()
            return
        }
        val raw=File(root,"${entry.id}.enc").inputStream().buffered()
        raw.use{input->
            val iv=ByteArray(12);check(input.read(iv)==12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))}
                CipherInputStream(input,cipher).buffered(1024*1024).use{plain->
                    context.contentResolver.openOutputStream(uri)?.buffered(1024*1024)?.use{
                        plain.copyTo(it,1024*1024)
                    } ?: error("Cannot open export location")
                }
                preserveMediaStoreDate()
        }
    }
    fun unlockVideo(entry:Entry,key:ByteArray):Uri {
        check(Build.VERSION.SDK_INT>=29) { "Unlocking to the original folder requires Android 10 or newer" }
        val extension=entry.name.substringAfterLast('.',"mp4").lowercase()
            .takeIf { it in setOf("mp4","mkv","webm","mov","m4v","avi","3gp","ts") } ?: "mp4"
        val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "video/mp4"
        val segments=entry.relativePath.split('/').filter { it.isNotBlank() && it!="." && it!=".." }
        val allowedRoots=setOf(Environment.DIRECTORY_DCIM,Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_PICTURES)
        val relativePath=if(segments.firstOrNull() in allowedRoots) {
            segments.joinToString(separator="/",postfix="/")
        } else {
            val folder=segments.lastOrNull()?.replace(Regex("[^A-Za-z0-9._ -]"),"_")
                ?.takeIf { it.isNotBlank() && it!="." && it!=".." } ?: "Restored"
            "${Environment.DIRECTORY_MOVIES}/Videonic/$folder/"
        }
        val values=ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,entry.name)
            put(MediaStore.Video.Media.MIME_TYPE,mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,relativePath)
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val fallbackPath="${Environment.DIRECTORY_MOVIES}/Videonic/"
        var destination=runCatching {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)
        }.getOrNull()
        if(destination==null && relativePath!=fallbackPath) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,fallbackPath)
            destination=resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values)
        }
        destination ?: error("Could not create the unlocked video in a valid media folder")
        try {
            exportVideo(entry,key,destination)
            check(resolver.update(destination,ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING,0)
            },null,null)>0) { "Could not publish the unlocked video" }
            if(entry.modified>0L) resolver.update(destination,ContentValues().apply {
                put(MediaStore.MediaColumns.DATE_MODIFIED,entry.modified)
            },null,null)
            remove(entry)
            return destination
        } catch(error:Exception) {
            runCatching { context.contentResolver.delete(destination,null,null) }
                .onFailure(error::addSuppressed)
            throw error
        }
    }
    fun cleanPlaybackCache() {context.cacheDir.listFiles()?.filter{it.name.startsWith("videonic_private_")}?.forEach{it.delete()} }
}
