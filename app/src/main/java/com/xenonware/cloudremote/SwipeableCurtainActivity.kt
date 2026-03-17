package com.xenonware.cloudremote

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xenonware.cloudremote.service.CurtainTileService
import com.xenonware.cloudremote.ui.res.PixelWatchFace
import com.xenonware.cloudremote.ui.theme.XenonTheme
import kotlin.math.roundToInt

class SwipeableCurtainActivity : ComponentActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        CurtainTileService.isCurtainActive = true

        // Disable back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing to block the back gesture
            }
        })

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val intentFilter = IntentFilter(ACTION_CLOSE_CURTAIN)
        ContextCompat.registerReceiver(this, closeReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            XenonTheme(darkTheme = isSystemInDarkTheme()) {
                var offsetY by remember { mutableFloatStateOf(0f) }
                val animatedOffsetY by animateFloatAsState(
                    targetValue = offsetY,
                    label = "offsetY",
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                val density = LocalDensity.current.density

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (offsetY < -200 * density) {
                                        finishAndRemoveTask()
                                    } else {
                                        offsetY = 0f
                                    }
                                }
                            ) { _, dragAmount ->
                                val newOffsetY = offsetY + dragAmount
                                if (newOffsetY < 0) {
                                    offsetY = newOffsetY
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PixelWatchFace()
                }
            }
        }
        hideSystemUI()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }


    override fun onDestroy() {
        CurtainTileService.isCurtainActive = false
        super.onDestroy()
        unregisterReceiver(closeReceiver)
        TileService.requestListeningState(this, ComponentName(this, CurtainTileService::class.java))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH -> true // Consume event
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        const val ACTION_CLOSE_CURTAIN = "com.xenonware.cloudremote.ACTION_CLOSE_CURTAIN"
    }
}