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
    private val poll: Runnable = object : Runnable {
        override fun run() {
            Thread {
                val result = runCatching { client.current().value }.getOrNull()
                val isPlaying = result?.playing == true
                if (initialized && isPlaying && !wasPlaying) {
                    startActivity(Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
                initialized = true; wasPlaying = isPlaying
                handler.postDelayed(poll, 5000)
            }.start()
        }
    }
    override fun onCreate() { super.onCreate(); client = SpotifyClient(Prefs(this)); handler.post(poll) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
