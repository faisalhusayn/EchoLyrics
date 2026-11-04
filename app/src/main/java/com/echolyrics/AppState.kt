package com.echolyrics

object AppState {
    @Volatile var apiBlockedUntil = 0L
    @Volatile var resumed = 0
}
