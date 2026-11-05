package com.echolyrics

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var dmSans: android.graphics.Typeface
    private var brightnessButton: TextView? = null
    override fun onResume() { super.onResume(); AppState.resumed++; applyBrightness() }
    override fun onStop() { if (AppState.resumed > 0) AppState.resumed--; super.onStop() }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        prefs = Prefs(this)
        dmSans = android.graphics.Typeface.createFromAsset(assets, "Figtree-SemiBold.ttf")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(18), dp(28), dp(18)); setBackgroundColor(Color.BLACK) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Settings", 25, Color.WHITE), LinearLayout.LayoutParams(0, dp(58), 1f)); header.addView(action("Done") { finish() })
        root.addView(header)
        root.addView(label("DISPLAY", 12, 0xff999999.toInt()), LinearLayout.LayoutParams(-1, dp(32)))
        root.addView(row(action(if (prefs.showLyrics) "Lyrics visible" else "Lyrics hidden") { prefs.showLyrics = !prefs.showLyrics; recreate() }, action(if (prefs.showArt) "Album art visible" else "Album art hidden") { prefs.showArt = !prefs.showArt; recreate() }))
        root.addView(row(action("Lyrics ${prefs.fontSize.toInt()}sp") { prefs.fontSize = nextFont(prefs.fontSize, lyricSteps); recreate() }, action("Active ${prefs.activeFontSize.toInt()}sp") { prefs.activeFontSize = nextFont(prefs.activeFontSize, activeSteps); recreate() }, action(if (prefs.nightMode) "Night mode on" else "Night mode off") { prefs.nightMode = !prefs.nightMode; recreate() }))
        val brightnessToggle = action(brightnessText()) { if (prefs.brightnessManual) prefs.brightnessManual = false else { prefs.brightnessManual = true; prefs.brightness = systemBrightness() }; recreate() }
        brightnessButton = brightnessToggle
        root.addView(row(action(if (prefs.accentEnabled) "Accent background on" else "Accent background off") { prefs.accentEnabled = !prefs.accentEnabled; recreate() }, brightnessToggle))
        root.addView(brightnessRow(), LinearLayout.LayoutParams(-1, dp(60)).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
        root.addView(label("SYNC", 12, 0xff999999.toInt()), LinearLayout.LayoutParams(-1, dp(32)))
        root.addView(row(action("Track -100ms") { adjust(-100) }, action("Track +100ms") { adjust(100) }, action("Global -100ms") { prefs.globalOffset -= 100 }, action("Global +100ms") { prefs.globalOffset += 100 }))
        root.addView(label("BEHAVIOR", 12, 0xff999999.toInt()), LinearLayout.LayoutParams(-1, dp(32)))
        root.addView(row(action(if (prefs.autoLaunch) "Auto-launch on" else "Auto-launch off") { prefs.autoLaunch = !prefs.autoLaunch; if (prefs.autoLaunch) startService(Intent(this, PlaybackMonitorService::class.java)) else stopService(Intent(this, PlaybackMonitorService::class.java)); recreate() }, action("Debug log") { startActivity(Intent(this, DebugActivity::class.java)) }))
        root.addView(label("ACCOUNT", 12, 0xff999999.toInt()), LinearLayout.LayoutParams(-1, dp(32)))
        root.addView(row(action("Log out") { prefs.clearAuth(); finish() }, action("Clear cached lyrics") { cacheDir.resolve("lyrics").deleteRecursively(); Toast.makeText(this, "Lyrics cache cleared", Toast.LENGTH_SHORT).show() }))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }
    private fun adjust(delta: Long) { prefs.globalOffset += delta }
    private fun brightnessRow(): LinearLayout {
        val bar = SeekBar(this).apply {
            max = 100
            progress = (displayBrightness() * 100).toInt().coerceIn(2, 100)
            progressTintList = ColorStateList.valueOf(Color.WHITE)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    prefs.brightnessManual = true
                    prefs.brightness = value.coerceAtLeast(2) / 100f
                    applyBrightness()
                    brightnessButton?.text = brightnessText()
                }
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(10), 0)
            background = GradientDrawable().apply { setColor(0xff242424.toInt()); cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xff3c3c3c.toInt()) }
            addView(label("Brightness", 13, 0xffcccccc.toInt()), LinearLayout.LayoutParams(0, dp(56), .32f))
            addView(bar, LinearLayout.LayoutParams(0, dp(56), .68f))
        }
    }
    private fun displayBrightness() = if (prefs.brightnessManual) prefs.brightness else systemBrightness()
    private fun brightnessText() = "Brightness: " + if (prefs.brightnessManual) "${(prefs.brightness * 100).toInt()}%" else "System"
    private fun systemBrightness(): Float {
        val value = android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 128)
        return (value / 255f).coerceIn(.05f, 1f)
    }
    private fun applyBrightness() {
        val params = window.attributes
        params.screenBrightness = if (prefs.brightnessManual) prefs.brightness.coerceIn(.02f, 1f) else -1f
        window.attributes = params
    }
    private val lyricSteps = listOf(20f, 24f, 28f, 34f, 40f, 48f)
    private val activeSteps = listOf(28f, 34f, 40f, 48f)
    private fun nextFont(current: Float, steps: List<Float>) = steps.firstOrNull { it > current + .5f } ?: steps.first()
    private fun row(vararg views: View) = LinearLayout(this).apply { gravity = Gravity.CENTER; views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(4), dp(3), dp(4), dp(3)) }) } }
    private fun label(value: String, size: Int, color: Int) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); typeface = dmSans; gravity = Gravity.CENTER_VERTICAL }
    private fun action(value: String, icon: Int = 0, run: () -> Unit) = TextView(this).apply { text = value; textSize = 13f; typeface = dmSans; gravity = Gravity.CENTER; setTextColor(Color.WHITE); minHeight = dp(56); setPadding(dp(10), 0, dp(10), 0); if (icon != 0) { setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0); compoundDrawablePadding = dp(8) }; background = GradientDrawable().apply { setColor(0xff242424.toInt()); cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xff3c3c3c.toInt()) }; elevation = dp(2).toFloat(); setOnClickListener { run() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
