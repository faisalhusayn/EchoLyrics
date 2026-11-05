package com.echolyrics

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class PlaybackMonitorService : Service() {
    private lateinit var client: SpotifyClient
    private val handler = Handler(Looper.getMainLooper())
    private var initialized = false
    private var wasPlaying = false
    private var lastTrackId: String? = null
    private val poll: Runnable = object : Runnable {
        override fun run() {
            Thread {
                val track = runCatching { client.current().value }.getOrNull()
                val isPlaying = track?.playing == true
                val trackId = track?.id
                val started = isPlaying && (!initialized || !wasPlaying)
                val changed = initialized && isPlaying && trackId != null && lastTrackId != null && trackId != lastTrackId
                if (started || changed) Refront.claim(this@PlaybackMonitorService)
                initialized = true
                wasPlaying = isPlaying
                if (trackId != null) lastTrackId = trackId
                handler.postDelayed(poll, 5000)
            }.start()
        }
    }
    override fun onCreate() { super.onCreate(); client = SpotifyClient(Prefs(this)); handler.post(poll) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
