package com.videonic.lite.data

import android.content.Context
import org.json.JSONArray

/** Local-only metadata. Does not modify video files. */
class LocalLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("videonic", Context.MODE_PRIVATE)
    fun grid(): Boolean = prefs.getBoolean("grid", false)
    fun setGrid(enabled: Boolean) { prefs.edit().putBoolean("grid", enabled).apply() }
    fun dark(): Boolean = prefs.getBoolean("dark", false)
    fun setDark(enabled: Boolean) { prefs.edit().putBoolean("dark", enabled).apply() }
    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    fun toggleFavorite(id: Long) {
        val values = favorites().toMutableSet()
        if (!values.add(id.toString())) values.remove(id.toString())
        prefs.edit().putStringSet("favorites", values).apply()
    }
    private fun strings(key: String): List<String> = runCatching {
        val a = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until a.length()).mapNotNull { a.optString(it).takeIf { value -> value.isNotBlank() } }
    }.getOrDefault(emptyList())
    private fun save(key: String, values: List<String>) { prefs.edit().putString(key, JSONArray(values).toString()).apply() }
    fun recent(): List<String> {
        val current = strings("recent_history")
        if (current.isNotEmpty() || prefs.contains("recent_history")) return current
        return prefs.getString("recent_ids", "").orEmpty().split(',').filter { it.isNotBlank() }
    }
    fun visited(id: Long) { save("recent_history", (listOf(id.toString()) + recent().filterNot { it == id.toString() }).take(100)) }
    fun removeRecent(id: Long) { save("recent_history", recent().filterNot { it == id.toString() }); prefs.edit().remove("recent_ids").apply() }
    fun clearRecent() { save("recent_history", emptyList()); prefs.edit().remove("recent_ids").apply() }
    fun position(id: Long): Long = prefs.getLong("pos_$id", 0L)
    fun savePosition(id: Long, ms: Long) { prefs.edit().putLong("pos_$id", ms.coerceAtLeast(0L)).apply() }
    fun playlists(): List<String> = strings("playlist_names")
    fun createPlaylist(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || n.length > 45 || playlists().any { it.equals(n, ignoreCase=true) }) return false
        save("playlist_names", playlists() + n)
        return true
    }
    private fun key(name: String) = "playlist_$name"
    fun playlistItems(name: String): List<String> = strings(key(name))
    fun addToPlaylist(name: String, id: Long) {
        if (name !in playlists()) return
        save(key(name), (playlistItems(name) + id.toString()).distinct())
    }
    fun removeFromPlaylist(name: String, id: Long) { save(key(name), playlistItems(name).filterNot { it == id.toString() }) }
    fun deletePlaylist(name: String) { save("playlist_names", playlists().filterNot { it == name }); prefs.edit().remove(key(name)).apply() }
}
