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
    private val legacyFont: Float get() = p.getFloat("font", 40f)
    /** Size of the currently sung (highlighted) line. Defaults to the old single font setting. */
    var activeFontSize: Float
        get() = p.getFloat("active_font", legacyFont)
        set(value) { p.edit().putFloat("active_font", value).apply() }
    /** Size of the non-active lyric lines. */
    var fontSize: Float
        get() = p.getFloat("normal_font", (activeFontSize * .68f).coerceAtLeast(20f))
        set(value) { p.edit().putFloat("normal_font", value).apply() }
    var globalOffset: Long
        get() = p.getLong("global_offset", 0)
        set(value) { p.edit().putLong("global_offset", value).apply() }
    var nightMode: Boolean
        get() = p.getBoolean("night", false)
        set(value) { p.edit().putBoolean("night", value).apply() }
    var autoLaunch: Boolean
        get() = p.getBoolean("auto_launch", false)
        set(value) { p.edit().putBoolean("auto_launch", value).apply() }
    /** When true, EchoLyrics uses [brightness] instead of following the system level. */
    var brightnessManual: Boolean
        get() = p.getBoolean("bright_manual", false)
        set(value) { p.edit().putBoolean("bright_manual", value).apply() }
    /** Manual window brightness, 0.02..1.0. */
    var brightness: Float
        get() = p.getFloat("bright", .5f)
        set(value) { p.edit().putFloat("bright", value.coerceIn(.02f, 1f)).apply() }
    init {
        // Anyone who had the old "Low brightness" toggle keeps that level as a manual override.
        if (!p.contains("bright_manual") && p.getBoolean("low_brightness", false))
            p.edit().putBoolean("bright_manual", true).putFloat("bright", .08f).apply()
    }
    var showArt: Boolean
        get() = p.getBoolean("show_art", true)
        set(value) { p.edit().putBoolean("show_art", value).apply() }
    var showLyrics: Boolean
        get() = p.getBoolean("show_lyrics", true)
        set(value) { p.edit().putBoolean("show_lyrics", value).apply() }
    var accentEnabled: Boolean
        get() = p.getBoolean("accent", false)
        set(value) { p.edit().putBoolean("accent", value).apply() }
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
