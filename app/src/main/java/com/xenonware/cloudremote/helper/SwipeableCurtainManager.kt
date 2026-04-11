package com.xenonware.cloudremote.helper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xenonware.cloudremote.service.CurtainTileService
import com.xenonware.cloudremote.ui.res.PixelWatchFace
import com.xenonware.cloudremote.ui.theme.XenonTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor

@SuppressLint("StaticFieldLeak")
object SwipeableCurtainManager {
    private const val TAG = "SwipeableCurtainManager"

    private var curtainView: View? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    var isCurtainVisible = false
        private set

    private var triggerHide: (() -> Unit)? = null

    @Suppress("DEPRECATION")
    fun showCurtain(context: Context) {
        if (isCurtainVisible) return

        if (!Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Overlay permission missing, cannot show curtain")
            return
        }

        val appContext = context.applicationContext
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )

            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

            val layout = object : FrameLayout(appContext) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                        return true // Consume BACK key
                    }
                    return super.dispatchKeyEvent(event)
                }
            }
            layout.setBackgroundColor(Color.TRANSPARENT)
            layout.isClickable = true
            layout.isFocusable = true
            layout.isFocusableInTouchMode = true
            
            layout.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN

            overlayLifecycleOwner = OverlayLifecycleOwner()
            overlayLifecycleOwner?.performRestore(null)
            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

            layout.setViewTreeLifecycleOwner(overlayLifecycleOwner)
            layout.setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            layout.setViewTreeViewModelStoreOwner(overlayLifecycleOwner)

            val composeView = ComposeView(appContext).apply {
                setContent {
                    XenonTheme(darkTheme = isSystemInDarkTheme()) {
                        val density = LocalDensity.current.density
                        val screenHeight = appContext.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
                        
                        var isContentVisible by remember { mutableStateOf(false) }
                        var contentOffsetY by remember { mutableFloatStateOf(-screenHeight) }
                        
                        LaunchedEffect(Unit) {
                            isContentVisible = true
                            contentOffsetY = 0f
                        }

                        triggerHide = {
                            isContentVisible = false
                            contentOffsetY = -screenHeight
                        }

                        val animatedContentOffsetY by animateFloatAsState(
                            targetValue = contentOffsetY,
                            label = "contentOffsetY",
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            finishedListener = {
                                // Only remove the view if it has reached the top and is supposed to be hidden
                                if (it <= -screenHeight + 1f && !isContentVisible) {
                                    removeCurtainView(context)
                                }
                            }
                        )
                        
                        val targetAlpha = if (isContentVisible) 1f else 0f
                        val animatedBgAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            label = "bgAlpha",
                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                        )

                        // Progress based on ANIMATED offset so alpha follows the motion during exit
                        val progress = (abs(animatedContentOffsetY) / screenHeight).coerceIn(0f, 1f)
                        
                        // Non-linear alpha: transparency grows faster (exponentially) as we swipe further
                        val dragAlphaFactor = (1f - progress.pow(3f)).coerceIn(0f, 1f)
                        
                        val finalBgAlpha = animatedBgAlpha * dragAlphaFactor

                        var isActive by remember { mutableStateOf(true) }

                        val animatedTextAlpha by animateFloatAsState(
                            targetValue = if (isActive) 0.5f else 0f,
                            label = "textAlpha",
                            animationSpec = tween(durationMillis = 500)
                        )

                        LaunchedEffect(isActive) {
                            if (isActive) {
                                delay(10000)
                                isActive = false
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(ComposeColor.Black.copy(alpha = finalBgAlpha))
                                .pointerInput(Unit) {
                                    coroutineScope {
                                        launch {
                                            detectTapGestures(onPress = {
                                                isActive = true
                                                tryAwaitRelease()
                                            })
                                        }
                                        launch {
                                            detectVerticalDragGestures(
                                                onDragStart = { isActive = true },
                                                onDragEnd = {
                                                    if (contentOffsetY < -200 * density) {
                                                        hideCurtain(context)
                                                    } else {
                                                        contentOffsetY = 0f
                                                    }
                                                }
                                            ) { _, dragAmount ->
                                                val newContentOffsetY = contentOffsetY + dragAmount
                                                if (newContentOffsetY <= 0) {
                                                    contentOffsetY = newContentOffsetY
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .offset { IntOffset(0, animatedContentOffsetY.roundToInt()) }
                                    .graphicsLayer {
                                        // Apply non-linear alpha to the whole content
                                        alpha = dragAlphaFactor
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                PixelWatchFace(isActive = isActive)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "Swipe up to unlock",
                                    color = ComposeColor.White.copy(alpha = animatedTextAlpha)
                                )
                                Spacer(modifier = Modifier.weight(0.2f))
                            }
                        }
                    }
                }
            }

            layout.addView(
                composeView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
            overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            windowManager.addView(layout, params)

            curtainView = layout
            isCurtainVisible = true
            
            CurtainTileService.isCurtainActive = true
            CurtainTileService.requestTileUpdate(context)
            Log.d(TAG, "Curtain shown successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing curtain", e)
            e.printStackTrace()
        }
    }

    fun hideCurtain(context: Context) {
        if (curtainView == null && !isCurtainVisible) {
            Log.d(TAG, "Curtain already hidden")
            return
        }

        triggerHide?.invoke()
    }
    
    private fun removeCurtainView(context: Context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        curtainView?.let { view ->
            try {
                // Safely remove view avoiding double removal crashes
                if (view.isAttachedToWindow) {
                    overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    overlayLifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    windowManager.removeViewImmediate(view)
                }
                Log.d(TAG, "Curtain removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing curtain view", e)
                e.printStackTrace()
            }
        }
        curtainView = null
        overlayLifecycleOwner = null
        triggerHide = null
        isCurtainVisible = false
        
        CurtainTileService.isCurtainActive = false
        CurtainTileService.requestTileUpdate(context)
    }

    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner,
        SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.Companion.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        fun performRestore(savedState: Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }
    }
}
