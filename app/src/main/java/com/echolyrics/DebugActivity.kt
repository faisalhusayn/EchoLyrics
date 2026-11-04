package com.echolyrics

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class DebugActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12); setBackgroundColor(Color.BLACK) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "Debug log"; textSize = 22f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, 50, 1f))
        header.addView(TextView(this).apply { text = "Clear"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(18, 0, 18, 0); setOnClickListener { DebugLog.clear(); recreate() } })
        root.addView(header)
        val list = TextView(this).apply { text = DebugLog.snapshot().joinToString("\n"); textSize = 13f; setTextColor(0xffcccccc.toInt()); setPadding(8, 8, 8, 8) }
        root.addView(ScrollView(this).apply { addView(list); post { fullScroll(ScrollView.FOCUS_DOWN) } }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
