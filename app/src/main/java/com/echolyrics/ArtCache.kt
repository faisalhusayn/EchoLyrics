package com.echolyrics

/**
 * Tiny LRU cache of downloaded album-art bytes, keyed by image URL. Prefetching the
 * next track's art lets the thumbnail and accent colour appear as soon as playback
 * moves on, without waiting for a download. Storing bytes (not bitmaps) avoids any
 * interaction with the visible bitmap's recycling.
 */
object ArtCache {
    private const val MAX_ENTRIES = 3
    private val entries = object : LinkedHashMap<String, ByteArray>(4, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > MAX_ENTRIES
    }

    @Synchronized fun get(url: String): ByteArray? = entries[url]

    @Synchronized fun put(url: String, bytes: ByteArray) { entries[url] = bytes }
}
