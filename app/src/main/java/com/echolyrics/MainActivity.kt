package com.echolyrics

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.res.ColorStateList
import android.view.*
import android.webkit.*
import android.widget.*
import android.graphics.drawable.GradientDrawable
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var spotify: SpotifyClient
    private lateinit var lyrics: LyricsClient
    private lateinit var root: FrameLayout
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var art: ImageView
    private lateinit var accentLayer: View
    private lateinit var thumbnail: ImageView
    private lateinit var syncedScroll: ScrollView
    private lateinit var syncedLines: LinearLayout
    private var syncedViews = emptyList<TextView>()
    private lateinit var plainScroll: ScrollView
    private lateinit var plainText: TextView
    private lateinit var syncedBox: LinearLayout
    private lateinit var overlay: LinearLayout
    private lateinit var queuePanel: LinearLayout
    private lateinit var queueList: LinearLayout
    private lateinit var queueStatus: TextView
    private var queueLoading = false
    private lateinit var seekBar: SeekBar
    private lateinit var elapsedTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var controlsButton: ImageButton
    private lateinit var playButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var saveButton: ImageButton
    private lateinit var shuffleButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private lateinit var dmSans: android.graphics.Typeface
    private var accentColor = Color.BLACK
    private var accentHighlight = Color.WHITE
    private var accentAnimator: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(3)
    private val pollExecutor = Executors.newSingleThreadExecutor()
    private var track: Track? = null
    private var lyric: Lyrics? = null
    private var polledAt = 0L
    private var progress = 0L
    private var playing = false
    private var pollDelay = 2000L
    private var endPollTrackId: String? = null
    private var plainAuto = true
    private var plainManualUntil = 0L
    private var lastLineIndex = -1
    private var lastBitmap: Bitmap? = null
    private var currentState = State.LOADING
    private var seeking = false
    private var savedState = false
    private var shuffleState = false
    private var repeatState = "off"
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownAt = 0L
    private var touchBlocked = false
    private var lastSwipeAt = 0L
    private var rebuildOnResume = false
    private val overlayOpen get() = ::overlay.isInitialized && overlay.visibility == View.VISIBLE
    private val queueVisible get() = ::queuePanel.isInitialized && queuePanel.visibility == View.VISIBLE

    private enum class State(val label: String, val color: Int) {
        LOGGED_OUT("logged out", 0xffcc7777.toInt()), OFFLINE("offline", 0xffccaa66.toInt()),
        LOADING("loading", 0xffaaaaaa.toInt()), NO_LYRICS("no lyrics", 0xffccaa66.toInt()),
        RATE_LIMITED("rate limited", 0xffccaa66.toInt()), OK("ok", 0xff8bd36b.toInt())
    }

    private val poller = object : Runnable {
        override fun run() { poll() }
    }
    private val ticker = object : Runnable {
        override fun run() { drawLyrics(); handler.postDelayed(this, 250) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this); spotify = SpotifyClient(prefs); lyrics = LyricsClient(this); dmSans = android.graphics.Typeface.createFromAsset(assets, "Figtree-SemiBold.ttf")
        applyBrightness()
        buildUi()
        if (prefs.autoLaunch) startService(Intent(this, PlaybackMonitorService::class.java))
        intent.getStringExtra("refresh_token")?.let { prefs.refreshToken = it }
        handler.post(poller); handler.post(ticker)
    }
    override fun onResume() {
        super.onResume(); AppState.resumed++; DebugLog.add("activity resume")
        if (rebuildOnResume && ::root.isInitialized) { rebuildOnResume = false; track = null; lyric = null; buildUi() }
        handler.removeCallbacks(poller); handler.post(poller)
    }
    override fun onPause() { super.onPause(); DebugLog.add("activity pause"); handler.removeCallbacks(poller) }
    override fun onStop() { if (AppState.resumed > 0) AppState.resumed--; super.onStop() }
    override fun onDestroy() { executor.shutdownNow(); pollExecutor.shutdownNow(); handler.removeCallbacksAndMessages(null); lastBitmap?.recycle(); super.onDestroy() }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y; touchDownAt = System.currentTimeMillis(); touchBlocked = false
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                val now = System.currentTimeMillis()
                val horizontal = Math.abs(dx) > dp(80) && Math.abs(dx) > Math.abs(dy) * 1.5f
                if (!touchBlocked && track != null && horizontal && now - touchDownAt in 1L..700L && now - lastSwipeAt > 500L) {
                    lastSwipeAt = now
                    swipeTrack(if (dx < 0) -1 else 1)
                }
                touchBlocked = false
            }
            MotionEvent.ACTION_CANCEL -> touchBlocked = false
        }
        return super.dispatchTouchEvent(event)
    }
    private fun swipeTrack(direction: Int) {
        if (track == null) return
        control(if (direction < 0) "/v1/me/player/next" else "/v1/me/player/previous")
        handler.removeCallbacks(poller); handler.postDelayed(poller, 1200)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(if (prefs.nightMode) Color.BLACK else 0xff050505.toInt())
            setOnClickListener { wakePolling(); toggleOverlay() }
            setOnLongClickListener { startActivity(Intent(this@MainActivity, DebugActivity::class.java)); true }
        }
        art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = .16f; visibility = if (prefs.showArt && !prefs.accentEnabled) View.VISIBLE else View.GONE }
        root.addView(art, FrameLayout.LayoutParams(-1, -1))
        accentLayer = View(this).apply { alpha = 1f; visibility = if (prefs.accentEnabled) View.VISIBLE else View.GONE; background = ColorDrawable(accentColor) }
        root.addView(accentLayer, FrameLayout.LayoutParams(-1, -1))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(36), dp(18), dp(36), dp(8) ) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        thumbnail = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = GradientDrawable().apply { setColor(0xff202020.toInt()); cornerRadius = dp(14).toFloat() }; visibility = if (prefs.showArt) View.VISIBLE else View.GONE }
        top.addView(thumbnail, LinearLayout.LayoutParams(dp(64), dp(64)))
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), 0, 0, 0) }
        title = text(22, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }; artist = text(15, 0xffb8b8b8.toInt()).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }; labels.addView(title); labels.addView(artist)
        top.addView(labels, LinearLayout.LayoutParams(0, -1, 1f)); body.addView(top, LinearLayout.LayoutParams(-1, dp(74)))
        syncedLines = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(90), 0, dp(90)) }
        syncedScroll = ScrollView(this).apply { isFillViewport = true; addView(syncedLines) }
        syncedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(syncedScroll, LinearLayout.LayoutParams(-1, -1)) }
        plainText = text(28, Color.WHITE).apply { gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0) }
        plainScroll = ScrollView(this).apply { addView(plainText); visibility = View.GONE; setOnTouchListener { _, event -> if (event.action == MotionEvent.ACTION_DOWN) { plainManualUntil = System.currentTimeMillis() + 4000; plainAuto = false }; false } }
        body.addView(syncedBox, LinearLayout.LayoutParams(-1, 0, 1f)); body.addView(plainScroll, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(body, FrameLayout.LayoutParams(-1, -1))
        syncedBox.visibility = if (prefs.showLyrics) View.VISIBLE else View.GONE
        plainScroll.visibility = View.GONE
        makeOverlay()
        controlsButton = iconButton(R.drawable.ic_lyrics, 56, "Open controls") { toggleOverlay() }
        root.addView(controlsButton, FrameLayout.LayoutParams(dp(72), dp(56), Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(16), 0, 0, dp(12)) })
        makeQueuePanel()
        root.addView(queuePanel, FrameLayout.LayoutParams(-1, -1))
        if (prefs.refreshToken == null && prefs.accessToken == null) showLogin() else setState(State.LOADING)
        setContentView(root)
    }

    private fun makeOverlay() {
        overlay = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(10), dp(16), dp(10)); background = GradientDrawable().apply { setColor(0xf01b1b1b.toInt()); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0xff383838.toInt()) }; visibility = View.GONE }
        fun addIcon(parent: LinearLayout, button: ImageButton, size: Int, spacing: Int = 8) { parent.addView(button, LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(spacing), 0, dp(spacing), 0) }) }
        val playback = LinearLayout(this).apply { gravity = Gravity.CENTER }
        shuffleButton = iconButton(R.drawable.ic_shuffle, 56, "Shuffle", shuffleState) { shuffleState = !shuffleState; updateControlIcons(); controlShuffle() }
        repeatButton = iconButton(R.drawable.ic_repeat, 56, "Repeat", repeatState != "off") { repeatState = if (repeatState == "off") "context" else if (repeatState == "context") "track" else "off"; updateControlIcons(); controlRepeat() }
        addIcon(playback, shuffleButton, 56)
        playback.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        val transport = LinearLayout(this).apply { gravity = Gravity.CENTER }
        previousButton = iconButton(R.drawable.ic_previous, 56, "Previous", plain = true) { control("/v1/me/player/previous") }; addIcon(transport, previousButton, 56, 16)
        playButton = iconButton(if (playing) R.drawable.ic_pause else R.drawable.ic_play, 72, if (playing) "Pause" else "Play", true) { control(if (playing) "/v1/me/player/pause" else "/v1/me/player/play") }; addIcon(transport, playButton, 72)
        nextButton = iconButton(R.drawable.ic_next, 56, "Next", plain = true) { control("/v1/me/player/next") }; addIcon(transport, nextButton, 56, 16)
        saveButton = iconButton(if (savedState) R.drawable.ic_heart_filled else R.drawable.ic_heart, 56, "Save track") { toggleSave() }
        playback.addView(transport)
        playback.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        addIcon(playback, repeatButton, 56)
        seekBar = SeekBar(this).apply { max = 1000; progressTintList = ColorStateList.valueOf(if (prefs.accentEnabled) accentHighlight else 0xffbdbdbd.toInt()); thumbTintList = ColorStateList.valueOf(Color.WHITE); setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) touchBlocked = true; if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) touchBlocked = false; false }; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onStartTrackingTouch(bar: SeekBar) { seeking = true }; override fun onStopTrackingTouch(bar: SeekBar) { seeking = false; track?.let { item -> val position = item.duration.toLong() * bar.progress / 1000; executor.execute { spotify.seek(position, item.deviceId) }; this@MainActivity.progress = position; polledAt = System.currentTimeMillis() } }; override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {} }) }
        val timing = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; elapsedTime = text(12, 0xffaaaaaa.toInt()); totalTime = text(12, 0xffaaaaaa.toInt()); addView(elapsedTime); addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f)); addView(totalTime) }
        overlay.addView(playback, LinearLayout.LayoutParams(-1, dp(76))); overlay.addView(timing, LinearLayout.LayoutParams(-1, dp(22))); overlay.addView(seekBar, LinearLayout.LayoutParams(-1, dp(38)))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f)); actions.addView(iconButton(R.drawable.ic_settings, 56, "Settings") { rebuildOnResume = true; startActivity(Intent(this, SettingsActivity::class.java)) }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { setMargins(dp(8), 0, dp(8), 0) }); actions.addView(iconButton(R.drawable.ic_refresh, 56, "Refresh lyrics") { track?.let { lyric = null; renderSynced(emptyList()); showSynced(); setState(State.LOADING); fetchLyrics(it, true) } }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { setMargins(dp(8), 0, dp(8), 0) }); actions.addView(iconButton(R.drawable.ic_queue, 56, "Next up") { showQueue() }, LinearLayout.LayoutParams(dp(56), dp(56)).apply { setMargins(dp(8), 0, dp(8), 0) }); actions.addView(saveButton, LinearLayout.LayoutParams(dp(56), dp(56)).apply { setMargins(dp(8), 0, dp(8), 0) })
        overlay.addView(actions)
        root.addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(12)) })
        updateControlIcons()
    }

    private fun toggleOverlay() {
        if (queueVisible) { hideQueue(); return }
        overlay.visibility = if (overlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        updateControlsButton()
    }
    private fun updateControlsButton() {
        if (!::controlsButton.isInitialized) return
        val (icon, description) = when {
            queueVisible -> R.drawable.ic_close to "Close queue"
            overlayOpen -> R.drawable.ic_close to "Close controls"
            else -> R.drawable.ic_lyrics to "Open controls"
        }
        controlsButton.setImageResource(icon); controlsButton.contentDescription = description
    }

    // Read-only "Next up" view. Spotify has no API for jumping to, reordering,
    // or removing queued items, so rows are deliberately not clickable. The
    // queue is fetched only when the panel opens (plus manual refresh), which
    // keeps it out of the playback poll loop entirely.
    private fun makeQueuePanel() {
        queuePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(10), dp(24), dp(8))
            setBackgroundColor(0xff090909.toInt())
            visibility = View.GONE
            isClickable = true
            setOnClickListener { }
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(24, Color.WHITE).apply { this.text = "Next up" }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(R.drawable.ic_refresh, 48, "Refresh queue") { loadQueue() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(0, 0, dp(10), 0) })
        header.addView(iconButton(R.drawable.ic_close, 48, "Close queue") { hideQueue() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        queuePanel.addView(header, LinearLayout.LayoutParams(-1, dp(58)))
        queueStatus = text(16, 0xff999999.toInt()).apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(16)) }
        queuePanel.addView(queueStatus, LinearLayout.LayoutParams(-1, -2))
        queueList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        queuePanel.addView(ScrollView(this).apply { addView(queueList) }, LinearLayout.LayoutParams(-1, 0, 1f))
    }
    private fun showQueue() {
        if (!::queuePanel.isInitialized) return
        overlay.visibility = View.GONE
        queuePanel.visibility = View.VISIBLE
        updateControlsButton()
        loadQueue()
    }
    private fun hideQueue() {
        if (!::queuePanel.isInitialized) return
        queuePanel.visibility = View.GONE
        updateControlsButton()
    }
    private fun loadQueue() {
        if (queueLoading) return
        queueLoading = true
        queueStatus.visibility = View.VISIBLE
        queueStatus.text = "Loading..."
        queueList.removeAllViews()
        DebugLog.add("queue fetch")
        executor.execute {
            val lookup = runCatching { spotify.queue() }
            val result = lookup.getOrNull()
            handler.post {
                queueLoading = false
                if (!queueVisible) return@post
                when {
                    result == null -> { DebugLog.add("queue network error"); queueStatus.text = "Couldn't load queue" }
                    429 == result.code -> { DebugLog.add("queue rate limited"); queueStatus.text = "Rate limited - try again later" }
                    result.code == 401 -> { DebugLog.add("queue unauthorized"); queueStatus.text = "Session expired" }
                    result.code !in 200..299 -> { DebugLog.add("queue code=${result.code}"); queueStatus.text = "Queue unavailable (${result.code})" }
                    else -> {
                        val items = result.value.orEmpty()
                        DebugLog.add("queue ${items.size} tracks")
                        if (items.isEmpty()) queueStatus.text = "Nothing queued"
                        else { queueStatus.visibility = View.GONE; renderQueue(items) }                    }
                }
            }
        }
    }
    private fun renderQueue(items: List<Track>) {
        queueList.removeAllViews()
        val shown = items.take(50)
        shown.forEachIndexed { index, item ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(9), 0, dp(9)) }
            row.addView(text(14, 0xff6f6f6f.toInt()).apply { this.text = "${index + 1}"; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(36), -2))
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            labels.addView(text(18, Color.WHITE).apply { this.text = item.title; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            labels.addView(text(14, 0xff9d9d9d.toInt()).apply { this.text = item.artist; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            queueList.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (index < shown.size - 1) queueList.addView(View(this).apply { setBackgroundColor(0xff1e1e1e.toInt()) }, LinearLayout.LayoutParams(-1, dp(1)))
        }
    }
    private fun iconButton(icon: Int, size: Int, description: String, active: Boolean = false, plain: Boolean = false, action: () -> Unit) = ImageButton(this).apply { setImageResource(icon); contentDescription = description; scaleType = ImageView.ScaleType.CENTER; setPadding(dp(if (size >= 72) 20 else 16), dp(if (size >= 72) 20 else 16), dp(if (size >= 72) 20 else 16), dp(if (size >= 72) 20 else 16)); background = getDrawable(if (plain) R.drawable.control_plain_ripple else if (active) R.drawable.control_active_ripple else R.drawable.control_ripple); isEnabled = true; setOnClickListener { action() } }
    private fun updateControlIcons() { if (!::playButton.isInitialized) return; playButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play); playButton.contentDescription = if (playing) "Pause" else "Play"; saveButton.setImageResource(if (savedState) R.drawable.ic_heart_filled else R.drawable.ic_heart); shuffleButton.background = getDrawable(if (shuffleState) R.drawable.control_active_ripple else R.drawable.control_ripple); repeatButton.setImageResource(if (repeatState == "track") R.drawable.ic_repeat_one else R.drawable.ic_repeat); repeatButton.background = getDrawable(if (repeatState != "off") R.drawable.control_active_ripple else R.drawable.control_ripple); val enabled = track != null; listOf(previousButton, playButton, nextButton, saveButton, shuffleButton, repeatButton).forEach { it.isEnabled = enabled; it.alpha = if (enabled) 1f else .45f } }
    private fun text(size: Int, color: Int) = TextView(this).apply { textSize = size.toFloat(); setTextColor(color); typeface = dmSans }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun setState(state: State, detail: String? = null) { currentState = state; DebugLog.add("state ${detail ?: state.label}") }
    private fun poll() {
        pollExecutor.execute {
            try {
                val result = spotify.current(); pollDelay = when (result.code) { 429 -> result.retryAfterMs else -> 2000 }
                when (result.code) {
                    401 -> handler.post { setState(State.LOGGED_OUT); if (prefs.needsReauth) showLogin() }
                    429 -> handler.post { setState(State.RATE_LIMITED) }
                    204 -> handler.post { playing = false; if (track == null) { title.text = "Nothing playing"; artist.text = "" }; setState(State.OK); pollDelay = 2000 }
                    in 200..299 -> result.value?.let { updateTrack(it) } ?: handler.post { setState(State.OK) }
                    else -> handler.post { setState(State.OFFLINE) }
                }
            } catch (_: Exception) { pollDelay = (pollDelay * 2).coerceIn(10000, 30000); DebugLog.add("poll network error backoff=$pollDelay"); handler.post { setState(State.OFFLINE) } }
            finally { handler.post { scheduleNextPoll() } }
        }
    }
    private fun updateTrack(fresh: Track) {
        val changed = fresh.id != track?.id; track = fresh; progress = fresh.progress; polledAt = System.currentTimeMillis(); playing = fresh.playing; pollDelay = 2000
        shuffleState = fresh.shuffle; repeatState = fresh.repeat
        handler.post { updateControlIcons() }
        handler.post { title.text = fresh.title; artist.text = fresh.artist; setState(if (lyric == null && changed) State.LOADING else State.OK); if (changed) { lyric = null; renderSynced(emptyList()); showSynced(); fetchLyrics(fresh, false); fresh.art?.let { loadArt(it) } ?: DebugLog.add("album art URL missing") } }
        if (!changed) return
    }
    private fun fetchLyrics(item: Track, force: Boolean, attempt: Int = 0) { DebugLog.add("lyrics fetch ${item.id.takeLast(6)}"); executor.execute { val lookup = runCatching { lyrics.get(item, force) }; val result = lookup.getOrNull(); val retry = result == null && attempt == 0 && (lookup.isFailure || lyrics.lastNetworkError); if (lookup.isFailure) DebugLog.add("lyrics lookup failed"); DebugLog.add("lyrics result ${item.id.takeLast(6)} synced=${result?.synced?.size ?: 0} plain=${!result?.plain.isNullOrBlank()}"); handler.post { if (track?.id != item.id) { DebugLog.add("discarded stale lyrics"); return@post }; if (retry) { setState(State.LOADING); handler.postDelayed({ if (track?.id == item.id) fetchLyrics(item, true, 1) }, 1500); return@post }; lyric = result; if (!prefs.showLyrics) return@post; if (result == null) { renderSynced(emptyList()); plainText.text = "No lyrics available"; syncedBox.visibility = View.GONE; plainScroll.visibility = View.VISIBLE; setState(if (lyrics.lastNetworkError) State.OFFLINE else State.NO_LYRICS) } else { setState(State.OK); if (result.synced.isEmpty()) { renderSynced(emptyList()); plainText.text = result.plain ?: "No lyrics available"; syncedBox.visibility = View.GONE; plainScroll.visibility = View.VISIBLE } else { renderSynced(result.synced); showSynced() } } } } }
    private fun renderSynced(lines: List<LyricLine>) {
        syncedLines.removeAllViews()
        syncedViews = lines.map { line -> text(prefs.fontSize.coerceAtMost(48f).toInt(), 0xff777777.toInt()).apply { this.text = line.text; gravity = Gravity.CENTER; setPadding(dp(12), dp(10), dp(12), dp(10)); maxLines = 3; setOnClickListener { if (!overlayOpen && !queueVisible) seekToLine(line.time) } } }
        syncedViews.forEach { syncedLines.addView(it, LinearLayout.LayoutParams(-2, -2)) }
        lastLineIndex = -1
    }
    private fun seekToLine(time: Long) {
        val item = track ?: return
        val position = time.coerceIn(0L, item.duration.toLong())
        DebugLog.add("seek tap ${position / 1000}s")
        executor.execute {
            val result = spotify.seek(position, item.deviceId)
            if (result.code in 200..299 || result.code == 204) {
                progress = position
                polledAt = System.currentTimeMillis()
                handler.post { setState(State.OK) }
            } else {
                handler.post { setState(State.OFFLINE, "Seek failed") }
            }
        }
    }
    private fun showSynced() { syncedBox.visibility = View.VISIBLE; plainScroll.visibility = View.GONE }
    private fun drawLyrics() {
        val item = track ?: return; if (!prefs.showLyrics) return; val value = progress + if (playing) System.currentTimeMillis() - polledAt else 0; val offset = prefs.offsetFor(item.id) + prefs.globalOffset; val lines = lyric?.synced.orEmpty()
        if (lines.isNotEmpty() && syncedViews.size == lines.size) { val index = lines.indexOfLast { it.time <= value + offset }.coerceAtLeast(0); if (index != lastLineIndex) { if (lastLineIndex == -1) DebugLog.add("font active=${prefs.activeFontSize.toInt()} other=${prefs.fontSize.toInt()}"); lastLineIndex = index; syncedViews.forEachIndexed { n, view -> view.setTextColor(if (n == index) Color.WHITE else 0xff777777.toInt()); view.textSize = if (n == index) prefs.activeFontSize else prefs.fontSize }; syncedScroll.post { val view = syncedViews[index]; syncedScroll.scrollTo(0, (view.top - syncedScroll.height / 2 + view.height / 2).coerceAtLeast(0)) } } }
        lyric?.plain?.takeIf { lines.isEmpty() }?.let { if (System.currentTimeMillis() > plainManualUntil && !plainAuto) plainAuto = true; if (plainAuto && System.currentTimeMillis() > plainManualUntil) { val ratio = (value.toFloat() / item.duration.coerceAtLeast(1)).coerceIn(0f, 1f); plainScroll.post { val max = (plainText.height - plainScroll.height).coerceAtLeast(0); plainScroll.scrollTo(0, (max * ratio).toInt()) } } }
        if (::elapsedTime.isInitialized) { elapsedTime.text = formatTime(value); totalTime.text = formatTime(item.duration.toLong()) }
        if (!seeking && ::seekBar.isInitialized && item.duration > 0) seekBar.progress = (value.coerceIn(0, item.duration.toLong()) * 1000 / item.duration).toInt()
    }
    private fun formatTime(milliseconds: Long): String { val seconds = milliseconds.coerceAtLeast(0) / 1000; return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" }
    private fun adjustOffset(delta: Long) { track?.let { prefs.setOffsetFor(it.id, prefs.offsetFor(it.id) + delta) } ?: run { prefs.globalOffset += delta } }
    private fun control(path: String) {
        val trackChange = path.endsWith("/next") || path.endsWith("/previous")
        if (trackChange) Refront.claim(this)
        executor.execute {
            val result = spotify.control(path, if (path.endsWith("pause") || path.endsWith("play")) "PUT" else "POST", track?.deviceId)
            handler.post {
                when (result.code) { 401 -> { setState(State.LOGGED_OUT); showLogin() }; 403 -> setState(State.RATE_LIMITED, "Premium required"); 404 -> setState(State.OFFLINE, "No active device"); in 200..299, 204 -> setState(State.OK); else -> setState(State.OFFLINE) }
                // Poll once Spotify has accepted the command, instead of on a fixed timer.
                if (trackChange && result.code in 200..299 && AppState.resumed > 0) {
                    handler.removeCallbacks(poller); handler.postDelayed(poller, 400)
                    DebugLog.add("poll after ${path.substringAfterLast('/')}")
                }
            }
        }
    }
    private fun toggleSave() { val item = track ?: return; executor.execute { val current = spotify.saved(item.id).value == true; val desired = !current; val result = spotify.save(item.id, desired); if (result.code in 200..299 || result.code == 204) { savedState = desired; handler.post { updateControlIcons() } }; DebugLog.add("library save code=${result.code}") } }
    private fun controlShuffle() { executor.execute { val result = spotify.shuffle(shuffleState, track?.deviceId); if (result.code !in 200..299 && result.code != 204) shuffleState = !shuffleState; DebugLog.add("shuffle code=${result.code}") } }
    private fun controlRepeat() { executor.execute { val result = spotify.repeat(repeatState, track?.deviceId); if (result.code !in 200..299 && result.code != 204) repeatState = if (repeatState == "off") "context" else "off"; DebugLog.add("repeat code=${result.code}") } }
    private fun loadArt(url: String) { executor.execute { val result = runCatching { val connection = URL(url).openConnection().apply { connectTimeout = 4000; readTimeout = 4000 }; connection.getInputStream().use { input -> val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeStream(input, null, bounds); val sample = if (bounds.outWidth > 240) 4 else 2; val options = BitmapFactory.Options().apply { inSampleSize = sample }; URL(url).openConnection().apply { connectTimeout = 4000; readTimeout = 4000 }.getInputStream().use { image -> BitmapFactory.decodeStream(image, null, options) } } }; result.onFailure { DebugLog.add("album art load failed") }; val bitmap = result.getOrNull(); if (bitmap == null) DebugLog.add("album art empty"); bitmap?.let { image -> val colors = if (prefs.accentEnabled) Accent.from(image) else null; handler.post { if (track?.art != url) { image.recycle(); return@post }; lastBitmap?.recycle(); lastBitmap = image; thumbnail.setImageBitmap(image); art.setImageBitmap(image); colors?.let { animateAccent(it) } } } } }
    private fun animateAccent(colors: AccentColors) { val from = accentColor; accentColor = colors.background; accentHighlight = colors.highlight; if (!prefs.accentEnabled) return; accentLayer.visibility = View.VISIBLE; accentAnimator?.cancel(); accentAnimator = ValueAnimator.ofFloat(0f, 1f).apply { duration = 400; addUpdateListener { animation -> val fraction = animation.animatedFraction; accentLayer.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(blend(from, colors.top, fraction), blend(from, colors.bottom, fraction))) }; start() }; seekBar.progressTintList = ColorStateList.valueOf(accentHighlight) }
    private fun blend(from: Int, to: Int, fraction: Float): Int { val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * fraction).toInt(); val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * fraction).toInt(); val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction).toInt(); return Color.rgb(r, g, b) }
    private fun scheduleNextPoll() {
        handler.removeCallbacks(poller)
        handler.postDelayed(poller, nextPollDelay())
    }
    // Poll just after the track is due to end, so a natural advance is noticed
    // within a few hundred ms instead of waiting for the next 2s tick. Runs at
    // most once per track, and never while rate-limited or backing off, so it
    // cannot turn into a poll loop.
    private fun nextPollDelay(): Long {
        if (pollDelay > 2000L || System.currentTimeMillis() < AppState.apiBlockedUntil) return pollDelay
        val item = track ?: return pollDelay
        if (!playing || item.duration <= 0) return pollDelay
        val remaining = item.duration.toLong() - (progress + (System.currentTimeMillis() - polledAt))
        if (endPollTrackId == item.id && remaining > pollDelay) endPollTrackId = null
        val target = (remaining + 400L).coerceAtLeast(500L)
        if (endPollTrackId != item.id && target < pollDelay) {
            endPollTrackId = item.id
            DebugLog.add("poll at track end in ${target}ms")
            return target
        }
        return pollDelay
    }
    private fun wakePolling() { pollDelay = 2000; handler.removeCallbacks(poller); handler.post(poller) }
    private fun applyBrightness() { val params = window.attributes; params.screenBrightness = if (prefs.lowBrightness) .08f else -1f; window.attributes = params }
    private fun showLogin() { val web = WebView(this); web.settings.javaScriptEnabled = true; web.settings.domStorageEnabled = true; web.webViewClient = object : WebViewClient() { override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest) = intercept(request.url); @Suppress("DEPRECATION") override fun shouldOverrideUrlLoading(v: WebView, url: String) = intercept(Uri.parse(url)) }; web.loadUrl(spotify.authUrl()); root.removeAllViews(); root.addView(web) }
    private fun intercept(uri: Uri): Boolean { if (uri.scheme == "echolyrics") { uri.getQueryParameter("code")?.let { code -> executor.execute { if (spotify.exchange(code)) handler.post { buildUi() } } }; return true }; return false }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); intent.getStringExtra("refresh_token")?.let { prefs.refreshToken = it; buildUi() }; intent.data?.takeIf { it.scheme == "echolyrics" }?.getQueryParameter("code")?.let { code -> executor.execute { if (spotify.exchange(code)) handler.post { buildUi() } } } }
}
