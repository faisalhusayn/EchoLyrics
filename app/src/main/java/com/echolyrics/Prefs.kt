package com.echolyrics

import android.content.Context

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("echo", Context.MODE_PRIVATE)
    var refreshToken: String?
        get() = p.getString("refresh", null)
        set(value) { p.edit().putString("refresh", value).apply() }
    var accessToken: String?
        get() = p.getString("access", null)
        set(value) { p.edit().putString("access", value).apply() }
    var expiresAt: Long
        get() = p.getLong("expires", 0)
        set(value) { p.edit().putLong("expires", value).apply() }
    var offset: Long
        get() = p.getLong("offset", 0)
        set(value) { p.edit().putLong("offset", value).apply() }
    var fontSize: Float
        get() = p.getFloat("font", 40f)
        set(value) { p.edit().putFloat("font", value).apply() }
    var globalOffset: Long
        get() = p.getLong("global_offset", 0)
        set(value) { p.edit().putLong("global_offset", value).apply() }
    var nightMode: Boolean
        get() = p.getBoolean("night", false)
        set(value) { p.edit().putBoolean("night", value).apply() }
    var autoLaunch: Boolean
        get() = p.getBoolean("auto_launch", false)
        set(value) { p.edit().putBoolean("auto_launch", value).apply() }
    var showArt: Boolean
        get() = p.getBoolean("show_art", true)
        set(value) { p.edit().putBoolean("show_art", value).apply() }
    var showLyrics: Boolean
        get() = p.getBoolean("show_lyrics", true)
        set(value) { p.edit().putBoolean("show_lyrics", value).apply() }
    var accentEnabled: Boolean
        get() = p.getBoolean("accent", false)
        set(value) { p.edit().putBoolean("accent", value).apply() }
    var lowBrightness: Boolean
        get() = p.getBoolean("low_brightness", false)
        set(value) { p.edit().putBoolean("low_brightness", value).apply() }
    var grantedScope: String
        get() = p.getString("scope", "") ?: ""
        set(value) { p.edit().putString("scope", value).apply() }
    var needsReauth: Boolean
        get() = p.getBoolean("needs_reauth", false)
        set(value) { p.edit().putBoolean("needs_reauth", value).apply() }
    fun offsetFor(trackId: String): Long = p.getLong("offset_$trackId", globalOffset)
    fun setOffsetFor(trackId: String, value: Long) { p.edit().putLong("offset_$trackId", value).apply() }
    fun clearOffsetFor(trackId: String) { p.edit().remove("offset_$trackId").apply() }
    fun clearAuth() { p.edit().remove("refresh").remove("access").remove("expires").apply() }
}
