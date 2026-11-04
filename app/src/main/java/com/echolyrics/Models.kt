package com.echolyrics

import kotlinx.serialization.Serializable

data class Track(val id: String, val title: String, val artist: String, val album: String, val duration: Int, val progress: Long, val playing: Boolean, val art: String? = null, val deviceId: String? = null, val shuffle: Boolean = false, val repeat: String = "off", val saved: Boolean? = null)
data class LyricLine(val time: Long, val text: String)
data class Lyrics(val synced: List<LyricLine> = emptyList(), val plain: String? = null)
data class ApiResult<T>(val code: Int, val value: T? = null, val retryAfterMs: Long = 3000, val error: String? = null)
@Serializable data class CachedLyrics(val synced: List<CachedLine> = emptyList(), val plain: String? = null)
@Serializable data class CachedLine(val time: Long, val text: String)
