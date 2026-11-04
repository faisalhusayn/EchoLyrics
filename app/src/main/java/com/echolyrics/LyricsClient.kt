package com.echolyrics

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okhttp3.Request
import java.io.File
import java.net.URLEncoder

interface LyricsSource { fun find(track: Track): Lyrics? }

class LrcLibSource : LyricsSource {
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    override fun find(track: Track): Lyrics? {
        val query = "track_name=${enc(track.title)}&artist_name=${enc(track.artist)}&album_name=${enc(track.album)}&duration=${track.duration / 1000}"
        val first = get("https://lrclib.net/api/get?$query") ?: get("https://lrclib.net/api/search?q=${enc(track.title + " " + track.artist)}") ?: return null
        val obj = first.jsonObject
        val lrc = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
        val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
        return Lyrics(parse(lrc.orEmpty()), plain)
    }
    private fun get(url: String): JsonElement? = okhttp(url)?.let { body ->
        val element = Json.parseToJsonElement(body)
        if (element is JsonArray) element.firstOrNull() else element
    }
    private fun okhttp(url: String): String? = http.newCall(Request.Builder().url(url).header("User-Agent", "EchoLyrics/2.0 (sideloaded Android app)").build()).execute().use { if (it.isSuccessful) it.body!!.string() else null }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun parse(value: String) = value.lines().flatMap { line -> Regex("\\[(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)").findAll(line).map { m -> LyricLine((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 1000 + m.groupValues[3].padEnd(3, '0').toLong(), m.groupValues[4].trim()) } }.sortedBy { it.time }
}

class LyricsOvhSource : LyricsSource {
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    override fun find(track: Track): Lyrics? {
        val url = "https://api.lyrics.ovh/v1/${enc(track.artist)}/${enc(track.title)}"
        return http.newCall(Request.Builder().url(url).header("User-Agent", "EchoLyrics/2.0").build()).execute().use { response ->
            if (!response.isSuccessful) null else Json.parseToJsonElement(response.body!!.string()).jsonObject["lyrics"]?.jsonPrimitive?.contentOrNull?.let { Lyrics(plain = it) }
        }
    }
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}

class LyricsClient(context: Context) {
    private val dir = File(context.cacheDir, "lyrics").also { it.mkdirs() }
    private val memory = LinkedHashMap<String, Lyrics>(12, .75f, true)
    private val sources = listOf<LyricsSource>(LrcLibSource(), LyricsOvhSource())
    var lastNetworkError = false
    fun get(track: Track, force: Boolean = false): Lyrics? {
        lastNetworkError = false
        if (!force) synchronized(memory) { memory[track.id] }?.let { DebugLog.add("cache memory hit"); return it }
        val file = File(dir, track.id.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        if (!force && file.exists()) runCatching { DebugLog.add("cache disk hit"); return Json.decodeFromString<CachedLyrics>(file.readText()).let { cache(it, track.id) } }
        DebugLog.add("cache miss")
        val result = sources.asSequence().mapNotNull { source -> try { source.find(track) } catch (_: Exception) { lastNetworkError = true; null } }.firstOrNull { it.synced.isNotEmpty() || !it.plain.isNullOrBlank() }
        result?.let { value -> synchronized(memory) { memory[track.id] = value }; runCatching { file.writeText(Json.encodeToString(CachedLyrics(value.synced.map { CachedLine(it.time, it.text) }, value.plain))) }; cap() }
        return result
    }
    fun clear(trackId: String) { synchronized(memory) { memory.remove(trackId) }; File(dir, trackId.replace(Regex("[^A-Za-z0-9._-]"), "_")).delete() }
    private fun cache(value: CachedLyrics, id: String): Lyrics { val result = Lyrics(value.synced.map { LyricLine(it.time, it.text) }, value.plain); synchronized(memory) { memory[id] = result }; return result }
    private fun cap() { while (dir.listFiles()?.sumOf { it.length() } ?: 0 > 1024 * 1024) dir.listFiles()?.minByOrNull { it.lastModified() }?.delete() }
}
