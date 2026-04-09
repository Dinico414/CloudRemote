package com.xenonware.cloudremote.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val dpSize = LocalSize.current

            LaunchedEffect(Unit) {
                while (true) {
                    try {
                        update(ctx, id)
                    } catch (_: Exception) {}

                    // Attempting a 60FPS (~16ms) loop.
                    // Note: Android OS IPC will likely throttle this to save battery.
                    delay(16L)
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

        // 1. Clear canvas completely to allow launcher to show through
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 2. Draw a translucent "frosted glass" style tint over the transparent background
        paint.color = Color.argb(80, 20, 20, 20) // Semi-transparent dark tint
        paint.style = Paint.Style.FILL
        val cornerRadius = min(w, h) * 0.15f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, paint)

        // 3. Draw a subtle border for the glass effect
        paint.color = Color.argb(40, 255, 255, 255)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius, paint)

        // 4. Draw Clock Face
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

        // High-precision sweeps for smooth 16ms ticks
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

        // Hour Text
        paint.color = Color.WHITE
        paint.textSize = r * 0.45f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true

        val fontMetrics = paint.fontMetrics
        val textOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

        canvas.drawText(
            String.format(locale, "%02d", displayHour),
            cx - r * 0.04f, cy - textOffset, paint
        )

        // Minute dial (inner)
        paint.isFakeBoldText = false
        for (i in 0 until 60) {
            val deg = (i - minuteF) * 6f
            val rad = Math.toRadians(deg.toDouble() - 90.0) // -90 to start at 12 o'clock
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val thick = i % 5 == 0
            paint.strokeWidth = if (thick) r * 0.012f else r * 0.005f
            paint.color = Color.LTGRAY
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
                val textMetrics = paint.fontMetrics
                val tOffset = (textMetrics.descent + textMetrics.ascent) / 2f
                canvas.drawText(
                    String.format(locale, "%02d", n),
                    cx + rInnerNum * c, cy + rInnerNum * s - tOffset, paint
                )
            }
        }

        // Seconds dial (outer)
        for (i in 0 until 60) {
            val deg = (i - secondF) * 6f
            val rad = Math.toRadians(deg.toDouble() - 90.0) // -90 to start at 12 o'clock
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            val thick = i % 5 == 0
            paint.strokeWidth = if (thick) r * 0.012f else r * 0.005f
            paint.color = Color.WHITE
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
                val textMetrics = paint.fontMetrics
                val tOffset = (textMetrics.descent + textMetrics.ascent) / 2f
                canvas.drawText(
                    String.format(locale, "%02d", n),
                    cx + rOuterNum * c, cy + rOuterNum * s - tOffset, paint
                )
            }
        }

        // Pill at 3 o'clock for Minute
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

        val pillTextMetrics = paint.fontMetrics
        val pillTextOffset = (pillTextMetrics.descent + pillTextMetrics.ascent) / 2f
        canvas.drawText(
            String.format(locale, "%02d", minute),
            pillLeft + pillR, cy - pillTextOffset, paint
        )
    }
}

class WatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WatchWidget()
}