package com.xenonware.cloudremote.widget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class WatchWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    companion object {
        private var cachedWallpaper: Bitmap? = null
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        if (cachedWallpaper == null) {
            cachedWallpaper = loadBlurredWallpaper(context)
        }

        provideContent {
            val ctx = LocalContext.current
            val dpSize = LocalSize.current

            LaunchedEffect(Unit) {
                while (true) {
                    try { update(ctx, id) } catch (_: Exception) {}
                    delay(100L)
                }
            }

            GlanceTheme {
                Content(ctx, dpSize)
            }
        }
    }

    @Composable
    private fun Content(context: Context, dpSize: androidx.compose.ui.unit.DpSize) {
        val density = context.resources.displayMetrics.density
        val rawW = (dpSize.width.value * density).toInt().coerceAtLeast(1)
        val rawH = (dpSize.height.value * density).toInt().coerceAtLeast(1)
        val maxDim = 400
        val scale = if (max(rawW, rawH) > maxDim) maxDim.toFloat() / max(rawW, rawH) else 1f
        val w = (rawW * scale).toInt().coerceAtLeast(1)
        val h = (rawH * scale).toInt().coerceAtLeast(1)

        val bitmap = renderFrame(context, w, h)

        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Watch Face",
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
    }

    private fun renderFrame(context: Context, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        cachedWallpaper?.let { wp ->
            val fitScale = max(w.toFloat() / wp.width, h.toFloat() / wp.height)
            val srcW = (w / fitScale).toInt().coerceAtMost(wp.width)
            val srcH = (h / fitScale).toInt().coerceAtMost(wp.height)
            val srcX = (wp.width - srcW) / 2
            val srcY = (wp.height - srcH) / 2
            canvas.drawBitmap(
                wp,
                Rect(srcX, srcY, srcX + srcW, srcY + srcH),
                Rect(0, 0, w, h),
                null
            )
        }

        paint.color = Color.argb(50, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        val clockSize = min(w, h)
        val offsetX = (w - clockSize) / 2f
        val offsetY = (h - clockSize) / 2f
        canvas.save()
        canvas.translate(offsetX, offsetY)
        drawClockFace(canvas, paint, clockSize, context)
        canvas.restore()

        return bmp
    }

    private fun drawClockFace(canvas: Canvas, paint: Paint, size: Int, context: Context) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val millis = (now % 1000).toInt()
        val second = cal.get(Calendar.SECOND)
        val minute = cal.get(Calendar.MINUTE)
        val rawHour = cal.get(Calendar.HOUR_OF_DAY)

        val secondF = second + millis / 1000f
        val minuteF = minute + second / 60f

        val is24 = DateFormat.is24HourFormat(context)
        val displayHour = if (is24) rawHour else {
            val h = rawHour % 12; if (h == 0) 12 else h
        }

        val locale = Locale.getDefault()
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f * 0.95f

        val rInnerNum     = r * 0.54f
        val rInnerTickIn  = r * 0.62f
        val rInnerTickOut = r * 0.65f
        val rOuterNum     = r * 0.82f
        val rOuterTickIn  = r * 0.9f
        val rOuterTickOut = r * 0.95f

        paint.reset()
        paint.isAntiAlias = true

        // Hour
        paint.color = Color.WHITE
        paint.textSize = r * 0.45f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        canvas.drawText(
            String.format(locale, "%02d", displayHour),
            cx - r * 0.04f, cy + paint.textSize * 0.35f, paint
        )

        // Minute dial (inner)
        paint.isFakeBoldText = false
        for (i in 0 until 60) {
            val deg = (i - minuteF) * 6f
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val thick = i % 5 == 0
            paint.strokeWidth = if (thick) r * 0.012f else r * 0.005f
            paint.color = Color.GRAY
            paint.style = Paint.Style.STROKE
            canvas.drawLine(
                cx + rInnerTickIn * c, cy + rInnerTickIn * s,
                cx + rInnerTickOut * c, cy + rInnerTickOut * s, paint
            )
            if (thick) {
                paint.style = Paint.Style.FILL
                paint.textSize = r * 0.08f
                paint.textAlign = Paint.Align.CENTER
                val n = if (i == 0) 60 else i
                canvas.drawText(
                    String.format(locale, "%02d", n),
                    cx + rInnerNum * c, cy + rInnerNum * s + paint.textSize * 0.35f, paint
                )
            }
        }

        // Seconds dial (outer) – smooth
        for (i in 0 until 60) {
            val deg = (i - secondF) * 6f
            val rad = Math.toRadians(deg.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val thick = i % 5 == 0
            paint.strokeWidth = if (thick) r * 0.012f else r * 0.005f
            paint.color = Color.GRAY
            paint.style = Paint.Style.STROKE
            canvas.drawLine(
                cx + rOuterTickIn * c, cy + rOuterTickIn * s,
                cx + rOuterTickOut * c, cy + rOuterTickOut * s, paint
            )
            if (thick) {
                paint.style = Paint.Style.FILL
                paint.textSize = r * 0.06f
                paint.textAlign = Paint.Align.CENTER
                val n = if (i == 0) 60 else i
                canvas.drawText(
                    String.format(locale, "%02d", n),
                    cx + rOuterNum * c, cy + rOuterNum * s + paint.textSize * 0.35f, paint
                )
            }
        }

        // Pill at 3 o'clock
        val pillH = r * 0.36f
        val pillR = pillH / 2f
        val pillRight = cx + rInnerTickOut
        val pillLeft = pillRight - pillH
        val pillTop = cy - pillH / 2f
        val pillBottom = cy + pillH / 2f

        paint.color = Color.WHITE
        paint.alpha = 51
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, pillR, pillR, paint)

        paint.color = Color.LTGRAY
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.008f
        canvas.drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, pillR, pillR, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = r * 0.18f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            String.format(locale, "%02d", minute),
            pillLeft + pillR, cy + paint.textSize * 0.35f, paint
        )
    }

    private fun loadBlurredWallpaper(context: Context): Bitmap? {
        val wm = WallpaperManager.getInstance(context)

        val drawable = try {
            wm.drawable
        } catch (_: SecurityException) { null }
        catch (_: Exception) { null }

        val finalDrawable = drawable ?: try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                wm.getBuiltInDrawable(WallpaperManager.FLAG_SYSTEM)
            else null
        } catch (_: Exception) { null }

        if (finalDrawable == null) return null

        val srcBitmap: Bitmap = if (finalDrawable is BitmapDrawable && finalDrawable.bitmap != null) {
            finalDrawable.bitmap
        } else {
            val dw = finalDrawable.intrinsicWidth.let { if (it > 0) it else 200 }
            val dh = finalDrawable.intrinsicHeight.let { if (it > 0) it else 200 }
            val bmp = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            finalDrawable.setBounds(0, 0, dw, dh)
            finalDrawable.draw(c)
            bmp
        }

        // Downscale → upscale = heavy blur. Two passes for extra smoothness.
        val pass1 = Bitmap.createScaledBitmap(srcBitmap, 20, 20, true)
        val pass2 = Bitmap.createScaledBitmap(pass1, 8, 8, true)
        return Bitmap.createScaledBitmap(pass2, 200, 200, true)
    }
}

class WatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchWidget()
}