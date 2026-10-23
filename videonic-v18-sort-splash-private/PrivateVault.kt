package com.videonic.lite.security

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
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
    data class Entry(val id:String, val name:String, val size:Long)
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
    fun entries():List<Entry> {
        val a=JSONArray(prefs.getString("entries","[]"))
        return (0 until a.length()).mapNotNull { i->runCatching {
            val o=a.getJSONObject(i);Entry(o.getString("id"),o.getString("name"),o.getLong("size"))
        }.getOrNull() }.filter{File(root,"${it.id}.enc").isFile}
    }
    @Synchronized private fun put(entries:List<Entry>) {
        val a=JSONArray();entries.forEach{a.put(JSONObject().put("id",it.id).put("name",it.name).put("size",it.size))}
        prefs.edit().putString("entries",a.toString()).commit()
    }
    fun importVideo(uri:Uri,name:String,key:ByteArray):Entry {
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
            val entry=Entry(id,name.take(180).ifBlank{"Private video"},total)
            put(entries()+entry)
            return entry
        } catch(e:Exception){file.delete();throw e}
    }
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
        try {
            val input=File(root,"${entry.id}.enc")
            input.inputStream().buffered().use { raw ->
                val iv=ByteArray(12)
                check(raw.read(iv)==12){"Invalid encrypted video"}
                val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))}
                CipherInputStream(raw,cipher).use{decrypted->output.outputStream().buffered().use{decrypted.copyTo(it)}}
            }
            return output
        } catch(e:Exception){output.delete();throw e}
    }
    fun remove(entry:Entry){ check(File(root,"${entry.id}.enc").delete()){ "Unable to remove encrypted file" };put(entries().filterNot{it.id==entry.id}) }
    fun exportVideo(entry:Entry,key:ByteArray,uri:Uri){
        val raw=File(root,"${entry.id}.enc").inputStream().buffered()
        raw.use{input->
            val iv=ByteArray(12);check(input.read(iv)==12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv))}
            CipherInputStream(input,cipher).use{plain->context.contentResolver.openOutputStream(uri)?.use{plain.copyTo(it)}?:error("Cannot open export location")}
        }
    }
    fun cleanPlaybackCache() {context.cacheDir.listFiles()?.filter{it.name.startsWith("videonic_private_")}?.forEach{it.delete()} }
}
