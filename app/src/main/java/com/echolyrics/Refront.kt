package com.echolyrics

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Bounded reclaim window. After a track changes, Fire OS/Aria may bring its Now
 * Playing screen forward at an unpredictable moment (observed 2-7s later on this
 * hardware). Instead of fixed-delay attempts we poll once per 1.5s for a short
 * window and only act when none of our activities are resumed.
 */
object Refront {
    private val handler = Handler(Looper.getMainLooper())
    private var until = 0L
    private var appContext: Context? = null
    private val tick = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() > until) return
            val context = appContext ?: return
            if (AppState.resumed == 0) {
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (power.isInteractive) runCatching {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            }
            handler.postDelayed(this, 1500)
        }
    }
    fun claim(context: Context, duration: Long = 15000L) {
        appContext = context.applicationContext
        until = maxOf(until, System.currentTimeMillis() + duration)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }
}
