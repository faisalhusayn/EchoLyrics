package com.echolyrics

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

data class AccentColors(val background: Int, val highlight: Int, val top: Int, val bottom: Int)

object Accent {
    private data class Bucket(var count: Int = 0, var saturation: Float = 0f)

    fun from(source: Bitmap): AccentColors? {
        val sample = Bitmap.createScaledBitmap(source, 48, 48, true)
        val buckets = HashMap<Int, Bucket>()
        for (y in 0 until sample.height) for (x in 0 until sample.width) {
            val color = sample.getPixel(x, y)
            val hsl = hsl(Color.red(color), Color.green(color), Color.blue(color))
            if (hsl[1] < 0.15f || hsl[2] < 0.08f || hsl[2] > 0.92f) continue
            val key = ((Color.red(color) and 0xf0) shl 16) or ((Color.green(color) and 0xf0) shl 8) or (Color.blue(color) and 0xf0)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.saturation += hsl[1]
        }
        sample.recycle()
        val best = buckets.maxByOrNull { (_, bucket) -> bucket.count * (0.3f + bucket.saturation / bucket.count) } ?: return null
        val rgb = best.key
        val base = hsl(Color.red(rgb), Color.green(rgb), Color.blue(rgb))
        var lightness = base[2].coerceIn(0.15f, 0.35f)
        val saturation = base[1].coerceIn(0.4f, 0.8f)
        val background = safeColor(base[0], saturation, lightness)
        val backgroundHsl = hsl(Color.red(background), Color.green(background), Color.blue(background))
        val top = safeColor(base[0], saturation, (backgroundHsl[2] + 0.035f).coerceAtMost(0.35f))
        val bottom = safeColor(base[0], saturation, (backgroundHsl[2] - 0.035f).coerceAtLeast(0.1f))
        val highlight = hslToColor(base[0], saturation, 0.7f)
        return AccentColors(background, highlight, top, bottom)
    }

    private fun safeColor(hue: Float, saturation: Float, initialLightness: Float): Int {
        var lightness = initialLightness
        while (lightness > 0f && contrast(Color.WHITE, hslToColor(hue, saturation, lightness)) < 4.5f) lightness -= 0.01f
        return hslToColor(hue, saturation, lightness)
    }

    private fun contrast(foreground: Int, background: Int): Float {
        fun channel(value: Int): Float { val c = value / 255f; return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat() }
        val luminance = 0.2126f * channel(Color.red(background)) + 0.7152f * channel(Color.green(background)) + 0.0722f * channel(Color.blue(background))
        val foregroundLuminance = 0.2126f * channel(Color.red(foreground)) + 0.7152f * channel(Color.green(foreground)) + 0.0722f * channel(Color.blue(foreground))
        return (max(luminance, foregroundLuminance) + 0.05f) / (min(luminance, foregroundLuminance) + 0.05f)
    }

    private fun hsl(red: Int, green: Int, blue: Int): FloatArray {
        val r = red / 255f; val g = green / 255f; val b = blue / 255f
        val max = max(r, max(g, b)); val min = min(r, min(g, b)); val delta = max - min
        val lightness = (max + min) / 2f
        if (delta == 0f) return floatArrayOf(0f, 0f, lightness)
        val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f))
        val hue = when (max) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(hue, saturation, lightness)
    }

    private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val rgb = when {
            hue < 60f -> floatArrayOf(c, x, 0f)
            hue < 120f -> floatArrayOf(x, c, 0f)
            hue < 180f -> floatArrayOf(0f, c, x)
            hue < 240f -> floatArrayOf(0f, x, c)
            hue < 300f -> floatArrayOf(x, 0f, c)
            else -> floatArrayOf(c, 0f, x)
        }
        return Color.rgb(((rgb[0] + m) * 255f).toInt().coerceIn(0, 255), ((rgb[1] + m) * 255f).toInt().coerceIn(0, 255), ((rgb[2] + m) * 255f).toInt().coerceIn(0, 255))
    }

    private fun Double.pow(exponent: Double) = java.lang.Math.pow(this, exponent)
}
