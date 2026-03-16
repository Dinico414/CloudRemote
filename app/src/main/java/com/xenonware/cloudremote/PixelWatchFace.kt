package com.xenonware.cloudremote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun PixelWatchFace() {
    val textMeasurer = rememberTextMeasurer()

    var touchCount by remember { mutableIntStateOf(0) }
    var isActive by remember { mutableStateOf(true) }

    // Start 10-second active timer, resets on every touch
    LaunchedEffect(touchCount) {
        isActive = true
        delay(10000)
        isActive = false
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.4f,
        animationSpec = tween(500),
        label = "alpha"
    )
    val pillRightWeight by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(500),
        label = "pillRight"
    )

    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isFullyInactive = !isActive && pillRightWeight == 0f

    LaunchedEffect(isFullyInactive) {
        while (true) {
            time = System.currentTimeMillis()
            if (!isFullyInactive) {
                delay(16) // ~60fps smooth rendering
            } else {
                // In ambient mode, sleep until the next minute tick
                val calendar = Calendar.getInstance().apply { timeInMillis = time }
                val seconds = calendar.get(Calendar.SECOND)
                val millis = calendar.get(Calendar.MILLISECOND)
                val delayToNextMinute = 60000L - (seconds * 1000L + millis)
                delay(max(delayToNextMinute, 100L))
            }
        }
    }

    val calendar = Calendar.getInstance().apply { timeInMillis = time }
    val hour = calendar.get(Calendar.HOUR_OF_DAY) % 12
    val displayHour = if (hour == 0) 12 else hour
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val millis = calendar.get(Calendar.MILLISECOND)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { touchCount++ }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = min(w, h) / 2f

        val rInnerNum = r * 0.5f
        val rInnerTickIn = r * 0.6f
        val rInnerTickOut = r * 0.65f

        val rOuterNum = r * 0.8f
        val rOuterTickIn = r * 0.9f
        val rOuterTickOut = r * 0.95f

        val primaryColor = Color.White.copy(alpha = animatedAlpha)
        val secondaryColor = Color.Gray.copy(alpha = animatedAlpha)
        val pillOutlineColor = Color.LightGray.copy(alpha = animatedAlpha)

        val hourStyle = TextStyle(
            color = primaryColor,
            fontSize = (r * 0.22f).sp,
            fontWeight = FontWeight.Medium
        )
        val digMinStyle = TextStyle(
            color = primaryColor,
            fontSize = (r * 0.08f).sp,
            fontWeight = FontWeight.Medium
        )
        val digSecStyle = TextStyle(
            color = primaryColor,
            fontSize = (r * 0.05f).sp,
            fontWeight = FontWeight.Medium
        )
        val dialNumStyle = TextStyle(
            color = secondaryColor,
            fontSize = (r * 0.035f).sp,
            fontWeight = FontWeight.Normal
        )

        // Draw Hour
        val hourText = displayHour.toString()
        val hourLayout = textMeasurer.measure(hourText, hourStyle)
        drawText(
            textLayoutResult = hourLayout,
            topLeft = Offset(cx - hourLayout.size.width / 2f, cy - hourLayout.size.height / 2f)
        )

        val currentMinuteFloat = minute.toFloat()
        val currentSecondFloat = second + millis / 1000f

        fun drawDial(
            currentValue: Float,
            rNum: Float,
            rTickIn: Float,
            rTickOut: Float,
            alpha: Float = 1f,
            hideCurrentNumber: Boolean = false
        ) {
            if (alpha <= 0f) return
            
            for (i in 0 until 60) {
                val angleDeg = (currentValue - i) * 6f
                val rad = Math.toRadians(angleDeg.toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()

                // Ticks
                val isThick = i % 5 == 0
                val strokeWidth = if (isThick) r * 0.012f else r * 0.005f
                drawLine(
                    color = secondaryColor.copy(alpha = secondaryColor.alpha * alpha),
                    start = Offset(cx + rTickIn * cosA, cy + rTickIn * sinA),
                    end = Offset(cx + rTickOut * cosA, cy + rTickOut * sinA),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                // Numbers
                if (isThick) {
                    val normalizedAngle = (angleDeg % 360f + 360f) % 360f
                    val isNearRight = normalizedAngle < 25f || normalizedAngle > 335f
                    
                    // Hide any number that falls inside the pill area (near 3 o'clock)
                    if (hideCurrentNumber && isNearRight) {
                        continue
                    }
                    val displayNum = if (i == 0) 60 else i
                    val numText = String.format(Locale.getDefault(), "%02d", displayNum)
                    val layout = textMeasurer.measure(numText, dialNumStyle)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            cx + rNum * cosA - layout.size.width / 2f,
                            cy + rNum * sinA - layout.size.height / 2f
                        ),
                        alpha = alpha
                    )
                }
            }
        }

        // Draw Minute Ring (ticks per minute instantly)
        drawDial(currentMinuteFloat, rInnerNum, rInnerTickIn, rInnerTickOut, hideCurrentNumber = true)
        
        // Draw Second Ring (smoothly glides per second)
        if (pillRightWeight > 0f) {
            drawDial(currentSecondFloat, rOuterNum, rOuterTickIn, rOuterTickOut, alpha = pillRightWeight, hideCurrentNumber = false)
        }

        // Pill
        val pillHeight = r * 0.36f // 0.61f - 0.25f = 0.36f to perfectly match the ring edge
        val pillTop = cy - pillHeight / 2f
        val pillBottom = cy + pillHeight / 2f
        
        val pillLeft = cx + r * 0.25f
        val pillRadius = pillHeight / 2f

        val inactivePillRight = cx + rInnerTickOut // cx + r * 0.61f
        val activePillRight = cx + rOuterTickOut   // cx + r * 0.88f
        val currentPillRight = inactivePillRight + (activePillRight - inactivePillRight) * pillRightWeight

        val pillRect = Rect(
            left = pillLeft,
            top = pillTop,
            right = currentPillRight,
            bottom = pillBottom
        )
        val pillPath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = pillRect,
                    cornerRadius = CornerRadius(pillRadius, pillRadius)
                )
            )
        }

        // Pill Outline (No solid background so ticks show through!)
        drawPath(pillPath, color = pillOutlineColor, style = Stroke(width = r * 0.008f))

        // Digital Minute Inside Pill
        val minText = String.format(Locale.getDefault(), "%02d", minute)
        val minLayout = textMeasurer.measure(minText, digMinStyle)
        
        // Center the minute text exactly in the circular part of the inactive pill
        val circleCenterX = pillLeft + pillRadius
        drawText(
            textLayoutResult = minLayout,
            topLeft = Offset(
                circleCenterX - minLayout.size.width / 2f,
                cy - minLayout.size.height / 2f
            )
        )
    }
}

@Preview(widthDp = 300, heightDp = 300)
@Composable
fun PixelWatchFacePreview() {
    PixelWatchFace()
}