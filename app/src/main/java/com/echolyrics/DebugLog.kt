package com.echolyrics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private val events = ArrayDeque<String>()
    @Synchronized fun add(message: String) { if (events.size >= 100) events.removeFirst(); events.addLast(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + " " + message) }
    @Synchronized fun snapshot(): List<String> = events.toList()
    @Synchronized fun clear() = events.clear()
}
