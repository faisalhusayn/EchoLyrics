package com.echolyrics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the playback monitor after a reboot or an app update, so auto-launch
 * keeps working without opening EchoLyrics first.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                if (!Prefs(context).autoLaunch) return
                runCatching { context.startService(Intent(context, PlaybackMonitorService::class.java)) }
                    .onFailure { DebugLog.add("monitor start failed after ${intent.action}") }
            }
        }
    }
}
